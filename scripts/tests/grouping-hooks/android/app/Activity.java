package android.app;
public class Activity extends android.content.Context {
    private final android.view.Window window = new android.view.Window(this);
    public void onResume() { }
    public android.view.Window getWindow() { return window; }
    public boolean destroyed, finishing;
    public boolean isDestroyed() { return destroyed; }
    public boolean isFinishing() { return finishing; }
}
