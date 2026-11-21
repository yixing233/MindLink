package wex.ui.core;

import java.util.HashMap;
import java.util.Map;

public final class WexStore {
    public static final WexStore INSTANCE = new WexStore();
    public boolean enabled;
    public boolean fail;
    public boolean failReads;
    public boolean nullStrings;
    public boolean failWrites;
    public boolean failReadAfterWrite;
    public int writes;
    private final Map<String, String> values = new HashMap<String, String>();

    public boolean getBool(String key, boolean defaultValue) {
        if (fail || failReads) throw new IllegalStateException("test read failure");
        if ("location_network_enabled".equals(key)) return enabled;
        String value = values.get(key);
        return value == null ? defaultValue : "true".equals(value);
    }

    public String getString(String key, String defaultValue) {
        if (fail || failReads) throw new IllegalStateException("test read failure");
        if (nullStrings) return null;
        String value = values.get(key);
        return value == null ? defaultValue : value;
    }

    public String getSettingChecked(String key) {
        if (fail || failReads || nullStrings) throw new IllegalStateException("test checked read failure");
        return values.get(key);
    }

    public void putString(String key, String value) {
        writes++;
        if (fail || failWrites) return;
        values.put(key, value);
        if (failReadAfterWrite) failReads = true;
    }

    public void putBool(String key, boolean value) {
        putString(key, String.valueOf(value));
        if (!fail && !failWrites && "location_network_enabled".equals(key)) enabled = value;
    }

    public void reset() {
        values.clear();
        enabled = false;
        fail = false;
        failReads = false;
        nullStrings = false;
        failWrites = false;
        failReadAfterWrite = false;
        writes = 0;
    }
}
