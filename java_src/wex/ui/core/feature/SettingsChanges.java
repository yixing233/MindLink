package wex.ui.core.feature;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import wex.ui.core.WexStore;

/** 设置保存与面板提示共用桥；不监听运行期缓存、播放状态等写入。 */
public final class SettingsChanges {
    public interface Listener {
        void onChanged();
        void onFailure(String message);
    }

    private static final Object LOCK = new Object();
    private static final SettingsChangeState STATE = new SettingsChangeState();
    private static final Map<String, DefaultValue> DEFAULTS = new HashMap<String, DefaultValue>();
    private static final Set<String> IMAGES = new HashSet<String>();
    private static final Set<String> FAILED = new HashSet<String>();
    private static final List<WeakReference<Listener>> LISTENERS = new ArrayList<WeakReference<Listener>>();

    private SettingsChanges() {
    }

    private static final class DefaultValue {
        final String value;
        final boolean bool;

        DefaultValue(String value, boolean bool) {
            this.value = value;
            this.bool = bool;
        }
    }

    public static String getString(WexStore store, String key, String def) {
        rememberDefault(key, def, false);
        return store.getString(key, def);
    }

    public static boolean getBool(WexStore store, String key, boolean def) {
        rememberDefault(key, String.valueOf(def), true);
        return store.getBool(key, def);
    }

    private static void rememberDefault(String key, String value, boolean bool) {
        synchronized (LOCK) {
            if (!DEFAULTS.containsKey(key)) {
                DEFAULTS.put(key, new DefaultValue(value, bool));
            }
        }
    }

    private static String effective(String key, String value) {
        DefaultValue def = DEFAULTS.get(key);
        if (def == null) {
            return value;
        }
        if (value == null || (def.bool && !"true".equals(value) && !"false".equals(value))) {
            return def.value;
        }
        return value;
    }

    static boolean needsRestart(String key) {
        return !key.startsWith("bubble_") && !key.startsWith("top_")
                && !key.startsWith("floating_chat_footer_") && !key.startsWith("floating_chat_header_")
                && !key.startsWith("nav_bar_")
                && !"chat_toolbar_enabled".equals(key) && !"chat_toolbar_reply".equals(key)
                && !"log_enabled".equals(key) && !"location_network_enabled".equals(key)
                && !(key.startsWith("tab_bg_alpha_") && key.length() == 14
                && key.charAt(13) >= '0' && key.charAt(13) <= '3');
    }

    /** Smali 的 receiver/key/value 参数保持不变，便于替换原存储调用。 */
    public static void putString(WexStore store, String key, String value) {
        save(store, key, value, false);
    }

    public static void putBool(WexStore store, String key, boolean value) {
        save(store, key, String.valueOf(value), true);
    }

    public static boolean saveString(String key, String value) {
        return save(WexStore.INSTANCE, key, value, false);
    }

    public static boolean saveBool(String key, boolean value) {
        return save(WexStore.INSTANCE, key, String.valueOf(value), true);
    }

    private static boolean save(WexStore store, String key, String value, boolean bool) {
        try {
            if (saveChecked(store, key, value, bool)) {
                publish(null);
            }
            synchronized (LOCK) {
                FAILED.remove(key);
            }
            return true;
        } catch (Throwable t) {
            boolean notify;
            synchronized (LOCK) {
                notify = FAILED.add(key);
            }
            if (notify) {
                publish("保存未确认，请重试");
            }
            return false;
        }
    }

    private static boolean saveChecked(WexStore store, String key, String value, boolean bool) {
        if (store == null || key == null || value == null) {
            throw new IllegalArgumentException("Missing setting value");
        }
        synchronized (LOCK) {
            String before = store.getSettingChecked(key);
            String imageBefore = null;
            boolean imageReset = value.length() == 0 && IMAGES.contains(key) && needsRestart(key);
            if (imageReset) {
                try {
                    imageBefore = fingerprint(before);
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot read previous image", e);
                }
            }
            if (needsRestart(key)) {
                STATE.remember(key, effective(key, before));
            }
            if (!Objects.equals(before, value)) {
                if (bool) {
                    store.putBool(key, Boolean.parseBoolean(value));
                } else {
                    store.putString(key, value);
                }
            }
            String actual = store.getSettingChecked(key);
            if (!value.equals(actual)) {
                throw new IllegalStateException("Setting write not confirmed");
            }
            boolean changed = needsRestart(key) && STATE.record(key, effective(key, before), effective(key, actual));
            if (imageReset) {
                changed |= STATE.record(key + "#image", imageBefore, "missing");
            }
            return changed;
        }
    }

    /** 在覆盖固定图片路径之前记下内容，不因同路径换图而漏报。 */
    public static String imageBefore(String key) {
        rememberDefault(key, "", false);
        try {
            String value = fingerprint(WexStore.INSTANCE.getSettingChecked(key));
            synchronized (LOCK) {
                IMAGES.add(key);
                if (needsRestart(key)) {
                    STATE.remember(key + "#image", value);
                }
            }
            return value;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read previous image", e);
        }
    }

    /** 图片失败提示由 ImagePicker 保留；失败不投递成功回调。 */
    public static boolean saveImage(String key, String path, String before) {
        try {
            String after = fingerprint(path);
            if ("missing".equals(after) || before == null) {
                return false;
            }
            boolean changed = saveChecked(WexStore.INSTANCE, key, path, false);
            synchronized (LOCK) {
                if (needsRestart(key)) {
                    changed |= STATE.record(key + "#image", before, after);
                }
            }
            if (changed) {
                publish(null);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String fingerprint(String path) throws Exception {
        if (path == null || path.length() == 0) {
            return "missing";
        }
        File file = new File(path);
        if (!file.exists()) {
            return "missing";
        }
        if (!file.isFile()) {
            throw new IOException("Image is not a file");
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[16384];
        try (FileInputStream stream = new FileInputStream(file)) {
            int count;
            while ((count = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) {
            result.append(Character.forDigit((value >>> 4) & 15, 16));
            result.append(Character.forDigit(value & 15, 16));
        }
        return result.toString();
    }

    public static boolean hasPending() {
        synchronized (LOCK) {
            return STATE.hasPending();
        }
    }

    public static void subscribe(Listener listener) {
        synchronized (LOCK) {
            unsubscribe(listener);
            LISTENERS.add(new WeakReference<Listener>(listener));
        }
        listener.onChanged();
    }

    public static void unsubscribe(Listener listener) {
        synchronized (LOCK) {
            for (int i = LISTENERS.size() - 1; i >= 0; i--) {
                Listener current = LISTENERS.get(i).get();
                if (current == null || current == listener) {
                    LISTENERS.remove(i);
                }
            }
        }
    }

    private static void publish(String error) {
        List<Listener> listeners = new ArrayList<Listener>();
        synchronized (LOCK) {
            for (int i = LISTENERS.size() - 1; i >= 0; i--) {
                Listener listener = LISTENERS.get(i).get();
                if (listener == null) {
                    LISTENERS.remove(i);
                } else {
                    listeners.add(listener);
                }
            }
        }
        for (Listener listener : listeners) {
            try {
                if (error == null) {
                    listener.onChanged();
                } else {
                    listener.onFailure(error);
                }
            } catch (Throwable ignore) {
            }
        }
    }
}
