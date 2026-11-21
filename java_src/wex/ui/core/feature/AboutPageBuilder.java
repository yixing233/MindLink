package wex.ui.core.feature;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

/** Platform-only entry used by the existing smali desktop activities. */
public final class AboutPageBuilder {
    private AboutPageBuilder() { }
    public static View build(Activity activity) { return ComposePayloadBridge.build(activity,null); }
    public static View build(Activity activity,Bundle saved) { return ComposePayloadBridge.build(activity,saved); }
    public static void save(Activity activity,Bundle state) { ComposePayloadBridge.save(activity,state); }
    public static boolean back(Activity activity) { return ComposePayloadBridge.back(activity); }
    public static void lifecycle(Activity activity,boolean resumed) { ComposePayloadBridge.lifecycle(activity,resumed); }
    public static void dispose(Activity activity) { ComposePayloadBridge.dispose(activity); }
}
