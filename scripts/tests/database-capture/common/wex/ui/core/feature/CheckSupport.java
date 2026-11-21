package wex.ui.core.feature;

import android.content.Context;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class CheckSupport {
    static final String SQL = "SELECT 'private_sql_capture_68129' FROM userinfo WHERE id=?";
    static final String PARAM = "private_parameter_capture_52391";
    static final String PREF = "private_preferences_capture_79352";
    static final String PATH = "/private/database_capture_49285.db";
    static final String MESSAGE = "private_throwable_capture_38472";
    static int checks;
    static int messageReads;

    static final class BareThrowable extends Throwable {
        BareThrowable() { super(MESSAGE); }
        @Override public String getMessage() { messageReads++; return MESSAGE; }
    }
    static final class HostError extends Error {
        HostError() { super(MESSAGE); }
        @Override public String getMessage() { messageReads++; return MESSAGE; }
    }
    @SuppressWarnings("unchecked")
    static <T extends Throwable> void raise(Throwable error) throws T { throw (T) error; }
    static void check(boolean value, String description) {
        checks++;
        if (!value) throw new AssertionError(description);
    }
    static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    static Method method(Class<?> owner, String name, Class<?>... parameters) throws Exception {
        Method method = owner.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }
    static XC_LoadPackage.LoadPackageParam loadPackage() {
        XC_LoadPackage.LoadPackageParam load = new XC_LoadPackage.LoadPackageParam();
        load.classLoader = CheckSupport.class.getClassLoader();
        return load;
    }
    static void privacy() {
        check(messageReads == 0, "Host Throwable.getMessage() must never be consulted");
        check(Context.preferenceReads == 0, "Capture/bootstrap must not inspect preferences");
        for (String log : XposedBridge.logs) {
            for (String secret : new String[]{SQL, "private_sql_capture_68129", PARAM, PREF, PATH, MESSAGE})
                check(!log.contains(secret), "SQL/parameters/preferences/path/host error leaked to logs");
        }
    }
    static void passed(String suite, String name) {
        privacy();
        System.out.println("PASS " + suite + "/" + name + " (" + checks + " assertions)");
    }
}
