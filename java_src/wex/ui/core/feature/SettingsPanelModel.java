package wex.ui.core.feature;

import android.app.Activity;
import android.os.Bundle;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import wex.ui.core.WexStore;

/**
 * Data contract between the module and the isolated Material3 settings panel.
 * The Compose side never touches WexStore, feature classes or host objects.
 */
final class SettingsPanelModel {

    private static final String LOG_PATH = "/sdcard/Android/media/com.tencent.mm/Mdl/log.txt";
    /** 24 小时内有过日志写入视为已激活（与旧 AboutCard 的判定一致）。 */
    private static final long ACTIVE_WINDOW_MS = 24L * 60 * 60 * 1000;
    /** 四页蒙层的不透明度共用一个值，与 TabBgDrawHook 的读取口径一致。 */
    private static final String TAB_ALPHA_PREFIX = "tab_bg_alpha_";
    private static final String TAB_ALPHA_KEY = TAB_ALPHA_PREFIX + "0";
    private static final int TAB_ALPHA_DEFAULT = 70;

    final Bundle values = new Bundle();
    final Map<String, Function<String, String>> writers = new HashMap<>();
    final Map<String, Runnable> actions = new HashMap<>();
    final Map<String, Function<String, String>> readers = new HashMap<>();

    private final Activity activity;

    private SettingsPanelModel(Activity activity) { this.activity = activity; }

    static SettingsPanelModel build(final Activity activity) {
        SettingsPanelModel model = new SettingsPanelModel(activity);
        model.collect();
        return model;
    }

