package android.view;

import android.content.Context;
import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

/** Minimal observable View lifecycle/layout double; no grouping behavior is implemented here. */
public class View {
    public static final int IMPORTANT_FOR_ACCESSIBILITY_NO = 2, IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS = 4;
    public static final int VISIBLE = 0, INVISIBLE = 4;
    private final Context context;
    private ViewGroup.LayoutParams params;
    private ViewGroup parent;
    private boolean attached = true, selected;
    private int visibility = VISIBLE, width = 400, height = 48;
    private float x, y;
    private float alpha = 1f;
    private android.graphics.drawable.Drawable background;
    public Rect clipBounds;
    private OnClickListener click;
    private OnLongClickListener longClick;
    private final List<OnAttachStateChangeListener> listeners = new ArrayList<>();
    private final ViewTreeObserver observer = new ViewTreeObserver();
    public int layouts;
    public View(Context context) { this.context = context; }
    public Context getContext() { return context; }
    public Resources getResources() { return new Resources(); }
    public static final class Resources { public Metrics getDisplayMetrics() { return new Metrics(); } }
    public static final class Metrics { public float density = 1f; }
    public void post(Runnable task) { new android.os.Handler(android.os.Looper.getMainLooper()).post(task); }
    public void setLayoutParams(ViewGroup.LayoutParams value) { params = value; }
    public ViewGroup.LayoutParams getLayoutParams() { return params; }
    public void setImportantForAccessibility(int value) { }
    public boolean isAttachedToWindow() { return attached; }
    public boolean isShown() { return attached && visibility == VISIBLE; }
    public ViewGroup getParent() { return parent; }
    public void parentForTest(ViewGroup value) { parent = value; }
    public void addOnAttachStateChangeListener(OnAttachStateChangeListener listener) { listeners.add(listener); }
    public void removeOnAttachStateChangeListener(OnAttachStateChangeListener listener) { listeners.remove(listener); }
    public void attachedForTest(boolean value) {
        if (attached == value) return;
        attached = value;
        if (value) onAttachedToWindow(); else onDetachedFromWindow();
        for (OnAttachStateChangeListener listener : new ArrayList<>(listeners)) {
            if (value) listener.onViewAttachedToWindow(this); else listener.onViewDetachedFromWindow(this);
        }
        if (this instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) group.getChildAt(i).attachedForTest(value);
    }
    protected void onAttachedToWindow() { }
    protected void onDetachedFromWindow() { }
    protected void onSizeChanged(int w, int h, int oldw, int oldh) { }
    public ViewTreeObserver getViewTreeObserver() { return observer; }
    public void requestLayout() { layouts++; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public void sizeForTest(int w, int h) { int oldw = width, oldh = height; width = w; height = h; onSizeChanged(w, h, oldw, oldh); }
    public void getLocationInWindow(int[] location) { location[0] = (int) x; location[1] = (int) y; }
    public int getPaddingTop() { return 0; }
    public int getPaddingBottom() { return 0; }
    public int getPaddingLeft() { return 0; }
    public int getPaddingRight() { return 0; }
    public int getScrollX() { return 0; }
    public int getScrollY() { return 0; }
    public void setX(float value) { x = value; }
    public void setY(float value) { y = value; }
    public void setClipBounds(Rect value) { clipBounds = new Rect();clipBounds.set(value.left,value.top,value.right,value.bottom); }
    public void setAlpha(float value) { alpha=value; }
    public float getAlpha() { return alpha; }
    public void setVisibility(int value) { visibility = value; }
    public int getVisibility() { return visibility; }
    public void setElevation(float value) { }
    public void setBackgroundColor(int value) { background = new android.graphics.drawable.ColorDrawable(value); }
    public void setBackground(android.graphics.drawable.Drawable value) { background = value; }
    public android.graphics.drawable.Drawable getBackground() { return background; }
    public void setPadding(int l, int t, int r, int b) { }
    public void setSelected(boolean value) { selected = value; }
    public boolean isSelected() { return selected; }
    public void setContentDescription(CharSequence value) { }
    public void setOnClickListener(OnClickListener listener) { click = listener; }
    public void setOnLongClickListener(OnLongClickListener listener) { longClick = listener; }
    public boolean performClick() { if (click == null) return false; click.onClick(this); return true; }
    public boolean dispatchTouchEvent(MotionEvent event) { return true; }
    public interface OnClickListener { void onClick(View view); }
    public interface OnLongClickListener { boolean onLongClick(View view); }
    public interface OnAttachStateChangeListener {
        void onViewAttachedToWindow(View view);
        void onViewDetachedFromWindow(View view);
    }
}
