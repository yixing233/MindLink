package wex.ui.core.feature;

import java.nio.file.Files;
import java.nio.file.Path;

import wex.ui.core.WexStore;

public final class SettingsChangesChecks {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Listener implements SettingsChanges.Listener {
        int updates;
        int failures;

        public void onChanged() { updates++; }
        public void onFailure(String message) { failures++; }
    }

    private static void stateChecks() {
        SettingsChangeState state = new SettingsChangeState();
        check(!state.hasPending(), "new process begins without pending changes");
        check(!state.record("one", "false", "false"), "unchanged setting stays clean");
        check(state.record("one", "false", "true"), "first change becomes pending");
        check(state.hasPending(), "pending visible");
        check(!state.record("one", "true", "true"), "repeated value does not notify again");
        state.record("two", "old", "new");
        state.record("one", "true", "false");
        check(state.hasPending(), "restoring one setting preserves other pending changes");
        state.record("two", "new", "old");
        check(!state.hasPending(), "restoring all original values clears pending");
    }

    private static void saves() {
        WexStore store = WexStore.INSTANCE;
        store.reset();
        Listener first = new Listener();
        SettingsChanges.subscribe(first);
        check(first.updates == 1 && !SettingsChanges.hasPending(), "new panel gets current clean state");
        check(!SettingsChanges.getBool(store, "test_switch", false), "register actual boolean default");
        check(SettingsChanges.saveBool("test_switch", false), "saving default is confirmed");
        check(!SettingsChanges.hasPending(), "explicit default does not count as change");
        int writes = store.writes;
        check(SettingsChanges.saveBool("test_switch", false) && store.writes == writes, "repeat avoids redundant storage write");
        check(SettingsChanges.saveBool("test_switch", true), "changed switch saves");
        check(SettingsChanges.hasPending(), "confirmed changed switch becomes pending");
        SettingsChanges.unsubscribe(first);
        Listener reopened = new Listener();
        SettingsChanges.subscribe(reopened);
        check(reopened.updates == 1 && SettingsChanges.hasPending(), "reopening panel preserves pending state");
        SettingsChanges.unsubscribe(reopened);
        SettingsChanges.subscribe(reopened);
        check(SettingsChanges.hasPending(), "closing or cancelling restart does not clear state");
        check(SettingsChanges.saveBool("test_switch", false), "restoring baseline saves");
        check(!SettingsChanges.hasPending(), "restoring baseline hides banner");
        check(first.updates == 2, "detached listener receives no later notifications");

        SettingsChanges.getString(store, "test_style", "1");
        check(SettingsChanges.saveString("test_style", "1") && !SettingsChanges.hasPending(), "string default is not a new change");
        SettingsChanges.saveString("test_style", "2");
        SettingsChanges.saveBool("test_switch", true);
        SettingsChanges.saveString("test_style", "1");
        check(SettingsChanges.hasPending(), "second pending key remains");
        SettingsChanges.saveBool("test_switch", false);
        check(!SettingsChanges.hasPending(), "multiple changes restore independently");

        SettingsChanges.getBool(store, "failed_switch", false);
        int failures = reopened.failures;
        store.failWrites = true;
        check(!SettingsChanges.saveBool("failed_switch", true), "silent failed write is detected");
        check(!SettingsChanges.hasPending() && reopened.failures == failures + 1, "failed write only shows failure");
        store.failWrites = false;
        store.failReads = true;
        writes = store.writes;
        check(!SettingsChanges.saveBool("failed_switch", true) && store.writes == writes, "failed initial read does not write");
        store.failReads = false;
        store.failReadAfterWrite = true;
        check(!SettingsChanges.saveString("readback_failure", "value"), "readback failure is not success");
        check(!SettingsChanges.hasPending(), "unconfirmed save does not report pending");
        store.failReads = false;
        store.failReadAfterWrite = false;
        store.nullStrings = true;
        check(!SettingsChanges.saveString("null_read", ""), "unavailable storage cannot masquerade as default");
        store.nullStrings = false;

        for (String key : new String[]{"bubble_mode", "bubble_enabled", "bubble_glass_blur_dp", "top_nickname",
                "top_profile_enabled", "top_moments_cards", "top_moments_progressive_blur",
                "chat_toolbar_enabled", "chat_toolbar_reply", "log_enabled",
                "location_network_enabled", "tab_bg_alpha_0", "tab_bg_alpha_3", "nav_bar_glass_mode",
                "nav_bar_blur_radius", "nav_bar_scale", "nav_bar_use_floating", "floating_chat_header_top_gap",
                "floating_chat_header_layout_style", "floating_chat_footer_bottom_gap", "floating_chat_footer_panel_above"}) {
            check(SettingsChanges.saveString(key, "changed"), "immediate setting saves");
            check(!SettingsChanges.hasPending(), "immediate settings do not demand restart: " + key);
        }
        check(SettingsChanges.needsRestart("chat_top_style"), "chat top style is not homepage top profile");
        check(SettingsChanges.needsRestart("tab_bg_enabled"), "wallpaper toggle retains restart requirement");
        check(SettingsChanges.needsRestart("tab_bg_path_0"), "wallpaper image is not alpha-only refresh");
        check(SettingsChanges.needsRestart("tab_bg_alpha_10"), "unknown alpha key does not accidentally match range");
        SettingsChanges.unsubscribe(reopened);
    }

    private static void images(Path directory) throws Exception {
        WexStore store = WexStore.INSTANCE;
        Path image = directory.resolve("setting-image.bin");
        byte[] original = {1, 2, 3, 4};
        Files.write(image, original);
        store.putString("test_image", image.toString());
        String before = SettingsChanges.imageBefore("test_image");
        check(!SettingsChanges.hasPending(), "opening or cancelling image picker does not mark change");
        check(SettingsChanges.saveImage("test_image", image.toString(), before), "identical image saves");
        check(!SettingsChanges.hasPending(), "same content and path are unchanged");
        Files.write(image, new byte[]{9, 8, 7, 6});
        check(SettingsChanges.saveImage("test_image", image.toString(), before), "same-path replacement saves");
        check(SettingsChanges.hasPending(), "same path with different pixels becomes pending");
        before = SettingsChanges.imageBefore("test_image");
        Files.write(image, original);
        check(SettingsChanges.saveImage("test_image", image.toString(), before), "restore image content");
        check(!SettingsChanges.hasPending(), "restoring content clears pending image");
        check(!SettingsChanges.saveImage("missing_image", directory.resolve("missing").toString(), "missing"), "failed image does not report success");
        check(!SettingsChanges.hasPending(), "failed image leaves pending unchanged");
        before = SettingsChanges.imageBefore("new_image");
        check(SettingsChanges.saveImage("new_image", image.toString(), before), "new image saves");
        check(SettingsChanges.hasPending(), "new image becomes pending");
        check(SettingsChanges.saveString("new_image", ""), "reset image to original default");
        check(!SettingsChanges.hasPending(), "image reset clears both path and content changes");
        before = SettingsChanges.imageBefore("bubble_bg_path");
        check(SettingsChanges.saveImage("bubble_bg_path", image.toString(), before), "immediate bubble image save");
        check(!SettingsChanges.hasPending(), "bubble picture keeps immediate apply behavior");
    }

    public static void main(String[] args) throws Exception {
        stateChecks();
        saves();
        images(Path.of(args[0]));
        System.out.println("PASS: settings confirmed saves, defaults, failure gates, restore, panel rebind, immediate modes and same-path images");
    }
}