    private void collect() {
        // ---------- 首页 ----------
        flag("top_scan_enabled", false);
        flag("tool_scan", true);
        flag("tool_group", true);
        flag("tool_qrcode", true);
        flag("tool_pay", true);
        flag("tool_friend", true);
        flag("conversation_filter_enabled", false);
        flag("bottom_bar_enabled", false, () -> NavigationBarFeature.refresh());
        flag("home_side_panel_enabled", false);
        flag("top_profile_enabled", false);
        flag("top_search_bar", true);
        text("top_title", "");
        text("top_nickname", "");
        text("top_status", "");
        text("top_search_hint", "");
        choice("top_dot_color", "green");
        image("top_avatar_path");
        flag("mark_all_read_enabled", false);
        actions.put("markAllRead", () -> MarkAllReadFeature.markAllRead(activity));

        // ---------- 首页底栏 ----------
        flag("nav_bar_use_floating", true, NavigationBarFeature::refresh);
        values.putString("nav_bar_glass_mode",
                String.valueOf(readInt("nav_bar_glass_mode", NavigationBarFeature.flag("nav_bar_use_backdrop", true) ? 2 : 0, 0, 2)));
        writers.put("nav_bar_glass_mode", requested -> {
            int mode = clamp(requested, 0, 2);
            SettingsChanges.saveString("nav_bar_glass_mode", String.valueOf(mode));
            SettingsChanges.saveBool("nav_bar_use_backdrop", mode != 0);
            refreshGlassSurfaces();
            return String.valueOf(readInt("nav_bar_glass_mode", mode, 0, 2));
        });
        number("nav_bar_blur_radius", 8, 0, 40, SettingsPanelModel::refreshGlassSurfaces);
        number("nav_bar_scale", 100, 50, 150, NavigationBarFeature::refresh);
        flag("nav_bar_use_wechat_icons", false, NavigationBarFeature::refresh);
        flag("nav_bar_hide_labels", false, NavigationBarFeature::refresh);
        flag("nav_bar_show_finder_badge", true, NavigationBarFeature::refresh);
        flag("nav_bar_animate_page_change", true, NavigationBarFeature::refresh);
        flag("nav_bar_auto_hide_on_scroll", false, NavigationBarFeature::refresh);
        flag("nav_bar_dynamic_gravity_highlight", false, NavigationBarFeature::refresh);

        // ---------- 聊天 ----------
        flag("floating_chat_header_enabled", false, FloatingChatHeaderFeature::refresh);
        choice("floating_chat_header_layout_style", "INTEGRATED");
        number("floating_chat_header_side_margin", 12, 0, 32, FloatingChatHeaderFeature::refresh);
        number("floating_chat_header_top_gap", 4, 0, 24, FloatingChatHeaderFeature::refresh);
        number("floating_chat_header_extra_gap", 8, 0, 24, FloatingChatHeaderFeature::refresh);
        number("floating_chat_header_corner_radius", 24, 0, 32, FloatingChatHeaderFeature::refresh);
        number("floating_chat_header_elevation", 4, 0, 16, FloatingChatHeaderFeature::refresh);
        number("floating_chat_header_component_gap", 8, 0, 24, FloatingChatHeaderFeature::refresh);

        flag("chat_toolbar_enabled", false, ChatToolbarFeature::refresh);
        text("chat_toolbar_enabled_items", "");
        flag("floating_chat_footer_enabled", false, FloatingChatFooterFeature::refresh);
        number("floating_chat_footer_bottom_gap", 8, 0, 24, FloatingChatFooterFeature::refresh);
        number("floating_chat_footer_side_margin", 12, 0, 32, FloatingChatFooterFeature::refresh);
        number("floating_chat_footer_corner_radius", 24, 0, 32, FloatingChatFooterFeature::refresh);
        number("floating_chat_footer_elevation", 4, 0, 16, FloatingChatFooterFeature::refresh);
        flag("floating_chat_footer_panel_above", true, FloatingChatFooterFeature::refresh);
        number("floating_chat_footer_more_height_dp", 220, 220, 360, FloatingChatFooterFeature::refresh);

        // ---------- 气泡 ----------
        flag("bubble_enabled", false, BubbleApply::refresh);
        values.putString("bubble_mode", String.valueOf(BubbleSettings.mode()));
        writers.put("bubble_mode", requested -> {
            int mode = clamp(requested, 0, 2);
            SettingsChanges.saveString("bubble_mode", String.valueOf(mode));
            BubbleApply.refresh();
            return String.valueOf(BubbleSettings.mode());
        });
        values.putString("bubble_glass_blur_dp", String.valueOf(BubbleSettings.blurDp()));
        bubbleNumber("bubble_glass_blur_dp", 0, 32);
        values.putString("bubble_glass_tint", String.valueOf(BubbleSettings.tintAlpha()));
        bubbleNumber("bubble_glass_tint", 0, 200);
        values.putString("bubble_corner_radius_dp", String.valueOf(BubbleSettings.cornerDp()));
        bubbleNumber("bubble_corner_radius_dp", 0, 40);
        values.putString("bubble_bg_alpha", String.valueOf(BubbleSettings.imageAlpha()));
        bubbleNumber("bubble_bg_alpha", 0, 255);
        image("bubble_bg_path_peer");
        image("bubble_bg_path_self");

        // ---------- 更多 ----------
        flag("global_bg_enabled", false);
        image("global_bg_path");
        values.putString("global_bg_opacity", String.valueOf(Math.round(GlobalBackgroundFeature.opacity() * 100f)));
        writers.put("global_bg_opacity", requested -> {
            int percent = clamp(requested, GlobalBackgroundFeature.MIN_OPACITY, GlobalBackgroundFeature.MAX_OPACITY);
            SettingsChanges.saveString("global_bg_opacity", String.valueOf(percent / 100f));
            return String.valueOf(Math.round(GlobalBackgroundFeature.opacity() * 100f));
        });
        flag("global_bg_transparent_status_bar", false);

        // ---------- 全屏壁纸（首页四页蒙层，TabBgDrawHook 读取） ----------
        flag("tab_bg_enabled", false);
        image(GlobalWallpaper.KEY_PATH);
        values.putString("tab_bg_alpha", String.valueOf(tabAlphaPercent()));
        writers.put("tab_bg_alpha", requested -> {
            int percent = clamp(requested, 0, 100);
            String stored = String.valueOf(Math.round(percent * 255f / 100f));
            for (int page = 0; page < 4; page++) {
                if (!SettingsChanges.saveString(TAB_ALPHA_PREFIX + page, stored)) {
                    SettingsShell.toast(activity, "保存未确认，请重试");
                    break;
                }
            }
            TabBgDrawHook.refreshOverlays();
            return String.valueOf(tabAlphaPercent());
        });

        flag(LocationPrivacy.KEY_ENABLED, false);
        actions.put("updateLocation", () -> SettingsShell.updateLocation(activity));

        // ---------- 关于 ----------
        flag("log_enabled", true);
        actions.put("restart", () -> SettingsShell.confirmRestart(activity));
        actions.put("copyDiagnostic", this::copyDiagnostic);

        // ---------- 动态列表 ----------
        values.putString("panel_tab", String.valueOf(SettingsShell.currentTab));
        writers.put("panel_tab", requested -> {
            int tab = clamp(requested, 0, 2);
            SettingsShell.currentTab = tab;
            return String.valueOf(tab);
        });
        readers.put("tools", ignored -> String.join(",", ChatToolbarFeature.toolNames()));
        readers.put("pending", ignored -> String.valueOf(SettingsChanges.hasPending()));
        readers.put("version", ignored -> version());
        readers.put("activation", ignored -> active()
                ? "已激活 · " + lastLogTime() : "未激活 · 请在 LSPosed 中启用并重启微信");
        readers.put("logText", ignored -> {
            try {
                return LogPreview.read(new File(LOG_PATH));
            } catch (Throwable error) {
                return "读取日志失败：" + error.getClass().getSimpleName() + "\n路径：" + LOG_PATH;
            }
        });
        for (String name : ChatToolbarFeature.toolNames()) {
            actions.put("toolUp:" + name, () -> ChatToolbarFeature.moveTool(name, -1));
            actions.put("toolDown:" + name, () -> ChatToolbarFeature.moveTool(name, 1));
        }
    }

