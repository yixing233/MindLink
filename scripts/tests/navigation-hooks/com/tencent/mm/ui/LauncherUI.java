package com.tencent.mm.ui;

/** Super callbacks run before the host initializes its views, exactly as on Android. */
public class LauncherUI extends android.app.Activity {
    public Runnable afterSuperResume;
    public boolean completedResume;
    @Override public void onResume() {
        completedResume = false;
        super.onResume();
        if (afterSuperResume != null) afterSuperResume.run();
        completedResume = true;
    }
}
