package wex.ui.core.feature;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Independent WeKit side-panel entry. Only platform types cross its isolated Compose runtime.
 *
 * Snapshot schema v1 (all names are case-sensitive):
 * root required: schemaVersion=1, initialized:boolean, editing:boolean, profile:Bundle,
 * cards:ArrayList<Bundle> in persisted order. No account or service readiness is required to render.
 * profile: wxId:string (may be empty), nickname:string (may be empty), avatarKey?:string,
 * status:Bundle {kind:loading|no_status|ready|error, iconId?:string, description?:string, iconKey?:string}.
 * ready status requires iconId and description; image keys reference Snapshot.images, not URLs.
 * each card requires id:string, cardType:string. The original card IDs/action IDs are never remapped.
 * date_time: showLunarCalendar:boolean.
 * weather: city?:Bundle, state:Bundle {kind:loading|ready|error, refreshing?:boolean,
 * message?:string, snapshot?:Bundle, cached?:Bundle}. ready requires snapshot; error requires message.
 * WeatherSnapshot requires city:Bundle {countryCode,province,city,cityNum:string, district?:string,
 * latitude?:double,longitude?:double}, weatherCode,temperature,feelsLike,high,low,humidity,
 * windSpeed,publishedAt:string and fetchedAt:long. Never turn partial/display-only data into Ready.
 * wallet: hideBalanceByDefault:boolean, state:Bundle {balanceFen?:long, defaultMaskEnabled:boolean,
 * isMasked:boolean}. Unknown balance is omitted (the original component displays its original state).
 * hitokoto: settings:Bundle {categories?:string,minLength?:int,maxLength?:int,showSource:boolean,
 * showAuthor:boolean}, state:Bundle {kind,refreshing?,message?,snapshot?,cached?}.
 * HitokotoSnapshot requires uuid,text:string,fetchedAt:long; type,source,author,creator,createdAt
 * are optional nullable strings retained from the original full model.
 * image: imageAssetId?:string,imageKey?:string,heightDp:int (80..800, step 8),
 * scaleMode?:crop|fit|fill_bounds|auto_ratio,imageWidthPx?:int,imageHeightPx?:int,importing?:boolean.
 * horizontal_actions / vertical_actions: actions:ArrayList<Bundle> {id:string,kind:string enum name}.
 *
 * Callback keys (Runnable, executed on the UI thread, absent means unavailable):
 * open_profile,open_status,refresh_status,open_settings,open_panel,close_panel,enter_edit,
 * save_edit,discard_edit,add_card,scan,payment_code,edit:<cardId>,delete:<cardId>,
 * refresh_weather:<cardId>,toggle_wallet:<cardId>,refresh_hitokoto:<cardId>,select_image:<cardId>,
 * run:<cardId>:<actionId>,delete_action:<cardId>:<actionId>,add_action:<cardId>,
 * image_scale:<cardId>:crop|fit|fill_bounds|auto_ratio,image_height:<cardId>:<heightDp>.
 * Image resize callbacks are pre-bound to the finite 80..800 step-8 values; no Kotlin lambda crosses.
 * A card long-press calls edit:<cardId>, falling back to enter_edit; a click never edits implicitly.
 * payment_code is only a callback to the existing native host navigation, not a payment operation.
 */
public final class SidePanelComposeBridge {
    private static final String ENTRY = "dev.mindlink.compose.SidePanelUi";
    private static final String PREFIX = "sidepanel-compose-payload";
    private static final String TAG = "mdl_isolated_side_panel";
    private static Class<?> entry;

    private SidePanelComposeBridge() { }

    /** Platform-only snapshot, separate from any legacy host/AndroidX/Kotlin model. */
    public static final class Snapshot {
        public final Bundle values;
        public final Map<String, Bitmap> images;

