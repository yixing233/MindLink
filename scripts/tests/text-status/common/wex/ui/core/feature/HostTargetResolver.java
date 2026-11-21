package wex.ui.core.feature;

import android.os.Looper;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

final class HostTargetResolver {
    private static final Queue<Runnable> BACKGROUND = new ArrayDeque<>();
    static Throwable resolveFailure, submissionFailure;
    static int resolutions;
    static int pending() { return BACKGROUND.size(); }
    static void background(Runnable task) {
        if (submissionFailure != null) HostTargetResolver.<RuntimeException>raise(submissionFailure);
        BACKGROUND.add(task);
    }
    static void runOne() {
        Runnable task = BACKGROUND.poll();
        if (task == null) throw new AssertionError("No queued host worker");
        Looper.runOffMain(task);
    }
    static void drain() {
        int limit = 100;
        while (!BACKGROUND.isEmpty()) {
            if (--limit < 0) throw new AssertionError("Unbounded host queue");
            runOne();
        }
    }
    static Method resolveMethodNow(String key, MethodSpec spec) throws Exception {
        resolutions++;
        if (resolveFailure != null) HostTargetResolver.<RuntimeException>raise(resolveFailure);
        throw new IllegalStateException("Unexpected host target resolution: " + key);
    }
    static List<Method> findMethods(MethodSpec spec) { return Collections.emptyList(); }
    static Method unique(List<Method> values, String name) throws Exception {
        if (values.size() != 1) throw new IllegalStateException(name);
        return values.get(0);
    }
    static Class<?> classWithFields(String... fields) { throw new AssertionError("Unexpected record resolution"); }
    static boolean usesFieldThroughDirectInvoke(Method method, String name) { return false; }
    @SuppressWarnings("unchecked")
    static <E extends Throwable> void raise(Throwable failure) throws E { throw (E) failure; }
    static final class MethodSpec {
        MethodSpec parameterTypes(String... types) { return this; }
        MethodSpec usingExactStrings(String... strings) { return this; }
        MethodSpec returnType(String type) { return this; }
        MethodSpec parameterCount(int count) { return this; }
        MethodSpec modifiers(int modifiers) { return this; }
        MethodSpec declaredClass(String type) { return this; }
    }
}
