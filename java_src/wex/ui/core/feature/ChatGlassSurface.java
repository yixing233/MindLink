package wex.ui.core.feature;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.os.SystemClock;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The home-bar liquid glass used as a card background, so the floating chat header and the
 * floating chat footer carry the same material as the bottom navigation bar.
 *
 * <p>The material is a background Drawable, not a separate overlay View: the host card keeps
 * its own {@code View.draw} order, its elevation shadow and its outline clip, and only its
 * fill changes. Sampling records the largest sibling content view (the message list) into a
 * {@code RenderNode}; the consumer is never part of that recording, so no draw reentrancy
 * guard is needed and the card never captures itself.</p>
 *
 * <p>Every unusable environment (no sibling content, software canvas, captured surface,
 * window mismatch) falls back to the previous solid card instead of a half transparent shell.</p>
 */
final class ChatGlassSurface extends Drawable {
    /** nav_bar_glass_mode: 0 plain surface, 1 frosted, 2 liquid glass. Shared with the home bar. */
    static final int PLAIN = 0, FROSTED = 1, LIQUID = 2;

    private final View consumer;
    private final LiquidGlassLayer glass = new LiquidGlassLayer();
    private final Map<String, LiquidGlassLayer> regions = new LinkedHashMap<>();
    private final LiquidGlassDecoration decoration = new LiquidGlassDecoration();
    private final RectF rect = new RectF();
    private final Rect bounds = new Rect();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ViewBackdropSampler sampler;
    private View source, content, excluded;
    private WeakReference<View> backdropSource;
    private WeakReference<View> backdropContent;
    private int mode = LIQUID, color = 0xFFF7F7F7;
    private float radius, blur, density = 1f;
    private boolean dark, running;
    private int visibleHeight = -1;
    private long freshAt, scrollSignature;

    ChatGlassSurface(View consumer) { this.consumer = consumer; }

    /** The same switch and blur radius the home bar reads: one material setting for both. */
    static int mode() { return BubbleSettings.readInt(NavigationBarFeature.KEY_EFFECT, LIQUID, PLAIN, LIQUID); }

    static int blurRadiusDp(int mode) {
        return BubbleSettings.readInt(NavigationBarFeature.KEY_BLUR, mode == FROSTED ? 25 : 8, 0, 40);
    }

    void configure(int color, float radius, float density, boolean dark, int mode, int blurDp) {
        this.color = color; this.radius = radius; this.density = density > 0f ? density : 1f;
        this.dark = dark; this.mode = mode; this.blur = blurDp * this.density;
    }

    /** A detached decoration can own a distant chat layout; source == content captures that layout directly. */
    void backdrop(View source, View content) {
        backdropSource = source == null ? null : new WeakReference<>(source);
        backdropContent = source == null || content == null ? null : new WeakReference<>(content);
    }

    void visibleHeight(int height) { visibleHeight = height; }

    @Override public void draw(Canvas canvas) {
        if (visibleHeight > 0 && getBounds().bottom != visibleHeight)
            setBounds(0, 0, consumer.getWidth(), visibleHeight);
        bounds.set(getBounds());
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        rect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
        if (mode == PLAIN || !canvas.isHardwareAccelerated()) { stopSampling(); plain(canvas); return; }
        if (!bind()) { plain(canvas); return; }
        keepFresh();
        LiquidGlassLayer layer = layer();
        boolean sampled = layer.drawBase(canvas, sampler, rect, radius, blur, mode == LIQUID, dark, density, true);
        if (mode == LIQUID && sampled) {
            decoration.highlight(canvas, rect, radius, density, 0.75f, 0f, -1f, -45f);
        }
    }