        public Snapshot(Bundle values, Map<String, Bitmap> images) {
            this.values = copy(Objects.requireNonNull(values, "values"));
            require(this.values, "schemaVersion", Integer.class);
            if (this.values.getInt("schemaVersion") != 1) throw new IllegalArgumentException("Unsupported side-panel schema");
            require(this.values, "initialized", Boolean.class);
            require(this.values, "editing", Boolean.class);
            Bundle profile = require(this.values, "profile", Bundle.class);
            require(profile, "wxId", String.class); // empty during loading is valid
            require(profile, "nickname", String.class);
            Bundle status = require(profile, "status", Bundle.class);
            String kind = require(status, "kind", String.class);
            if (!("loading".equals(kind) || "no_status".equals(kind) || "ready".equals(kind) || "error".equals(kind)))
                throw new IllegalArgumentException("Unknown profile status kind");
            if ("ready".equals(kind)) {
                require(status, "iconId", String.class);
                require(status, "description", String.class);
            }
            ArrayList<?> cards = require(this.values, "cards", ArrayList.class);
            for (Object card : cards) {
                if (!(card instanceof Bundle)) throw new IllegalArgumentException("Cards must be platform Bundles");
                if (require((Bundle) card, "id", String.class).trim().isEmpty())
                    throw new IllegalArgumentException("Card ids must not be blank");
                require((Bundle) card, "cardType", String.class);
            }
            LinkedHashMap<String, Bitmap> safeImages = new LinkedHashMap<>();
            if (images != null) {
                for (Map.Entry<String, Bitmap> image : images.entrySet()) {
                    String key = Objects.requireNonNull(image.getKey(), "image key");
                    Bitmap bitmap = image.getValue();
                    if (bitmap != null && !bitmap.isRecycled()) safeImages.put(key, bitmap);
                }
            }
            this.images = Collections.unmodifiableMap(safeImages);
        }
    }

    public static final class Callbacks {
        public final Map<String, Runnable> actions;

        public Callbacks(Map<String, Runnable> actions) {
            LinkedHashMap<String, Runnable> safe = new LinkedHashMap<>();
            if (actions != null) for (Map.Entry<String, Runnable> action : actions.entrySet()) {
                String key = Objects.requireNonNull(action.getKey(), "callback key");
                if (action.getValue() != null) safe.put(key, action.getValue());
            }
            this.actions = Collections.unmodifiableMap(safe);
        }
    }

    public static View build(Activity activity, Bundle saved, Snapshot snapshot, Callbacks callbacks) {
        return create(activity, saved, snapshot, callbacks, false);
    }

    /** Uses the original WeKit toolbar content, with a separate lifecycle owner from the drawer. */
    public static View buildToolbar(Activity activity, Snapshot snapshot, Callbacks callbacks) {
        return create(activity, null, snapshot, callbacks, true);
    }

    private static View create(Activity activity, Bundle saved, Snapshot snapshot, Callbacks callbacks, boolean toolbar) {
        mainThread();
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(callbacks, "callbacks");
        if (activity == null || activity.isFinishing() || activity.isDestroyed())
            throw new IllegalStateException("Side-panel Activity is not active");
        try {
            Context module = moduleContext(activity);
            View view = (View) entry(module).getMethod("create", Activity.class, Context.class, Bundle.class,
                    Bundle.class, Map.class, Map.class, boolean.class).invoke(null,
                    activity, module, saved, snapshot.values, snapshot.images, callbacks.actions, toolbar);
            view.setTag(TAG);
            return view;
        } catch (ReflectiveOperationException | java.io.IOException error) {
            throw failure(error);
        }
    }

    public static void update(View view, Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        call(view, "update", new Class<?>[]{View.class, Bundle.class, Map.class}, snapshot.values, snapshot.images);
    }

