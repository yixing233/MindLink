package android.view;
public class ViewPropertyAnimator {
    private final View view;
    private Runnable end;
    public ViewPropertyAnimator(View view) { this.view = view; }
    public ViewPropertyAnimator translationY(float value) { view.setTranslationY(value); return this; }
    public ViewPropertyAnimator alpha(float value) { view.setAlpha(value); return this; }
    public ViewPropertyAnimator scaleX(float value) { return this; }
    public ViewPropertyAnimator scaleY(float value) { return this; }
    public ViewPropertyAnimator setDuration(long value) { return this; }
    public ViewPropertyAnimator withEndAction(Runnable value) { end = value; return this; }
    public void start() { if (end != null) { Runnable action = end; end = null; action.run(); } }
    public void cancel() { end = null; }
}
