package wex.ui.core;
public final class WexStore {
    public static final WexStore INSTANCE = new WexStore();
    public boolean getBool(String key, boolean fallback) {
        return "top_profile_enabled".equals(key) || "home_side_panel_enabled".equals(key);
    }
}
