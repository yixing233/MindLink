package wex.ui.core.feature;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ownership/schema test double only: deliberately does not pretend to render original Compose UI. */
public final class SidePanelComposeBridge {
    public static int builds, toolbarBuilds, updates, saves, disposals;
    public static View lastView;
    public static Snapshot lastSnapshot;
    public static Callbacks lastCallbacks;
    public static final Map<View, Boolean> active = new java.util.IdentityHashMap<>();
    private static final Map<View, Callbacks> callbacksByView = new java.util.IdentityHashMap<>();

    public static final class Snapshot {
        public final Bundle values;
        public final Map<String, Bitmap> images;
        Snapshot(Bundle values, Map<String, Bitmap> images) {
            validate(values);
            Bundle status = values.getBundle("profile").getBundle("status");
            String iconKey = status.getString("iconKey");
            if (iconKey != null && (!images.containsKey(iconKey) || images.get(iconKey).isRecycled()))
                throw new AssertionError("status iconKey must reference a live actual Bitmap");
            this.values = values;
            this.images = new LinkedHashMap<>(images);
        }
    }
    public static final class Callbacks {
        public final Map<String, Runnable> actions;
        Callbacks(Map<String, Runnable> actions) { this.actions = new LinkedHashMap<>(actions); }
    }
    private static final class Input implements Runnable, java.util.function.Consumer<Bundle> {
        private final java.util.function.Consumer<Bundle> consumer;
        Input(java.util.function.Consumer<Bundle> consumer) { this.consumer = consumer; }
        @Override public void run() { }
        @Override public void accept(Bundle value) { consumer.accept(value); }
    }
    static Runnable input(java.util.function.Consumer<Bundle> consumer) { return new Input(consumer); }
    public static View build(Activity activity, Bundle saved, Snapshot snapshot, Callbacks callbacks) {
        builds++;
        return created(activity, snapshot, callbacks);
    }
    public static View buildToolbar(Activity activity, Snapshot snapshot, Callbacks callbacks) {
        toolbarBuilds++;
        return created(activity, snapshot, callbacks);
    }
    private static View created(Activity activity, Snapshot snapshot, Callbacks callbacks) {
        lastView = new View(activity);
        lastSnapshot = snapshot;
        lastCallbacks = callbacks;
        active.put(lastView, false);
        callbacksByView.put(lastView, callbacks);
        return lastView;
    }
    public static void update(View view, Snapshot snapshot) {
        updates++; lastSnapshot = snapshot;
    }
    public static void update(View view, Snapshot snapshot, Callbacks callbacks) {
        update(view, snapshot); lastCallbacks = callbacks; callbacksByView.put(view, callbacks);
    }
    public static void save(View view, Bundle state) { saves++; state.putInt("scroll", 27); }
    public static void lifecycle(View view, boolean resumed) { active.put(view, resumed); }
    public static void dispose(View view) { disposals++; active.remove(view); callbacksByView.remove(view); }
    /** Mirrors HomeSidePanelState.call's lifecycle gate, not a rendered Compose UI. */
    public static boolean dispatch(View view, String key) {
        Callbacks callbacks = callbacksByView.get(view);
        if (!Boolean.TRUE.equals(active.get(view)) || callbacks == null) return false;
        Runnable action = callbacks.actions.get(key);
        if (action == null) return false;
        action.run();
        return true;
    }

    private static <T> T required(Bundle bundle, String key, Class<T> type) {
        Object value = bundle.get(key);
        if (!type.isInstance(value)) throw new AssertionError("Incomplete test snapshot: " + key + " / " + type.getSimpleName());
        return type.cast(value);
    }
    private static void validate(Bundle root) {
        if (required(root, "schemaVersion", Integer.class) != 1) throw new AssertionError("schemaVersion");
        required(root, "initialized", Boolean.class); required(root, "editing", Boolean.class);
        Bundle profile = required(root, "profile", Bundle.class);
        required(profile, "wxId", String.class); required(profile, "nickname", String.class);
        Bundle status = required(profile, "status", Bundle.class);
        String kind = required(status, "kind", String.class);
        if (!java.util.Set.of("loading", "no_status", "ready", "error").contains(kind)) throw new AssertionError("status kind");
        if (kind.equals("ready")) { required(status, "iconId", String.class); required(status, "description", String.class); }
        for (Object item : required(root, "cards", ArrayList.class)) {
            if (!(item instanceof Bundle)) throw new AssertionError("Bundle cards");
            Bundle card = (Bundle) item;
            required(card, "id", String.class);
            switch (required(card, "cardType", String.class)) {
                case "date_time": required(card, "showLunarCalendar", Boolean.class); break;
                case "weather":
                    required(card, "currentLocation", Boolean.class);
                    if (card.containsKey("city")) required(card, "city", Bundle.class);
                    validateState(required(card, "state", Bundle.class), true);
                    break;
                case "hitokoto":
                    Bundle settings = required(card, "settings", Bundle.class);
                    required(settings, "categories", String.class);
                    required(settings, "showSource", Boolean.class); required(settings, "showAuthor", Boolean.class);
                    validateState(required(card, "state", Bundle.class), false); break;
                case "wallet":
                    required(card, "hideBalanceByDefault", Boolean.class);
                    Bundle wallet = required(card, "state", Bundle.class);
                    required(wallet, "defaultMaskEnabled", Boolean.class); required(wallet, "isMasked", Boolean.class);
                    if (wallet.containsKey("balanceFen")) required(wallet, "balanceFen", Long.class);
                    break;
                case "image":
                    int height = required(card, "heightDp", Integer.class);
                    if (height < 80 || height > 800 || height % 8 != 0) throw new AssertionError("image height");
                    required(card, "scaleMode", String.class);
                    required(card, "importing", Boolean.class);
                    break;
                case "horizontal_actions": case "vertical_actions":
                    for (Object value : required(card, "actions", ArrayList.class)) {
                        Bundle action = (Bundle) value;
                        required(action, "id", String.class); required(action, "kind", String.class);
                    }
                    break;
                default: throw new AssertionError("unknown card type");
            }
        }
    }
    private static void validateState(Bundle state, boolean weather) {
        String kind = required(state, "kind", String.class);
        required(state, "refreshing", Boolean.class);
        if (!java.util.Set.of("loading", "ready", "error").contains(kind)) throw new AssertionError("data kind");
        if (kind.equals("error")) required(state, "message", String.class);
        Bundle snapshot = kind.equals("ready") ? required(state, "snapshot", Bundle.class) : state.getBundle("cached");
        if (snapshot == null) return;
        required(snapshot, "fetchedAt", Long.class);
        if (weather) {
            Bundle city = required(snapshot, "city", Bundle.class);
            for (String key : new String[]{"countryCode", "province", "city", "district", "cityNum"}) required(city, key, String.class);
            for (String key : new String[]{"weatherCode", "temperature", "feelsLike", "high", "low", "humidity", "windSpeed", "publishedAt"})
                required(snapshot, key, String.class);
        } else { required(snapshot, "uuid", String.class); required(snapshot, "text", String.class); }
    }
}
