package wex.ui.core.feature;

import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ListView;

import java.lang.ref.WeakReference;

import wex.ui.core.WexStore;

/** One interactive overlay plus a same-height native header, following WeKit's group-tab host. */
final class ConversationGroupTabsHost extends FrameLayout implements ViewTreeObserver.OnPreDrawListener {
    private final ListView list;
    private final FrameLayout header;
    private final View spacer;
    private final ConversationGroupBar bar;
    private final int[] parentPosition = new int[2], listPosition = new int[2], headerPosition = new int[2];
    private final Rect visible = new Rect();
    private ViewTreeObserver observer;
    private MotionEvent down;
    private boolean forwarding, horizontal, disposed;
    private int headerIndex;
    private final int slop;
    private final OnAttachStateChangeListener listListener = new OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View view) { }
        @Override public void onViewDetachedFromWindow(View view) {
            // Home can stay detached while a conversation is open. Removing its header here
            // changes the native row offsets and forces a second mount on return.
            WeakReference<ListView> ref = new WeakReference<>(list);
            list.post(() -> {
                ListView target = ref.get();
                if (target == null || target.isAttachedToWindow()) return;
                android.app.Activity activity = ChatToolbarLifecycle.findActivity(target.getContext());
                if (activity != null && (activity.isDestroyed() || activity.isFinishing())) {
                    ConversationGroupingFeature.INSTANCE.releaseList(target);
                }
            });
        }
    };

    ConversationGroupTabsHost(ListView list) {
        super(list.getContext());
        this.list = list;
        header = new FrameLayout(getContext());
        spacer = new View(getContext());
        header.addView(spacer, new LayoutParams(LayoutParams.MATCH_PARENT, 0));
        header.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        bar = new ConversationGroupBar(getContext());
        addView(bar, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        setVisibility(INVISIBLE);
        setElevation(1f);
    }

    void install() {
        ViewGroup parent = list.getParent() instanceof ViewGroup ? (ViewGroup) list.getParent() : null;
        if (parent == null) {
            throw new IllegalStateException("Conversation overlay host missing");
        }
        list.addHeaderView(header, null, false);
        headerIndex = list.getHeaderViewsCount() - 1;
        try {
            ViewGroup.LayoutParams params = parent instanceof FrameLayout
                    ? new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                    : parent instanceof android.widget.RelativeLayout
                    ? new android.widget.RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                    : new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            parent.addView(this, params);
        } catch (RuntimeException error) { list.removeHeaderView(header); throw error; }
        list.addOnAttachStateChangeListener(listListener);
        refresh();
    }

    void refresh() { if (!disposed) bar.rebuild(); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        observer = getViewTreeObserver(); observer.addOnPreDrawListener(this);
    }

    @Override protected void onDetachedFromWindow() {
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
        observer = null;
        if (down != null) down.recycle(); down = null;
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (spacer.getLayoutParams().height != h) {
            spacer.getLayoutParams().height = h; spacer.requestLayout();
        }
    }

    @Override public boolean onPreDraw() {
        if (disposed || !list.isShown() || getHeight() == 0 || !(getParent() instanceof ViewGroup)) {
            hideTabs();
            return true;
        }
        View parent = (View) getParent();
        parent.getLocationInWindow(parentPosition);
        list.getLocationInWindow(listPosition);
        View actionBar = ConversationListBridge.actionBar(list);
        if (actionBar == null || !actionBar.isShown() || actionBar.getAlpha() <= 0f) { hideTabs(); return true; }
        boolean headerShown = header.isAttachedToWindow() && header.isShown();
        if (headerShown) {
            int position = list.getPositionForView(header);
            if (position >= 0) headerIndex = position;
        }
        boolean pinned = WexStore.INSTANCE.getBool(ConversationGroupingFeature.KEY_PIN, true);
        if (!headerShown && (!pinned || list.getFirstVisiblePosition() <= headerIndex)) { hideTabs(); return true; }
        int top = listPosition[1] + list.getPaddingTop();
        if (actionBar != null) {
            actionBar.getLocationInWindow(headerPosition);
            top = Math.max(top, headerPosition[1] + actionBar.getHeight());
        }
        int viewportTop = top;
        if (headerShown) { header.getLocationInWindow(headerPosition); top = pinned ? Math.max(top, headerPosition[1]) : headerPosition[1]; }
        int width = list.getWidth() - list.getPaddingLeft() - list.getPaddingRight();
        if (getLayoutParams().width != width) { getLayoutParams().width = width; requestLayout(); }
        setX(listPosition[0] + list.getPaddingLeft() - parentPosition[0] + parent.getScrollX());
        setY(top - parentPosition[1] + parent.getScrollY());
        int clipTop = Math.max(0, viewportTop - top);
        int clipBottom = Math.min(getHeight(), listPosition[1] + list.getHeight() - list.getPaddingBottom() - top);
        visible.set(0, clipTop, width, clipBottom);
        setClipBounds(visible);
        if (clipBottom <= clipTop) hideTabs();
        else { setAlpha(actionBar == null ? 1f : actionBar.getAlpha()); setVisibility(VISIBLE); }
        return true;
    }

    private void hideTabs() {
        visible.set(0,0,0,0);
        setClipBounds(visible);
        setAlpha(0f);
        if (!forwarding) setVisibility(INVISIBLE);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (!visible.contains((int) event.getX(), (int) event.getY())) return false;
            if (down != null) down.recycle();
            down = MotionEvent.obtain(event); forwarding = false; horizontal = false;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && down != null && !forwarding && !horizontal) {
            float dx = Math.abs(event.getRawX() - down.getRawX()), dy = Math.abs(event.getRawY() - down.getRawY());
            if (Math.max(dx, dy) > slop) {
                horizontal = dx >= dy;
                if (!horizontal) {
                    MotionEvent cancel = MotionEvent.obtain(event); cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel); cancel.recycle();
                    forwarding = true; forward(down);
                }
            }
        }
        try {
            if (forwarding) { forward(event); return true; }
            return super.dispatchTouchEvent(event);
        } finally {
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                if (down != null) down.recycle(); down = null; forwarding = false;
            }
        }
    }

    private void forward(MotionEvent event) {
        getLocationInWindow(parentPosition); list.getLocationInWindow(listPosition);
        MotionEvent copy = MotionEvent.obtain(event);
        copy.offsetLocation(parentPosition[0] - listPosition[0], parentPosition[1] - listPosition[1]);
        try { list.dispatchTouchEvent(copy); } finally { copy.recycle(); }
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        list.removeOnAttachStateChangeListener(listListener);
        list.removeHeaderView(header);
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
    }
}
