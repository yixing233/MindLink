package wex.ui.core.feature;
import java.util.HashMap;
import java.util.Map;
/** Controllable setting store: the card material must read the bottom-bar keys. */
public final class BubbleSettings {
    public static final Map<String, Integer> values = new HashMap<>();
    public static int readInt(String key, int fallback, int min, int max) {
        Integer value = values.get(key);
        return value == null ? fallback : Math.max(min, Math.min(max, value));
    }
}
