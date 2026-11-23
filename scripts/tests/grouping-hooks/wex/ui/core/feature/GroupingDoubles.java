package wex.ui.core.feature;

import java.lang.reflect.Method;
import java.util.*;
import android.app.Activity;
import android.widget.ListView;

final class HostTargetResolver {
    static Method resolved;
    static Method footerResolved, barResolved;
    static java.lang.reflect.Field barField;
    static final class MethodSpec {
        MethodSpec classStrings(String... values) { return this; }
        MethodSpec name(String value) { return this; }
        MethodSpec returnType(String value) { return this; }
        MethodSpec parameterTypes(String... values) { return this; }
        MethodSpec declaredClass(String value) { return this; }
        MethodSpec parameterCount(int value) { return this; }
        MethodSpec usingExactStrings(String... values) { return this; }
    }
    interface MethodCallback { void onResolved(Method method); void onFailure(Throwable error); }
    static void resolveMethod(String key, MethodSpec spec, MethodCallback callback) {
        Method target = key.equals("conversation_empty_footer_v1") ? footerResolved
                : key.equals("conversation_action_bar_reader_v1") ? barResolved : resolved;
        if (target == null) callback.onFailure(new NoSuchMethodException("dummy unmapped"));
        else callback.onResolved(target);
    }
    static java.lang.reflect.Field fieldReadBy(Method reader, Class<?> type) { return barField; }
    static final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    static boolean defer;
    static void background(Runnable task) { if (defer) pending.add(task); else task.run(); }
    static void completeForTest() { pending.removeFirst().run(); }
}
final class HostDatabaseBridge {
    static List<String> result = Collections.emptyList();
    static boolean fail;
    static List<String> queryStrings(String sql, Object args) {
        if (fail) throw new IllegalStateException("dummy unavailable");
        return result;
    }
}
final class SettingsChanges {
    static boolean failSave;
    static boolean saveString(String key, String value) {
        if (failSave) return false;
        wex.ui.core.WexStore.INSTANCE.values.put(key, value); return true;
    }
}
final class ConversationGroupStore {
    static final String ALL_ID = "mdl_group_all";
    enum Type { MANUAL, PRESET_UNREAD, PRESET_GROUPS, PRESET_FRIENDS, PRESET_OFFICIALS, SQL }
    static List<Group> decoded;
    static final class Group {
        final String id, selectFields, whereClause;
        final Type type;
        final List<String> members;
        Group(String id, Type type, String... members) { this(id, type, Arrays.asList(members), "", ""); }
        Group(String id, Type type, List<String> members, String selectFields, String whereClause) {
            this.id = id; this.type = type; this.members = members;
            this.selectFields = selectFields; this.whereClause = whereClause;
        }
        String label() { return id; }
        boolean isAll() { return ALL_ID.equals(id); }
    }
    static List<Group> defaults() {
        return Arrays.asList(new Group(ALL_ID, Type.MANUAL), new Group("unread", Type.PRESET_UNREAD),
                new Group("groups", Type.PRESET_GROUPS), new Group("friends", Type.PRESET_FRIENDS),
                new Group("officials", Type.PRESET_OFFICIALS), new Group("manual", Type.MANUAL, "dummy-b"));
    }
    static List<Group> decode(String raw) { return decoded == null ? defaults() : decoded; }
    static String encode(List<Group> values) { return "dummy-config"; }
}
final class ChatToolbarLifecycle {
    static Activity findActivity(android.content.Context context) { return context instanceof Activity ? (Activity) context : null; }
}
final class ConversationGroupEditor {
    static void edit(Activity activity, ConversationGroupStore.Group group) { }
}
/** The real card dress needs FloatingHeaderStyle/ChatGlassSurface; the hooks suite only needs the tabs in the host. */
final class ConversationGroupCard {
    static final int TAB_HEIGHT_DP = 36;
    static int applies, styled, animated, reveals;
    static android.view.View wrap(android.widget.FrameLayout host, android.view.View tabs) {
        host.addView(tabs, new android.widget.FrameLayout.LayoutParams(-1, -2)); applies++; return tabs;
    }
    static void apply(android.view.View card) { applies++; }
    static void release(android.view.View card) { }
    static int glassRefreshes; static void refreshGlass(android.view.View card) { glassRefreshes++; }
    static void styleTabs(android.widget.LinearLayout tabs) { }
    static void tabMargins(android.view.ViewGroup.LayoutParams params, android.content.Context context) { }
    static void styleTab(android.widget.TextView label, boolean selected, boolean animate) { styled++; if (animate) animated++; }
    static void styleAdd(android.widget.TextView add) { }
    static void reveal(android.widget.HorizontalScrollView bar, android.view.View tab) { reveals++; }
}
final class HostStageLog {
    static final List<String> events = new ArrayList<>();
    static boolean fail;
    static void write(String component, String stage) {
        if (fail) throw new IllegalStateException("dummy log unavailable");
        events.add(component + " " + stage);
    }
}
final class SettingsTheme {
    static boolean dark;
    static boolean isDark(android.content.Context context) { return dark; }
    static int accent(android.content.Context context) { return dark ? 0xFF22AA22 : 0xFF00AA00; }
    static int ink(android.content.Context context) { return dark ? 0xFFEEEEEE : 0xFF111111; }
}
