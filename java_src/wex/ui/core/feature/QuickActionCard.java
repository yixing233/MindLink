package wex.ui.core.feature;

import wex.ui.core.WexStore;

/**
 * 「一键已读」开关的读取入口。
 *
 * 卡片本体已随 Material3 设置面板下线（开关由 SettingsPanelModel 的
 * mark_all_read_enabled + markAllRead 动作提供），这里只保留 + 面板
 * （PlusMenuExtra）要读的那一个状态位。
 */
public final class QuickActionCard {

    private static final String KEY_ENABLED = "mark_all_read_enabled";

    private QuickActionCard() {
    }

    public static boolean isEnabled() {
        try {
            return SettingsChanges.getBool(WexStore.INSTANCE, KEY_ENABLED, false);
        } catch (Throwable t) {
            return false;
        }
    }
}
