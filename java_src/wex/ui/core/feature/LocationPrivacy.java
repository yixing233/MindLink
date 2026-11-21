package wex.ui.core.feature;

import wex.ui.core.WexStore;

/** 位置相关请求共用的授权门禁，读取失败时禁止联网。 */
public final class LocationPrivacy {

    public static final String KEY_ENABLED = "location_network_enabled";

    private LocationPrivacy() {
    }

    public static boolean isEnabled() {
        try {
            return WexStore.INSTANCE.getBool(KEY_ENABLED, false);
        } catch (Throwable t) {
            return false;
        }
    }
}
