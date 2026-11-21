package android.view;
public final class Window {
    private final android.widget.FrameLayout decor;
    public Window(android.content.Context context) { decor = new android.widget.FrameLayout(context); }
    public View getDecorView() { return decor; }
}
