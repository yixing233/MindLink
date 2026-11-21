package android.view;

import android.content.Context;
import android.graphics.drawable.Drawable;

public class View {
    public static final int VISIBLE = 0, INVISIBLE = 4, IMPORTANT_FOR_ACCESSIBILITY_NO = 2, IMPORTANT_FOR_ACCESSIBILITY_YES = 1;
    private final ViewTreeObserver observer = new ViewTreeObserver();
    private final ViewPropertyAnimator propertyAnimator = new ViewPropertyAnimator(this);
    private OnLongClickListener longClick;
    private boolean longClickable;
    public interface OnLongClickListener { boolean onLongClick(View view); }
    public void setOnLongClickListener(OnLongClickListener value) { longClick = value; }
    public void setLongClickable(boolean value) { longClickable = value; }
    public boolean isLongClickable() { return longClickable; }
    public boolean performLongClick() { return longClick != null && longClick.onLongClick(this); }
    public boolean hasOnClickListeners() { return click != null; }
    public View findViewById(int value) { return id == value ? this : null; }
    public ViewTreeObserver getViewTreeObserver() { return observer; }
    public ViewPropertyAnimator animate() { return propertyAnimator; }
    public void postOnAnimation(Runnable task) { android.os.Handler.frameForTest(task); }
    public void setWillNotDraw(boolean value) { }
    public void setOutlineProvider(ViewOutlineProvider value) { }
    public void invalidateOutline() { }
    public int layoutRequests, measuredWidth, measuredHeight, drawCalls;
    private boolean layoutRequested, laidOut;
    public void requestLayout() { layoutRequests++; layoutRequested = true; }
    public boolean isLayoutRequested() { return layoutRequested; }
    public boolean isLaidOut() { return laidOut; }
    public void postInvalidateOnAnimation() { invalidate(); }
    public boolean isPaddingRelative() { return false; }
    public int getPaddingStart() { return getPaddingLeft(); }
    public int getPaddingEnd() { return getPaddingRight(); }
    public void setPaddingRelative(int start, int top, int end, int bottom) { setPadding(start, top, end, bottom); }
    public void setScaleX(float value) { }
    public void setScaleY(float value) { }
    public boolean performHapticFeedback(int value) { return true; }
    public boolean getLocalVisibleRect(android.graphics.Rect value) { return getGlobalVisibleRect(value); }
    public boolean dispatchTouchEvent(MotionEvent event) { return onTouchEvent(event); }
    public boolean onTouchEvent(MotionEvent event) { return false; }
    public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) { }
    protected void onMeasure(int width, int height) { }
    protected void setMeasuredDimension(int width, int height) { measuredWidth = width; measuredHeight = height; }
    public void measure(int width, int height) { onMeasure(width, height); }
    public void draw(android.graphics.Canvas canvas) { drawCalls++; onDraw(canvas); if (this instanceof ViewGroup) ((ViewGroup) this).dispatchDraw(canvas); }
    public WindowInsets rootInsets;
    public WindowInsets getRootWindowInsets() { return getRootView().rootInsets; }
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) { }
    public static class MeasureSpec { public static int getSize(int value) { return value; } }
    private OnScrollChangeListener scrollListener;
    public interface OnScrollChangeListener { void onScrollChange(View view,int x,int y,int oldX,int oldY); }
    public void setOnScrollChangeListener(OnScrollChangeListener value) { scrollListener=value; }
    protected void scrolledForTest(int x,int y,int oldX,int oldY) { if(scrollListener!=null)scrollListener.onScrollChange(this,x,y,oldX,oldY); }
    public void setContentDescription(CharSequence value) {}
    public void setImportantForAccessibility(int value) {}
    public int getWindowVisibility() { return VISIBLE; }
    protected void onAttachedToWindow() {}
    protected void onDetachedFromWindow() {}
    protected void onWindowVisibilityChanged(int value) {}
    protected void onDraw(android.graphics.Canvas canvas) {}
    public void attachForTest() { onAttachedToWindow();for(OnAttachStateChangeListener listener:attachListeners)listener.onViewAttachedToWindow(this);if(this instanceof ViewGroup){ViewGroup group=(ViewGroup)this;for(int i=0;i<group.getChildCount();i++)group.getChildAt(i).attachForTest();} }
    public void detachForTest() { if(this instanceof ViewGroup){ViewGroup group=(ViewGroup)this;for(int i=0;i<group.getChildCount();i++)group.getChildAt(i).detachForTest();}onDetachedFromWindow();for(OnAttachStateChangeListener listener:attachListeners)listener.onViewDetachedFromWindow(this); }
    public void removeCallbacks(Runnable task) { android.os.Handler.removeForTest(task); }
    public boolean postDelayed(Runnable task,long delay) { return android.os.Handler.delayedForTest(task); }
    public static final int GONE = 8;
    public static final int LAYOUT_DIRECTION_LTR = 0;
    public static final int LAYOUT_DIRECTION_RTL = 1;
    public static final int TEST_PRESSED = 16842919;
    private final Context context;
    ViewParent parent;
    private ViewGroup.LayoutParams params;
    private Object tag;
    private int left, top, right, bottom;
    private int padLeft, padTop, padRight, padBottom;
    private int minHeight, visibility, direction;
    private boolean pressed, duplicateState;
    private Drawable background, foreground;
    private OnClickListener click;
    private android.content.res.ColorStateList backgroundTint;
    private float elevation, translationZ;
    private android.animation.StateListAnimator animator;
    private boolean enabled = true, selected;
    private Object rootToken, tokenOverride;
    private float alpha = 1f, translationX, translationY;
    public float getTranslationX() { return translationX; }
    public float getTranslationY() { return translationY; }
    public void setTranslationX(float value) { translationX = value; }
    public void setTranslationY(float value) { translationY = value; }
    public int invalidations;
    public android.content.res.Resources getResources() { return context.getResources(); }
    public View getRootView() { View current = this; while (current.getParent() instanceof View) current = (View) current.getParent(); return current; }
    public Object getWindowToken() { return tokenOverride != null ? tokenOverride : getRootView().rootToken; }
    public boolean isAttachedToWindow() { return getWindowToken() != null; }
    public void attachRootForTest(Object token) { rootToken = token; }
    public void setWindowTokenForTest(Object token) { tokenOverride = token; }
    public float getAlpha() { return alpha; }
    public void setAlpha(float value) { alpha = value; }
    public boolean isShown() {
        if (!isAttachedToWindow()) return false;
        for (View current = this; current != null; current = current.getParent() instanceof View ? (View) current.getParent() : null)
            if (current.getVisibility() != VISIBLE) return false;
        return true;
    }
    public void getLocationInWindow(int[] location) {
        location[0] = left + Math.round(translationX); location[1] = top + Math.round(translationY);
        for (View current = getParent() instanceof View ? (View) getParent() : null; current != null;
             current = current.getParent() instanceof View ? (View) current.getParent() : null) {
            location[0] += current.getLeft() + Math.round(current.getTranslationX()) - current.getScrollX();
            location[1] += current.getTop() + Math.round(current.getTranslationY()) - current.getScrollY();
        }
    }
    public boolean getGlobalVisibleRect(android.graphics.Rect result) {
        if (!isShown() || alpha <= 0f || getWidth() <= 0 || getHeight() <= 0) return false;
        int[] position = new int[2]; getLocationInWindow(position);
        result.left = position[0]; result.top = position[1]; result.right = position[0] + getWidth(); result.bottom = position[1] + getHeight();
        return true;
    }
    public boolean post(Runnable task) { return new android.os.Handler(android.os.Looper.getMainLooper()).post(task); }
    public void invalidate() { invalidations++; }
    private final java.util.List<OnLayoutChangeListener> layoutListeners = new java.util.ArrayList<>();
    private final java.util.List<OnAttachStateChangeListener> attachListeners = new java.util.ArrayList<>();
    private OnApplyWindowInsetsListener insetsListener;
    public interface OnApplyWindowInsetsListener { WindowInsets onApplyWindowInsets(View view, WindowInsets insets); }
    public interface OnAttachStateChangeListener {
        void onViewAttachedToWindow(View view);
        void onViewDetachedFromWindow(View view);
    }
    public void addOnAttachStateChangeListener(OnAttachStateChangeListener listener) { attachListeners.add(listener); }
    public void removeOnAttachStateChangeListener(OnAttachStateChangeListener listener) { attachListeners.remove(listener); }
    public void setOnApplyWindowInsetsListener(OnApplyWindowInsetsListener listener) { insetsListener = listener; }
    public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) { return insetsListener == null ? insets : insetsListener.onApplyWindowInsets(this, insets); }
    public void requestApplyInsets() {}

    public interface OnLayoutChangeListener {
        void onLayoutChange(View view, int left, int top, int right, int bottom,
                            int oldLeft, int oldTop, int oldRight, int oldBottom);
    }
    public void addOnLayoutChangeListener(OnLayoutChangeListener listener) { layoutListeners.add(listener); }

    public View(Context context) { this.context = context; }
    private int id;
    public void setId(int value) { id = value; }
    public int getId() { return id; }
    public interface OnClickListener { void onClick(View view); }
    public Context getContext() { return context; }
    public ViewParent getParent() { return parent; }
    public void setTag(Object tag) { this.tag = tag; }
    public Object getTag() { return tag; }
    public View findViewWithTag(Object value) { return value.equals(tag) ? this : null; }
    public void setLayoutParams(ViewGroup.LayoutParams params) {
        if (params == null) throw new NullPointerException("Layout parameters cannot be null");
        this.params = params;
    }
    public ViewGroup.LayoutParams getLayoutParams() { return params; }
    public void layout(int left, int top, int right, int bottom) {
        laidOut = true; layoutRequested = false;
        int oldLeft = this.left, oldTop = this.top, oldRight = this.right, oldBottom = this.bottom;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        for (OnLayoutChangeListener listener : layoutListeners)
            listener.onLayoutChange(this, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom);
        onLayout(oldLeft != left || oldTop != top || oldRight != right || oldBottom != bottom, left, top, right, bottom);
    }
    public int getWidth() { return right - left; }
    public int getHeight() { return bottom - top; }
    public int getLeft() { return left; }
    public int getTop() { return top; }
    public int getRight() { return right; }
    public int getBottom() { return bottom; }
    public int getScrollX() { return 0; }
    public int getScrollY() { return 0; }
    public void setPadding(int left, int top, int right, int bottom) {
        padLeft = left;
        padTop = top;
        padRight = right;
        padBottom = bottom;
    }
    public int getPaddingLeft() { return padLeft; }
    public int getPaddingTop() { return padTop; }
    public int getPaddingRight() { return padRight; }
    public int getPaddingBottom() { return padBottom; }
    public int getVisibility() { return visibility; }
    public void setVisibility(int value) { visibility = value; }
    public int getLayoutDirection() { return direction; }
    public void setLayoutDirection(int value) { direction = value; }
    public void setMinimumHeight(int value) { minHeight = value; }
    public int getMinimumHeight() { return minHeight; }
    public void setBackground(Drawable value) { background = value; }
    public void setBackgroundColor(int value) { background = new android.graphics.drawable.ColorDrawable(value); }
    public Drawable getBackground() { return background; }
    public void setForeground(Drawable value) { foreground = value; }
    public Drawable getForeground() { return foreground; }
    public void setBackgroundTintList(android.content.res.ColorStateList value) { backgroundTint = value; }
    public android.content.res.ColorStateList getBackgroundTintList() { return backgroundTint; }
    public void setElevation(float value) { elevation = value; }
    public float getElevation() { return elevation; }
    public void setTranslationZ(float value) { translationZ = value; }
    public float getTranslationZ() { return translationZ; }
    public void setStateListAnimator(android.animation.StateListAnimator value) { animator = value; }
    public android.animation.StateListAnimator getStateListAnimator() { return animator; }
    public void setSelected(boolean value) { selected = value; }
    public boolean isSelected() { return selected; }
    public void setEnabled(boolean value) { enabled = value; }
    public boolean isEnabled() { return enabled; }
    public void setClickable(boolean value) {}
    public void setFocusable(boolean value) {}
    public void setDuplicateParentStateEnabled(boolean value) { duplicateState = value; }
    public boolean isDuplicateParentStateEnabled() { return duplicateState; }
    public void setOnClickListener(OnClickListener value) { click = value; }
    public boolean performClick() {
        if (click == null) return false;
        click.onClick(this);
        return true;
    }
    public boolean isPressed() { return pressed; }
    public void setPressed(boolean value) {
        pressed = value;
        int[] state = value ? new int[]{TEST_PRESSED} : new int[0];
        if (background != null) background.setState(state);
        if (foreground != null) foreground.setState(state);
    }
}
