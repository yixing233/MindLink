package android.app;
import android.content.Context;
import android.content.Intent;
public class Activity extends Context {
    public int opened;
    public boolean isFinishing() { return false; }
    public boolean isDestroyed() { return false; }
    public void startActivity(Intent intent) { opened++; }
}
