package wex.ui.core.feature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Bottom-bar tab model with enable/order persistence and pager index mapping.
 * 语义对齐 WeKit `ReplaceNavigationBar.kt` 的 normalizedTabOrder / normalizedEnabledTabs。
 */
final class NavigationTabModel {

    static final class Tab {
        final int wechatIndex;
        final String label;

        Tab(int wechatIndex, String label) {
            this.wechatIndex = wechatIndex;
            this.label = label;
        }
    }

    static final int HOME = 0;
    static final int CONTACTS = 1;
    static final int DISCOVER = 2;
    static final int ME = 3;
    static final int TAB_COUNT = 4;

    private static final String[] LABELS = {"首页", "通讯录", "发现", "我"};

    private NavigationTabModel() {
    }

    static List<Tab> defaults() {
        List<Tab> tabs = new ArrayList<Tab>();
        for (int i = 0; i < TAB_COUNT; i++) {
            tabs.add(new Tab(i, LABELS[i]));
        }
        return tabs;
    }

    static List<Tab> normalize(String orderRaw, String enabledRaw) {
        List<Tab> all = defaults();
        List<Tab> ordered = new ArrayList<Tab>();
        if (orderRaw != null) {
            for (String token : orderRaw.split(",")) {
                Integer index = parse(token);
                if (index == null) {
                    continue;
                }
                for (Tab tab : all) {
                    if (tab.wechatIndex == index && !contains(ordered, index)) {
                        ordered.add(tab);
                        break;
                    }
                }
            }
        }
        for (Tab tab : all) {
            if (!contains(ordered, tab.wechatIndex)) {
                ordered.add(tab);
            }
        }

        List<Integer> enabled = new ArrayList<Integer>();
        if (enabledRaw != null) {
            for (String token : enabledRaw.split(",")) {
                Integer index = parse(token);
                if (index != null && !enabled.contains(index)) {
                    enabled.add(index);
                }
            }
        }
        if (enabled.isEmpty()) {
            for (Tab tab : all) {
                enabled.add(tab.wechatIndex);
            }
        }

        List<Tab> visible = new ArrayList<Tab>();
        for (Tab tab : ordered) {
            if (enabled.contains(tab.wechatIndex)) {
                visible.add(tab);
            }
        }
        if (visible.isEmpty()) {
            visible.add(ordered.get(0));
        }
        return Collections.unmodifiableList(visible);
    }

    /** 可见位置 -> 宿主微信索引；越界或空列表返回宿主首页。 */
    static int wechatIndexFor(List<Tab> visible, int pagerIndex) {
        if (visible.isEmpty()) {
            return HOME;
        }
        if (pagerIndex < 0 || pagerIndex >= visible.size()) {
            return visible.get(0).wechatIndex;
        }
        return visible.get(pagerIndex).wechatIndex;
    }

    /** 宿主微信索引 -> 可见 pager 位置；隐藏页回落到当前可见首页。 */
    static int pagerIndexFor(List<Tab> visible, int wechatIndex) {
        for (int i = 0; i < visible.size(); i++) {
            if (visible.get(i).wechatIndex == wechatIndex) {
                return i;
            }
        }
        return visible.isEmpty() ? 0 : 0;
    }

    /** 宿主可能传入禁用页索引（如从通知跳转）：映射到最近的可见页。 */
    static int clampToVisible(List<Tab> visible, int wechatIndex) {
        for (int i = 0; i < visible.size(); i++) {
            if (visible.get(i).wechatIndex == wechatIndex) {
                return wechatIndex;
            }
        }
        return visible.isEmpty() ? HOME : visible.get(0).wechatIndex;
    }

    static String encodeOrder(List<Tab> visible) {
        StringBuilder raw = new StringBuilder();
        List<Integer> seen = new ArrayList<Integer>();
        for (Tab tab : visible) {
            if (seen.contains(tab.wechatIndex)) {
                continue;
            }
            seen.add(tab.wechatIndex);
            if (raw.length() > 0) {
                raw.append(',');
            }
            raw.append(tab.wechatIndex);
        }
        // 未启用的标签按默认顺序补在尾部，保证下次启用时位置可预期。
        for (int i = 0; i < TAB_COUNT; i++) {
            if (seen.contains(i)) {
                continue;
            }
            seen.add(i);
            if (raw.length() > 0) {
                raw.append(',');
            }
            raw.append(i);
        }
        return raw.toString();
    }

    static String encodeEnabled(List<Tab> visible) {
        StringBuilder raw = new StringBuilder();
        for (Tab tab : visible) {
            if (raw.length() > 0) {
                raw.append(',');
            }
            raw.append(tab.wechatIndex);
        }
        return raw.toString();
    }

    static boolean isHome(Tab tab) {
        return tab != null && tab.wechatIndex == HOME;
    }

    static boolean isDiscover(Tab tab) {
        return tab != null && tab.wechatIndex == DISCOVER;
    }

    static String labelFor(int wechatIndex) {
        if (wechatIndex < 0 || wechatIndex >= LABELS.length) {
            return "";
        }
        return LABELS[wechatIndex];
    }

    static String describe(List<Tab> visible) {
        StringBuilder out = new StringBuilder();
        for (Tab tab : visible) {
            if (out.length() > 0) {
                out.append('>');
            }
            out.append(tab.wechatIndex);
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }

    private static boolean contains(List<Tab> tabs, int wechatIndex) {
        for (Tab tab : tabs) {
            if (tab.wechatIndex == wechatIndex) {
                return true;
            }
        }
        return false;
    }

    private static Integer parse(String token) {
        if (token == null) {
            return null;
        }
        try {
            return Integer.parseInt(token.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
