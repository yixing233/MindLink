package wex.ui.core.feature;

import java.util.ArrayList;
import java.util.List;

/** 展开只由用户点击触发，页面离开及过期回调不能重新打开。 */
final class HomeSidebarState {
    private boolean available;
    private boolean open;
    private long generation;

    void available(boolean value) {
        available = value;
        if (!value) close();
    }

    long open() {
        if (!available || open) return -1L;
        open = true;
        return ++generation;
    }

    void close() {
        open = false;
        generation++;
    }

    boolean accepts(long token) {
        return available && open && generation == token;
    }

    boolean isOpen() {
        return open;
    }

    static List<String> order(String raw, boolean calendar, boolean image, boolean music) {
        List<String> result = new ArrayList<String>();
        String value = raw == null ? "" : raw;
        for (String kind : (value + ",calendar,image,music").split(",")) {
            kind = kind.trim();
            boolean enabled = "calendar".equals(kind) ? calendar
                    : "image".equals(kind) ? image : "music".equals(kind) && music;
            if (enabled && !result.contains(kind)) result.add(kind);
        }
        return result;
    }
}
