package wex.ui.core.feature;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Function;

/**
 * Loads the isolated Material3 settings panel from the existing side-panel Compose payload.
 * Only platform types cross this boundary; the Compose runtime stays in its own class loader.
 */
public final class SettingsComposeBridge {
    private static final String TAG = "MindLinkSettingsView";
    private static final String PREFIX = "sidepanel-compose-payload";
    private static final String ENTRY = "dev.mindlink.compose.SettingsUi";
    private static Class<?> entry;

    private SettingsComposeBridge() { }

    public static View build(Activity activity, Bundle saved, Bundle values,
                             Map<String, Function<String, String>> writers,
                             Map<String, Runnable> actions,
                             Map<String, Function<String, String>> readers,
                             boolean dark) {
        try {
            Context module = moduleContext(activity);
            View view = (View) entry(module).getMethod("create", Activity.class, Context.class, Bundle.class,
                    Bundle.class, Map.class, Map.class, Map.class, boolean.class).invoke(null,
                    activity, module, saved, values, writers, actions, readers, dark);
            view.setTag(TAG);
            return view;
        } catch (ReflectiveOperationException | java.io.IOException error) {
            throw new IllegalStateException("Material3 settings panel failed", error);
        }
    }

    public static void save(View view, Bundle output) {
        call(view, "save", new Class<?>[]{View.class, Bundle.class}, output);
    }

    public static void dispose(View view) {
        call(view, "dispose", new Class<?>[]{View.class});
    }

    /** Returns true when the panel consumed back by leaving a detail page. */
    public static boolean back(View view) {
        Object result = call(view, "back", new Class<?>[]{View.class});
        return Boolean.TRUE.equals(result);
    }

    private static Object call(View view, String name, Class<?>[] types, Object... args) {
        if (view == null) return null;
        if (!TAG.equals(view.getTag())) throw new IllegalArgumentException("Not an isolated settings view");
        try {
            Class<?> ui = entry;
            if (ui == null || view.getClass().getClassLoader() != ui.getClassLoader())
                throw new IllegalStateException("Settings view runtime mismatch");
            Object[] parameters = new Object[args.length + 1];
            parameters[0] = view;
            System.arraycopy(args, 0, parameters, 1, args.length);
            Method method = ui.getMethod(name, types);
            return method.invoke(null, parameters);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Material3 settings panel failed", error);
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
}