    /** Update pre-bound actions after card/action IDs change, without rebuilding the composition. */
    public static void update(View view, Snapshot snapshot, Callbacks callbacks) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(callbacks, "callbacks");
        call(view, "update", new Class<?>[]{View.class, Bundle.class, Map.class, Map.class},
                snapshot.values, snapshot.images, callbacks.actions);
    }

    public static void save(View view, Bundle saved) {
        call(view, "save", new Class<?>[]{View.class, Bundle.class}, saved);
    }

    public static void lifecycle(View view, boolean resumed) {
        call(view, "lifecycle", new Class<?>[]{View.class, boolean.class}, resumed);
    }

    public static void dispose(View view) {
        call(view, "dispose", new Class<?>[]{View.class});
    }

    private static Object call(View view, String name, Class<?>[] types, Object... args) {
        mainThread();
        if (view == null) return null;
        if (!TAG.equals(view.getTag())) throw new IllegalArgumentException("Not an isolated side-panel view");
        try {
            Class<?> ui = entry;
            if (ui == null || view.getClass().getClassLoader() != ui.getClassLoader())
                throw new IllegalStateException("Side-panel view runtime mismatch");
            Object[] parameters = new Object[args.length + 1];
            parameters[0] = view;
            System.arraycopy(args, 0, parameters, 1, args.length);
            Method method = ui.getMethod(name, types);
            return method.invoke(null, parameters);
        } catch (ReflectiveOperationException error) {
            throw failure(error);
        }
    }

    private static synchronized Class<?> entry(Context module) throws ReflectiveOperationException, java.io.IOException {
        if (entry == null) entry = ComposePayloadLoader.entry(module, PREFIX, ENTRY);
        return entry;
    }

    private static Context moduleContext(Activity activity) {
        if ("dev.mindlink".equals(activity.getPackageName())) return activity;
        try {
            // No INCLUDE_CODE: assets/resources only; never use the host's class loader as parent.
            return activity.createPackageContext("dev.mindlink", Context.CONTEXT_IGNORE_SECURITY);
        } catch (android.content.pm.PackageManager.NameNotFoundException error) {
            throw new IllegalStateException("Installed MindLink resources are unavailable", error);
        }
    }

    private static void mainThread() {
        if (Looper.myLooper() != Looper.getMainLooper())
            throw new IllegalStateException("Side-panel bridge must run on the main thread");
    }

    private static IllegalStateException failure(Exception error) {
        Throwable cause = error instanceof InvocationTargetException
                ? ((InvocationTargetException) error).getCause() : error;
        return new IllegalStateException("WeKit/Compose side-panel failed", cause);
    }

    @SuppressWarnings("deprecation")
    private static <T> T require(Bundle bundle, String key, Class<T> type) {
        Object value = bundle.get(key);
        if (!type.isInstance(value)) throw new IllegalArgumentException("Side-panel key '" + key + "' must be " + type.getSimpleName());
        return type.cast(value);
    }

    /** Copy and reject arbitrary Parcelables/serializables before anything enters the isolated loader. */
    @SuppressWarnings({"deprecation", "unchecked"})
    private static Bundle copy(Bundle input) {
        Bundle result = new Bundle();
        for (String key : input.keySet()) {
            Object value = input.get(key);
            if (value == null) result.putString(key, null);
            else if (value instanceof String) result.putString(key, (String) value);
            else if (value instanceof Boolean) result.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) result.putInt(key, (Integer) value);
            else if (value instanceof Long) result.putLong(key, (Long) value);
            else if (value instanceof Double) result.putDouble(key, (Double) value);
            else if (value instanceof Float) result.putFloat(key, (Float) value);
            else if (value instanceof Bundle) result.putBundle(key, copy((Bundle) value));
            else if (value instanceof ArrayList) {
                ArrayList<?> list = (ArrayList<?>) value;
                ArrayList<Bundle> copied = new ArrayList<>();
                for (Object element : list) {
                    if (!(element instanceof Bundle)) throw new IllegalArgumentException("Only Bundle lists cross the side-panel boundary");
                    copied.add(copy((Bundle) element));
                }
                result.putParcelableArrayList(key, copied);
            } else throw new IllegalArgumentException("Non-platform snapshot value at '" + key + "'");
        }
        return result;
    }
}
