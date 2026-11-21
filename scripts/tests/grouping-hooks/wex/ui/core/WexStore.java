package wex.ui.core;
import java.util.HashMap;
import java.util.Map;
public final class WexStore {
    public static final WexStore INSTANCE = new WexStore();
    public final Map<String, String> values = new HashMap<>();
    public final Map<String, Integer> boolReads = new HashMap<>();
    public boolean getBool(String key, boolean fallback) {
        boolReads.merge(key, 1, Integer::sum);
        return values.containsKey(key) ? Boolean.parseBoolean(values.get(key)) : fallback;
    }
    public String getString(String key, String fallback) { return values.getOrDefault(key, fallback); }
    public String getSettingChecked(String key) { return values.getOrDefault(key, ""); }
}
