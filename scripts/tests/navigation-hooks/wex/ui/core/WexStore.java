package wex.ui.core;

public final class WexStore {
    public static final WexStore INSTANCE=new WexStore();
    public boolean enabled = true, floating = true, autoHide = true;
    public boolean getBool(String key,boolean fallback){return "bottom_bar_enabled".equals(key) ? enabled : "nav_bar_use_floating".equals(key) ? floating : "nav_bar_auto_hide_on_scroll".equals(key) ? autoHide : fallback;}
}