    // ---------- helpers ----------

    private static void refreshGlassSurfaces() {
        NavigationBarFeature.refresh();
        FloatingChatHeaderFeature.refresh();
        FloatingChatFooterFeature.refresh();
    }

    private void flag(String key, boolean def) { flag(key, def, null); }

    private void flag(String key, boolean def, Runnable after) {
        values.putString(key, String.valueOf(SettingsChanges.getBool(WexStore.INSTANCE, key, def)));
        writers.put(key, requested -> {
            boolean next = "true".equals(requested);
            boolean saved = SettingsChanges.saveBool(key, next);
            boolean actual = SettingsChanges.getBool(WexStore.INSTANCE, key, def);
            if (!saved && actual != next) SettingsShell.toast(activity, "保存未确认，请重试");
            if (after != null) after.run();
            return String.valueOf(actual);
        });
    }

    private void text(String key, String def) {
        values.putString(key, SettingsChanges.getString(WexStore.INSTANCE, key, def));
        writers.put(key, requested -> {
            String next = requested == null ? "" : requested;
            if (!SettingsChanges.saveString(key, next)) SettingsShell.toast(activity, "保存未确认，请重试");
            return SettingsChanges.getString(WexStore.INSTANCE, key, def);
        });
    }

    private void choice(String key, String def) { text(key, def); }

    private void image(String key) { actions.put("pick:" + key, () -> new PickCallback(activity).pick(key)); }

    private void number(String key, int def, int min, int max) { number(key, def, min, max, null); }

    private void number(String key, int def, int min, int max, Runnable after) {
        int initial = BubbleSettings.readInt(key, def, min, max);
        values.putString(key, String.valueOf(initial));
        writers.put(key, requested -> {
            int value = clamp(requested, min, max);
            boolean saved = SettingsChanges.saveString(key, String.valueOf(value));
            int actual = BubbleSettings.readInt(key, def, min, max);
            if (!saved && actual != value) SettingsShell.toast(activity, "保存未确认，请重试");
            if (after != null) after.run();
            return String.valueOf(actual);
        });
    }

    /** Bubble keys are stored straight through WexStore by their own feature; confirm by re-read. */
    private void bubbleNumber(String key, int min, int max) {
        writers.put(key, requested -> {
            int value = clamp(requested, min, max);
            try {
                WexStore.INSTANCE.putString(key, String.valueOf(value));
            } catch (Throwable error) {
                SettingsShell.toast(activity, "保存未确认，请重试");
            }
            int actual = BubbleSettings.readInt(key, value, min, max);
            try {
                BubbleApply.refresh();
            } catch (Throwable ignored) {
            }
            if (actual != value) SettingsShell.toast(activity, "保存未确认，请重试");
            return String.valueOf(actual);
        });
    }

    private static int clamp(String requested, int min, int max) {
        int value;
        try {
            value = Integer.parseInt(requested == null ? "" : requested.trim());
        } catch (NumberFormatException error) {
            value = min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static int readInt(String key, int def, int min, int max) {
        return BubbleSettings.readInt(key, def, min, max);
    }

    /** 蒙层不透明度：存的是 0–255，面板显示 0–100%。 */
    private static int tabAlphaPercent() {
        int alpha = BubbleSettings.readInt(TAB_ALPHA_KEY, TAB_ALPHA_DEFAULT, 0, 255);
        return Math.round(alpha * 100f / 255f);
    }

    /** 24 小时内写过日志即视为模块已生效（hook 已注册）。 */
    private static boolean active() {
        try {
            File file = new File(LOG_PATH);
            return file.exists() && System.currentTimeMillis() - file.lastModified() < ACTIVE_WINDOW_MS;
        } catch (Throwable error) {
            return false;
        }
    }

    /** 最近一次日志写入时间，无日志返回 null。 */
    private static String lastLogTime() {
        try {
            File file = new File(LOG_PATH);
            if (!file.exists()) {
                return null;
            }
            return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                    .format(new Date(file.lastModified()));
        } catch (Throwable error) {
            return null;
        }
    }

    private String version() {
        try {
            String name = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0).versionName;
            return name == null || name.isEmpty() ? "1.0" : name;
        } catch (Throwable error) {
            return "1.0";
        }
    }

    private void copyDiagnostic() {
        try {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) activity.getSystemService(Activity.CLIPBOARD_SERVICE);
            if (clipboard == null) throw new IllegalStateException("clipboard");
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("MindLink diagnostics", FeatureDiagnostics.summary()));
            SettingsShell.toast(activity, "已复制脱敏诊断摘要");
        } catch (Throwable error) {
            SettingsShell.toast(activity, "复制失败，请重试");
        }
    }
}
