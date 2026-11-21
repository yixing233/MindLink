package android.app;

public class Activity extends android.content.Context {
    private final android.view.Window window = new android.view.Window(this);
    private final android.window.OnBackInvokedDispatcher back = new android.window.OnBackInvokedDispatcher();
    public android.window.OnBackInvokedDispatcher getOnBackInvokedDispatcher() { return back; }
    public void setContentView(android.view.View view) { ((android.view.ViewGroup)window.getDecorView()).removeAllViews(); ((android.view.ViewGroup)window.getDecorView()).addView(view); }
    private boolean finishing, destroyed;
    public final java.util.List<android.content.Intent> launches = new java.util.ArrayList<>();
    public android.view.Window getWindow() { return window; }
    public void startActivity(android.content.Intent intent) { launches.add(intent); }
    public boolean isFinishing() { return finishing; }
    public boolean isDestroyed() { return destroyed; }
    public void finish() { finishing = true; }
    public void onResume() { de.robv.android.xposed.XposedBridge.baseLifecycle(this, "onResume", false); }
    public void onPause() { de.robv.android.xposed.XposedBridge.baseLifecycle(this, "onPause", true); }
    public void onDestroy() { de.robv.android.xposed.XposedBridge.baseLifecycle(this, "onDestroy", true); destroyed = true; }
    public void destroyForTest() { destroyed = true; }
}