    /**
     * Binds the sampler to the card's parent and to the largest sibling content view. The
     * sampler requires both endpoints to be direct children of one source view.
     */
    private boolean bind() {
        View fixedSource = backdropSource == null ? null : backdropSource.get();
        View fixedContent = backdropContent == null ? null : backdropContent.get();
        if (fixedSource != null && fixedContent != null && !contains(fixedContent, consumer)) {
            if (sampler != null && source != fixedSource) stopSampling();
            if (sampler == null) sampler = new ViewBackdropSampler(fixedSource, consumer);
            if (source != fixedSource || content != fixedContent) {
                source = fixedSource; content = fixedContent;
                sampler.source(fixedSource, fixedContent);
            }
            sampler.start();
            running = true;
            return true;
        }
        if (consumer == null || !(consumer.getParent() instanceof View)) { stop(); return false; }
        View child = consumer;
        View host = null, backer = null;
        for (int depth = 0; depth < 5 && child.getParent() instanceof View; depth++) {
            host = (View) child.getParent();
            backer = backer(host, child);
            if (backer != null) break;
            child = host;
        }
        if (backer == null) { stop(); return false; }
        if (sampler != null && (source != host || excluded != child)) stopSampling();
        if (sampler == null) sampler = new ViewBackdropSampler(host, consumer, child);
        excluded = child;
        if (source != host || content != backer) {
            source = host; content = backer;
            // The constructor seeds content with the source; a card must record the sibling list
            // only, otherwise the whole host (this card included) is captured and re-enters.
            sampler.source(host, backer);
        }
        sampler.start();
        running = true;
        return true;
    }

    private static boolean contains(View root, View view) {
        for (View current = view; current != null;
             current = current.getParent() instanceof View ? (View) current.getParent() : null)
            if (current == root) return true;
        return false;
    }

    private LiquidGlassLayer layer() {
        // A footer changes size as panels open; its effect has one persistent owner.
        if (visibleHeight >= 0) return glass;
        String key = bounds.left + "," + bounds.top + "," + bounds.right + "," + bounds.bottom;
        LiquidGlassLayer value = regions.get(key);
        if (value == null) {
            if (regions.size() >= 8) releaseRegions();
            value = new LiquidGlassLayer();
            regions.put(key, value);
        }
        return value;
    }

    private void keepFresh() {
        if (consumer == null || !consumer.getClass().getName().contains("tipsbar")) return;
        long now = SystemClock.uptimeMillis();
        long signature = scrollSignature(content, 0);
        if (signature != scrollSignature || now - freshAt >= 120L) {
            scrollSignature = signature; freshAt = now;
            sampler.sourceContentChanged();
        }
    }

    private static long scrollSignature(View view, int depth) {
        if (view == null || depth > 5 || !view.isShown()) return 0L;
        long value = view.getScrollX() * 31L + view.getScrollY();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int count = Math.min(group.getChildCount(), 32);
            value = value * 31 + count;
            for (int i = 0; i < count; i++)
                value = value * 31 + group.getChildAt(i).getTop()
                        + scrollSignature(group.getChildAt(i), depth + 1);
        }
        return value;
    }
    private static View backer(View host, View consumer) {
        if (!(host instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) host;
        View best = null;
        long area = 0L;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child == null || child == consumer || !child.isShown() || child.getAlpha() <= 0f) continue;
            long candidate = (long) child.getWidth() * child.getHeight();
            if (candidate > area) { area = candidate; best = child; }
        }
        return best;
    }

    private void plain(Canvas canvas) {
        fill.setColor(color);
        canvas.drawRoundRect(rect, radius, radius, fill);
    }

    /** Idempotent: called on every frame that cannot sample, and when the host is restored. */
    void stop() {
        stopSampling();
        releaseRegions();
        backdropSource = null; backdropContent = null;
    }

    private void stopSampling() {
        if (sampler != null) { sampler.stop(); sampler = null; }
        if (running) { glass.release(); decoration.release(); running = false; }
        source = null; content = null; excluded = null;
        releaseRegions();
    }

    private void releaseRegions() {
        for (LiquidGlassLayer layer : regions.values()) layer.release();
        regions.clear();
    }

    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter filter) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    /** Keeps a host that clips to the drawable outline rounded instead of square. */
    @Override public void getOutline(Outline outline) {
        Rect area = getBounds();
        if (area.isEmpty()) return;
        if (radius > 0f) outline.setRoundRect(area, radius); else outline.setRect(area);
    }
}
