package wex.ui.core.feature;

public final class HostTargetResolver {
    public static ClassLoader hostClassLoader() { return HostTargetResolver.class.getClassLoader(); }
    public static class MethodSpec {
        public MethodSpec declaredClass(String value){return this;}
        public MethodSpec returnType(String value){return this;}
        public MethodSpec parameterTypes(String... value){return this;}
        public MethodSpec usingExactStrings(String... value){return this;}
        public MethodSpec classStrings(String... value){return this;}
        public MethodSpec name(String value){return this;}
    }
    public interface MethodCallback {
        void onResolved(java.lang.reflect.Method method);
        void onFailure(Throwable error);
    }
    private static final java.util.Map<String, MethodCallback> callbacks = new java.util.HashMap<>();
    public static void resolveMethod(String id,MethodSpec spec,MethodCallback callback){ callbacks.put(id, callback); callback.onFailure(new IllegalStateException("optional test target unavailable")); }
    static void complete(String id, java.lang.reflect.Method method) { callbacks.get(id).onResolved(method); }
}
