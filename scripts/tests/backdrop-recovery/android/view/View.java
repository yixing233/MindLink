package android.view;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.List;

/** Controllable platform boundary. Callbacks run only when the test dispatches the fake clock. */
public class View {
    public static final int VISIBLE = 0, INVISIBLE = 4, GONE = 8;
    public interface OnAttachStateChangeListener {
        void onViewAttachedToWindow(View view);
        void onViewDetachedFromWindow(View view);
    }
    public interface OnLayoutChangeListener {
        void onLayoutChange(View view, int l, int t, int r, int b, int ol, int ot, int or, int ob);
    }
    public int width = 300, height = 240, x, y, scrollX, scrollY, layoutDirection, visibility = VISIBLE;
    public int drawCalls, failDraws, invalidations, postCalls, removedCallbacks;
    public boolean attached = true, shown = true, dirty, layoutRequested, dirtyOnDraw;
    public float alpha = 1f;
    public final android.graphics.Matrix matrix = new android.graphics.Matrix();
    public android.graphics.Matrix getMatrix() { return matrix; }
    public String content = "A";
    public Object windowToken = new Object();
    public final List<Long> postedDelays = new ArrayList<>();
    private final Resources resources = new Resources();
    private final ViewTreeObserver observer = new ViewTreeObserver();
    private final List<OnAttachStateChangeListener> attachListeners = new ArrayList<>();
    private final List<OnLayoutChangeListener> layoutListeners = new ArrayList<>();
    private final List<Posted> callbacks = new ArrayList<>();
    private static final class Posted {
        final Runnable runnable; final long due;
        Posted(Runnable runnable, long due) { this.runnable = runnable; this.due = due; }
    }
    public Resources getResources() { return resources; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getTop() { return y; }
    public int getScrollX() { return scrollX; }
    public int getScrollY() { return scrollY; }
    public int getLayoutDirection() { return layoutDirection; }
    public boolean isAttachedToWindow() { return attached; }
    public boolean isShown() { return shown && visibility == VISIBLE && (parent == null || parent.isShown()); }
    public View parent;
    public android.graphics.drawable.Drawable background;
    public Rect clippedBounds;
    public boolean throwBounds;
    public View getParent() { return parent; }
    public View getRootView() { return parent == null ? this : parent.getRootView(); }
    public android.graphics.drawable.Drawable getBackground() { return background; }
    public void getLocationOnScreen(int[] result) { getLocationInWindow(result); }
    public boolean getGlobalVisibleRect(Rect result) {
        if (throwBounds) throw new IllegalStateException("injected bounds failure");
        if (!isShown() || width <= 0 || height <= 0) return false;
        View root = getRootView();
        result.set(x - root.x, y - root.y, x - root.x + width, y - root.y + height);
        if (clippedBounds != null) result.set(clippedBounds.left, clippedBounds.top, clippedBounds.right, clippedBounds.bottom);
        return result.width() > 0 && result.height() > 0;
    }
    public Object getWindowToken() { return windowToken; }
    public boolean isDirty() { return dirty; }
    public boolean isLayoutRequested() { return layoutRequested; }
    public int getVisibility() { return visibility; }
    public float getAlpha() { return alpha; }
    public void setAlpha(float value) { alpha = value; }
    public void setVisibility(int value) { visibility = value; }
    public void getLocationInWindow(int[] result) { result[0] = x; result[1] = y; }
    public boolean getLocalVisibleRect(Rect result) { result.set(0, 0, width, height); return isShown(); }
    public Runnable onDraw, onInvalidatedFrame;
    private final Runnable frame = () -> {
        if (attached && isShown() && onInvalidatedFrame != null) onInvalidatedFrame.run();
    };
    public void draw(Canvas canvas) {
        drawCalls++; canvas.mark(content);
        if (onDraw != null) onDraw.run();
        if (dirtyOnDraw) dirty = true;
        if (failDraws > 0) { failDraws--; throw new IllegalStateException("injected View.draw failure"); }
    }
    public void invalidate() {
        invalidations++;
        if (onInvalidatedFrame != null && callbacks.stream().noneMatch(value -> value.runnable == frame))
            callbacks.add(new Posted(frame, SystemClock.uptimeMillis() + 1L));
    }
    public boolean postDelayed(Runnable runnable, long delay) {
        postCalls++; postedDelays.add(delay); callbacks.add(new Posted(runnable, SystemClock.uptimeMillis() + delay));
        return true;
    }
    public boolean removeCallbacks(Runnable runnable) {
        int before = callbacks.size(); callbacks.removeIf(value -> value.runnable == runnable);
        removedCallbacks += before - callbacks.size(); return before != callbacks.size();
    }
    public int pendingCallbacks() { return callbacks.size(); }
    public long nextCallbackAt() { return callbacks.stream().mapToLong(value -> value.due).min().orElse(-1L); }
    public void dispatchDueCallbacks() {
        while (true) {
            Posted next = null;
            for (Posted value : callbacks) if (value.due <= SystemClock.uptimeMillis()
                    && (next == null || value.due < next.due)) next = value;
            if (next == null) return;
            callbacks.remove(next); next.runnable.run();
        }
    }
    public ViewTreeObserver getViewTreeObserver() { return observer; }
    public void addOnAttachStateChangeListener(OnAttachStateChangeListener value) { attachListeners.add(value); }
    public void removeOnAttachStateChangeListener(OnAttachStateChangeListener value) { attachListeners.remove(value); }
    public void addOnLayoutChangeListener(OnLayoutChangeListener value) { layoutListeners.add(value); }
    public void removeOnLayoutChangeListener(OnLayoutChangeListener value) { layoutListeners.remove(value); }
    public int attachListenerCount() { return attachListeners.size(); }
    public int layoutListenerCount() { return layoutListeners.size(); }
    public void detach() {
        attached = false;
        for (OnAttachStateChangeListener value : List.copyOf(attachListeners)) value.onViewDetachedFromWindow(this);
    }
    public void attach() {
        attached = true;
        for (OnAttachStateChangeListener value : List.copyOf(attachListeners)) value.onViewAttachedToWindow(this);
    }
}
