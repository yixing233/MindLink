package wex.ui.core.feature;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 只记录当前进程内已确认保存、尚需重新加载的设置。 */
final class SettingsChangeState {
    private final Map<String, String> baseline = new HashMap<String, String>();
    private final Set<String> pending = new HashSet<String>();

    void remember(String key, String before) {
        if (!baseline.containsKey(key)) {
            baseline.put(key, before);
        }
    }

    boolean record(String key, String before, String after) {
        remember(key, before);
        boolean wasPending = pending.contains(key);
        if (Objects.equals(baseline.get(key), after)) {
            pending.remove(key);
        } else {
            pending.add(key);
        }
        return wasPending != pending.contains(key);
    }

    boolean hasPending() {
        return !pending.isEmpty();
    }
}
