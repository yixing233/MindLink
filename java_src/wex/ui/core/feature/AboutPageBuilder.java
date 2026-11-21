package wex.ui.core.feature;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

/**
 * Platform-only entry used by the existing smali desktop activities.
 *
 * The page is built natively (Material 3) so the isolated Compose desktop payload is not needed;
 * every setting keeps living in the WeChat-side panel.
 */
public final class AboutPageBuilder {
    private AboutPageBuilder() { }
    public static View build(Activity activity) { return DesktopPageView.build(activity); }
    public static View build(Activity activity, Bundle saved) { return DesktopPageView.build(activity); }
    public static void save(Activity activity, Bundle state) { }
    public static boolean back(Activity activity) { return false; }
    public static void lifecycle(Activity activity, boolean resumed) { }
    public static void dispose(Activity activity) { }
}
