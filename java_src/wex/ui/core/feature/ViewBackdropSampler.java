package wex.ui.core.feature;

import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;

/** Native-view recording adapted from WeKit ViewBackdrop (GPL-3.0-only). */
final class ViewBackdropSampler implements ViewTreeObserver.OnPreDrawListener,
        View.OnAttachStateChangeListener, View.OnLayoutChangeListener {
    enum Reason { READY, STOPPED, REENTRANT, SOFTWARE, SOURCE_UNAVAILABLE, WINDOW_MISMATCH,
        NO_INTERSECTION, NO_BACKGROUND, SURFACE, TREE_BUDGET, UNSAFE_BOUNDS, CAPTURE_FAILED, RETRY_WAIT }
    private static final int TREE_BUDGET = 768, LOG_BUDGET = 12;
    private WeakReference<View> sourceRef, contentRef;
    private final WeakReference<View> consumerRef;
    private final WeakReference<View> excludedRef;
    private RenderNode sourceNode = new RenderNode("MindLink-home-backdrop");
    private RenderNode pendingNode = new RenderNode("MindLink-home-backdrop-pending");
    private final BackdropCaptureState state = new BackdropCaptureState();
    private final int[] sourcePosition = new int[2], consumerPosition = new int[2], childPosition = new int[2];
    private final int[] rootPosition = new int[2];
    private final Rect visible = new Rect(), requested = new Rect(), sourceVisible = new Rect(), lastRegion = new Rect();
    private final ArrayDeque<View> backgrounds = new ArrayDeque<>();
    private final BackgroundState[] backgroundStates = new BackgroundState[16];
    private ViewTreeObserver observer;
    private Object windowToken;
    private long generation, contentRevision, contentGeneration;
    private boolean running, recording, warned, retryPending, availabilityBlocked, hasRegion, hostDirty;
    private int offsetX, offsetY, visited, logs, backgroundCount;
    private int contentWidth, contentHeight, contentScrollX, contentScrollY;
    private boolean observedBackgrounds, observedContent;
    private Reason reason = Reason.STOPPED;
    private BackdropCaptureState.Key recheckKey;
    private int rechecks;
    private final Runnable retry = this::retryCapture;

    private static final class BackgroundState {
        final WeakReference<View> view;
        final WeakReference<Drawable> drawable;
        final int alpha, level, color;
        final int[] state;
        BackgroundState(View view, Drawable drawable) {
            this.view = new WeakReference<>(view); this.drawable = new WeakReference<>(drawable);
            alpha = drawable == null ? 0 : drawable.getAlpha();
            level = drawable == null ? 0 : drawable.getLevel();
            color = drawable instanceof ColorDrawable ? ((ColorDrawable) drawable).getColor() : 0;
            state = drawable == null ? null : drawable.getState().clone();
        }
        boolean matches(View current, Drawable background) {
            return view.get() == current && drawable.get() == background
                    && (background == null || alpha == background.getAlpha() && level == background.getLevel()
                    && color == (background instanceof ColorDrawable ? ((ColorDrawable) background).getColor() : 0)
                    && java.util.Arrays.equals(state, background.getState()));
        }
    }

    ViewBackdropSampler(View source, View consumer) {
        this(source, consumer, consumer);
    }

    ViewBackdropSampler(View source, View consumer, View excludedBranch) {
        sourceRef = new WeakReference<>(source);
        contentRef = new WeakReference<>(source);
        consumerRef = new WeakReference<>(consumer);
        excludedRef = new WeakReference<>(excludedBranch);
    }

    /** Parent supplies the real background below a short pager; pager alone supplies native content. */
    void source(View source, View content) {
        if (sourceRef.get() == source && contentRef.get() == content) return;
        boolean resume = running;
        stop();
        sourceRef = new WeakReference<>(source);
        contentRef = new WeakReference<>(content);
        hasRegion = false;
        if (resume) start();
    }

    boolean isCapturing() { return recording; }
    Reason reason() { return reason; }
    int visited() { return visited; }

    void start() {
        if (running) return;
        View source = sourceRef.get();
        if (source == null) return;
        running = true;
        source.addOnAttachStateChangeListener(this);
        source.addOnLayoutChangeListener(this);
        observe(source);
        changed();
    }

    void stop() {
        running = false;
        View source = sourceRef.get();
        if (source != null) {
            source.removeOnAttachStateChangeListener(this);
            source.removeOnLayoutChangeListener(this);
        }
        unobserve();
        resetCapture();
        backgrounds.clear();
        java.util.Arrays.fill(backgroundStates, null);
        backgroundCount = 0; observedBackgrounds = observedContent = hostDirty = false;
        windowToken = null;
        availabilityBlocked = false;
        reason = Reason.STOPPED;
    }

    private void retryCapture() {
        retryPending = false;
        View source = sourceRef.get(), content = contentRef.get(), consumer = consumerRef.get();
        if (!running || consumer == null || !consumer.isAttachedToWindow() || !consumer.isShown()) return;
        if (availabilityBlocked) {
            if (source == null || content == null || !source.isAttachedToWindow()
                    || source.getWindowToken() == null || source.getWindowToken() != consumer.getWindowToken()) return;
            Reason safety = !source.isShown() || source.getWidth() <= 0 || source.getHeight() <= 0
                    ? Reason.SOURCE_UNAVAILABLE : availability(source, content, consumer, lastRegion);
            if (safety != Reason.READY) {
                scheduleRecheck(source, consumer, SystemClock.uptimeMillis());
                return;
            }
            // Never capture inside a timer: re-run every normal-frame gate before recording.
        }
        consumer.invalidate();
    }

    private void cancelRetry() {
        View consumer = consumerRef.get();
        if (consumer != null) consumer.removeCallbacks(retry);
        retryPending = false;
    }

    private void scheduleRetry(BackdropCaptureState.Key key, long now) {
        long delay = state.retryDelay(key, now);
        View consumer = consumerRef.get();
        if (delay < 0L || retryPending || !running || consumer == null || !consumer.isShown()) return;
        retryPending = consumer.postDelayed(retry, Math.max(1L, delay));
    }

    private void scheduleRecheck(View source, View consumer, long now) {
        if (!hasRegion || !running || source == null || consumer == null || !source.isAttachedToWindow()
                || !consumer.isAttachedToWindow() || !consumer.isShown() || source.getWindowToken() == null
                || source.getWindowToken() != consumer.getWindowToken()) return;
        BackdropCaptureState.Key key = captureKey(source, source.getWindowToken());
        // Only a real source/environment revision may begin a new two-check window.
        // changed(), self-invalidation and structural jitter never mint another budget.
        if (recheckKey == null || !recheckKey.sameEnvironment(key)
                || recheckKey.contentGeneration != key.contentGeneration) {
            recheckKey = key; rechecks = 0;
        }
        if (retryPending || rechecks >= 2 || state.retryExhausted(key)) return;
        long backoff = state.retryDelay(key, now);
        long delay = Math.max(rechecks == 0 ? 80L : 240L, Math.max(0L, backoff));
        if (consumer.postDelayed(retry, delay)) { retryPending = true; rechecks++; }
    }

    private BackdropCaptureState.Key captureKey(View source, Object token) {
        return new BackdropCaptureState.Key(source, token, generation, contentGeneration,
                source.getWidth(), source.getHeight(), source.getScrollX(), source.getScrollY(),
                source.getResources().getDisplayMetrics().density,
                source.getResources().getConfiguration().fontScale, source.getLayoutDirection());
    }

    private void resetCapture() {
        cancelRetry();
        recheckKey = null; rechecks = 0;
        state.invalidate();
        sourceNode.discardDisplayList();
        pendingNode.discardDisplayList();
    }

    /** Main deduplicates actual list position/top/empty changes, not draw invalidations. */
    void sourceContentChanged() {
        if (!running || recording) return;
        contentGeneration++;
        changed();
    }

    // Layout/consumer invalidation is not evidence that the failed host content changed.
    void changed() {
        generation++;
        View consumer = consumerRef.get();
        if (running && consumer != null && consumer.isShown()) consumer.invalidate();
    }

    private void observeHostChanges(View content) {
        boolean dirty = content.isDirty() || content.isLayoutRequested();
        int width = content.getWidth(), height = content.getHeight();
        int scrollX = content.getScrollX(), scrollY = content.getScrollY();
        if (observedContent && (dirty && !hostDirty || width != contentWidth || height != contentHeight
                || scrollX != contentScrollX || scrollY != contentScrollY)) contentGeneration++;
        contentWidth = width; contentHeight = height; contentScrollX = scrollX; contentScrollY = scrollY;
        hostDirty = dirty; observedContent = true;
    }

    private boolean backgroundsChanged(View source) {
        boolean changed = false; int count = 0;
        for (View view = source; view != null && count < backgroundStates.length;
             view = view.getParent() instanceof View ? (View) view.getParent() : null) {
            Drawable background = view.getBackground();
            BackgroundState previous = backgroundStates[count];
            if (previous == null || !previous.matches(view, background)) {
                changed = true; backgroundStates[count] = new BackgroundState(view, background);
            }
            count++;
        }
        changed |= count != backgroundCount;
        for (int i = count; i < backgroundCount; i++) backgroundStates[i] = null;
        boolean result = observedBackgrounds && changed;
        backgroundCount = count; observedBackgrounds = true;
        // Observable values only. Never replace a host Drawable callback or use
        // parent.isDirty(): our own consumer/retry invalidates that same parent.
        return result;
    }

    private static boolean changingHost(View content) {
        return content.isDirty() || content.isLayoutRequested();
    }

    private void observeHostContent(View source, View content) {
        observeHostChanges(content);
        if (source != content && backgroundsChanged(source)) { contentGeneration++; generation++; }
    }

    private void observe(View source) {
        unobserve();
        observer = source.getViewTreeObserver();
        if (observer.isAlive()) observer.addOnPreDrawListener(this);
    }

    private void unobserve() {
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
        observer = null;
    }

    @Override public boolean onPreDraw() {
        View source = sourceRef.get(), content = contentRef.get(), consumer = consumerRef.get();
        if (!running || source == null || content == null || consumer == null || !consumer.isShown()) return true;
        source.getLocationInWindow(sourcePosition);
        consumer.getLocationInWindow(consumerPosition);
        int x = consumerPosition[0] - sourcePosition[0], y = consumerPosition[1] - sourcePosition[1];
        long previousContent = contentGeneration;
        observeHostContent(source, content);
        // A parent containing this overlay is dirtied by the overlay itself. Never use that as a capture clock.
        if (x != offsetX || y != offsetY || contentGeneration != previousContent) {
            offsetX = x; offsetY = y;
            changed();
        } else if (availabilityBlocked && hasRegion && availability(source, content, consumer, lastRegion) == Reason.READY) {
            // A real host layout/content draw can remove a surface without changing the pager's own dirty bit.
            changed();
        }
        return true;
    }

    boolean prepare(Canvas canvas, Rect sampleInConsumer) {
        View source = sourceRef.get(), content = contentRef.get(), consumer = consumerRef.get();
        visited = 0;
        if (!running) return status(Reason.STOPPED, source, consumer, false);
        if (recording) return false;
        if (!canvas.isHardwareAccelerated()) {
            cancelRetry();
            return status(Reason.SOFTWARE, source, consumer, false);
        }
        lastRegion.set(sampleInConsumer.left, sampleInConsumer.top, sampleInConsumer.right, sampleInConsumer.bottom);
        hasRegion = true;
        if (source == null || content == null || consumer == null || !source.isAttachedToWindow()
                || !consumer.isAttachedToWindow() || !source.isShown() || !consumer.isShown()
                || source.getWidth() <= 0 || source.getHeight() <= 0)
            return unavailable(Reason.SOURCE_UNAVAILABLE, source, consumer);
        if (source.getWindowToken() == null || source.getWindowToken() != consumer.getWindowToken()) {
            cancelRetry();
            return status(Reason.WINDOW_MISMATCH, source, consumer, false);
        }
        Object token = source.getWindowToken();
        if (token != windowToken) {
            resetCapture(); windowToken = token; generation++;
        }
        source.getLocationInWindow(sourcePosition);
        consumer.getLocationInWindow(consumerPosition);
        offsetX = consumerPosition[0] - sourcePosition[0];
        offsetY = consumerPosition[1] - sourcePosition[1];
        Reason safety = availability(source, content, consumer, sampleInConsumer);
        if (safety != Reason.READY) return unavailable(safety, source, consumer);
        if (availabilityBlocked) { availabilityBlocked = false; generation++; }
        observeHostContent(source, content);
        BackdropCaptureState.Key key = captureKey(source, token);
        // A cached success is reusable only while the actual published node still exists.
        if (!sourceNode.hasDisplayList()) state.captureUnavailable();
        long now = SystemClock.uptimeMillis();
        BackdropCaptureState.Decision decision = state.decide(key, now);
        Reason result = decision == BackdropCaptureState.Decision.SKIP ? Reason.RETRY_WAIT : Reason.READY;
        if (decision == BackdropCaptureState.Decision.CAPTURE) {
            cancelRetry();
            recording = true;
            boolean began = false;
            Throwable failure = null;
            try {
                pendingNode.setPosition(0, 0, key.width, key.height);
                RecordingCanvas capture = pendingNode.beginRecording(key.width, key.height);
                began = true;
                // View.draw does not apply the parent's scroll transform to the pager.
                if (source == content) {
                    capture.translate(-key.scrollX, -key.scrollY);
                    source.draw(capture);
                } else recordParent(capture, source, content);
            } catch (Throwable error) { failure = error; }
            finally {
                // Any dirty/layout signal produced by our own throwing draw is
                // already part of this attempt, never a new host-content revision.
                hostDirty = changingHost(content);
                try { if (began) pendingNode.endRecording(); }
                catch (Throwable error) { if (failure == null) failure = error; }
                finally { recording = false; }
            }
            if (failure == null && pendingNode.hasDisplayList()) {
                RenderNode previous = sourceNode;
                sourceNode = pendingNode; pendingNode = previous;
                state.succeeded(key); contentRevision++;
            } else {
                pendingNode.discardDisplayList();
                state.failed(key, now);
                result = Reason.CAPTURE_FAILED;
                if (!warned) {
                    warned = true;
                    NavigationBarFeature.log("真实背景采样失败 "
                            + (failure == null ? "EmptyDisplayList" : failure.getClass().getSimpleName()));
                }
            }
        }
        scheduleRetry(key, SystemClock.uptimeMillis());
        return status(result, source, consumer, state.canDraw(key) && sourceNode.hasDisplayList());
    }

    private boolean unavailable(Reason value, View source, View consumer) {
        // A structural gap/surface must never resurrect a pre-gap frame when it clears.
        if (!availabilityBlocked) {
            cancelRetry(); sourceNode.discardDisplayList(); pendingNode.discardDisplayList();
            state.captureUnavailable();
            availabilityBlocked = true;
        }
        scheduleRecheck(source, consumer, SystemClock.uptimeMillis());
        return status(value, source, consumer, false);
    }

    private boolean status(Reason value, View source, View consumer, boolean drawable) {
        if (value != reason && logs < LOG_BUDGET) {
            logs++;
            NavigationBarFeature.log("背景采样 reason=" + value + " visited=" + visited + "/" + TREE_BUDGET
                    + " tokenMatch=" + (source != null && consumer != null && source.getWindowToken() != null
                    && source.getWindowToken() == consumer.getWindowToken())
                    + " sourceShown=" + (source != null && source.isShown())
                    + " source=" + (source == null ? "null" : source.getWidth() + "x" + source.getHeight())
                    + " sample=" + requested.left + "," + requested.top + "," + requested.right + "," + requested.bottom);
        }
        reason = value;
        return drawable;
    }

    void draw(Canvas canvas) {
        int save = canvas.save();
        canvas.translate(-offsetX, -offsetY);
        canvas.drawRenderNode(sourceNode);
        canvas.restoreToCount(save);
    }

    long revision() { return contentRevision; }
    int offsetX() { return offsetX; }
    int offsetY() { return offsetY; }

    private Reason availability(View source, View content, View consumer, Rect region) {
        backgrounds.clear(); visited = 0;
        try {
            source.getLocationInWindow(sourcePosition);
            consumer.getLocationInWindow(consumerPosition);
            View root = source.getRootView();
            root.getLocationInWindow(rootPosition);
            requested.set(region.left, region.top, region.right, region.bottom);
            requested.offset(consumerPosition[0], consumerPosition[1]);
            if (requested.width() <= 0 || requested.height() <= 0 || !windowVisible(source, sourceVisible)
                    || !Rect.intersects(sourceVisible, requested)) return Reason.NO_INTERSECTION;
            // Recording has source-sized bounds. Only pixels in this intersection can affect the effect.
            requested.set(Math.max(requested.left, sourceVisible.left), Math.max(requested.top, sourceVisible.top),
                    Math.min(requested.right, sourceVisible.right), Math.min(requested.bottom, sourceVisible.bottom));
            if (source != content) {
                View excluded = excludedRef.get();
                if (content.getParent() != source || excluded == null || excluded.getParent() != source
                        || !source.getMatrix().isIdentity()
                        || !content.getMatrix().isIdentity() || source.getAlpha() != 1f || content.getAlpha() != 1f)
                    return Reason.UNSAFE_BOUNDS;
                boolean opaque = false;
                for (View view = source; view != null && backgrounds.size() < 16;
                     view = view.getParent() instanceof View ? (View) view.getParent() : null) {
                    backgrounds.addLast(view);
                    Drawable background = view.getBackground();
                    if (background != null && background.getOpacity() == PixelFormat.OPAQUE && view.getAlpha() == 1f
                            && view.getMatrix().isIdentity() && windowVisible(view, visible)
                            && contains(visible, requested)) { opaque = true; break; }
                }
                // An oversized parent is not evidence of real pixels below a short content view.
                if (!opaque && (!windowVisible(content, visible) || !contains(visible, requested))) return Reason.NO_BACKGROUND;
            }
            return hasUncapturableSurface(source, consumer);
        } catch (RuntimeException error) { return Reason.UNSAFE_BOUNDS; }
    }

    private boolean windowVisible(View view, Rect out) {
        if (!view.isShown() || view.getAlpha() <= 0f || !view.getGlobalVisibleRect(out)) return false;
        // Android getGlobalVisibleRect is in root-view coordinates, not screen/window coordinates.
        out.offset(rootPosition[0], rootPosition[1]);
        return out.width() > 0 && out.height() > 0;
    }

    private static boolean contains(Rect outer, Rect inner) {
        return outer.left <= inner.left && outer.top <= inner.top && outer.right >= inner.right && outer.bottom >= inner.bottom;
    }

    private Reason hasUncapturableSurface(View source, View consumer) {
        ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(source);
        while (!queue.isEmpty()) {
            View view = queue.removeFirst();
            if (view == consumer || view == excludedRef.get() || !view.isShown() || view.getAlpha() <= 0f) continue;
            boolean intersects = windowVisible(view, visible) && Rect.intersects(visible, requested);
            ViewGroup group = view instanceof ViewGroup ? (ViewGroup) view : null;
            // Unclipped groups can draw children outside their own bounds: descend conservatively.
            if (!intersects && (group == null || group.getClipChildren())) continue;
            if (visited == TREE_BUDGET) return Reason.TREE_BUDGET;
            visited++;
            if (intersects && (view instanceof SurfaceView || view instanceof TextureView)
                    && !conversationPullWallpaper(view, source)) return Reason.SURFACE;
            if (group != null) for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
        }
        return Reason.READY;
    }

    /**
     * The conversation page owns this full-window decorative GL surface. It has no Canvas
     * content to sample, so it must not suppress sampling of the native list behind it.
     */
    private static boolean conversationPullWallpaper(View surface, View source) {
        if (!"com.tencent.mm.dynamicbackground.view.DynamicBackgroundGLSurfaceView".equals(surface.getClass().getName())
                || !(surface.getParent() instanceof View)
                || !"com.tencent.mm.plugin.multitask.ui.bg.DynamicBgContainer".equals(surface.getParent().getClass().getName()))
            return false;
        View page = surface.getParent().getParent() instanceof View ? (View) surface.getParent().getParent() : null;
        if (page == null || !"com.tencent.mm.ui.conversation.MainUIView".equals(page.getClass().getName())) return false;
        for (View current = page; current != null;
             current = current.getParent() instanceof View ? (View) current.getParent() : null)
            if (current == source) return true;
        return false;
    }

    private void recordParent(RecordingCanvas capture, View source, View content) {
        // Do not call parent.draw(): hardware drawChild may reuse this consumer's cached RenderNode,
        // bypassing its draw() reentrancy guard. Record actual host backgrounds and native pager only.
        while (!backgrounds.isEmpty()) {
            View view = backgrounds.removeLast();
            Drawable background = view.getBackground();
            if (background == null) continue;
            view.getLocationInWindow(childPosition);
            Rect bounds = new Rect(background.getBounds());
            int save = capture.save();
            try {
                capture.translate(childPosition[0] - sourcePosition[0], childPosition[1] - sourcePosition[1]);
                background.setBounds(0, 0, view.getWidth(), view.getHeight());
                background.draw(capture);
            } finally { background.setBounds(bounds); capture.restoreToCount(save); }
        }
        content.getLocationInWindow(childPosition);
        int save = capture.save();
        capture.clipRect(childPosition[0] - sourcePosition[0], childPosition[1] - sourcePosition[1],
                childPosition[0] - sourcePosition[0] + content.getWidth(), childPosition[1] - sourcePosition[1] + content.getHeight());
        capture.translate(childPosition[0] - sourcePosition[0] - content.getScrollX(),
                childPosition[1] - sourcePosition[1] - content.getScrollY());
        content.draw(capture);
        capture.restoreToCount(save);
    }

    @Override public void onViewAttachedToWindow(View view) { if (running) { observe(view); changed(); } }
    @Override public void onViewDetachedFromWindow(View view) {
        unobserve(); resetCapture(); windowToken = null; changed();
    }
    @Override public void onLayoutChange(View view, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
        if (l != ol || t != ot || r != or || b != ob) changed();
    }
}
