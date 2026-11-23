package dev.mindlink.xposed;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** de.robv XposedHelpers 的同形实现（纯反射，与 hook 框架无关）。 */
public final class XposedHelpers {
    private static final Map<String, Class<?>> PRIMITIVES = new HashMap<>();
    private static final Map<String, Field> FIELDS = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
    private static final WeakHashMap<Object, HashMap<String, Object>> ADDITIONAL = new WeakHashMap<>();

    static {
        for (Class<?> c : new Class<?>[]{boolean.class, byte.class, char.class, short.class, int.class,
                long.class, float.class, double.class, void.class}) {
            PRIMITIVES.put(c.getName(), c);
        }
    }

    private XposedHelpers() {
    }

    public static final class ClassNotFoundError extends Error {
        public ClassNotFoundError(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class InvocationTargetError extends Error {
        public InvocationTargetError(Throwable cause) {
            super(cause);
        }
    }

    // ------------------------------------------------------------------ classes
    public static Class<?> findClass(String className, ClassLoader classLoader) {
        if (classLoader == null) classLoader = XposedHelpers.class.getClassLoader();
        Class<?> primitive = PRIMITIVES.get(className);
        if (primitive != null) return primitive;
        if (className.endsWith("[]")) {
            Class<?> component = findClass(className.substring(0, className.length() - 2), classLoader);
            return Array.newInstance(component, 0).getClass();
        }
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new ClassNotFoundError(className, e);
        }
    }

    public static Class<?> findClassIfExists(String className, ClassLoader classLoader) {
        try {
            return findClass(className, classLoader);
        } catch (ClassNotFoundError e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ fields
    public static Field findField(Class<?> clazz, String fieldName) {
        String key = identity(clazz) + '#' + fieldName;
        Field cached = FIELDS.get(key);
        if (cached != null) return cached;
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(fieldName);
                field.setAccessible(true);
                FIELDS.put(key, field);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldError(clazz.getName() + "#" + fieldName);
    }

    public static Field findFieldIfExists(Class<?> clazz, String fieldName) {
        try {
            return findField(clazz, fieldName);
        } catch (NoSuchFieldError e) {
            return null;
        }
    }

    public static Object getObjectField(Object obj, String fieldName) {
        try {
            return findField(obj.getClass(), fieldName).get(obj);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static void setObjectField(Object obj, String fieldName, Object value) {
        try {
            findField(obj.getClass(), fieldName).set(obj, value);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static int getIntField(Object obj, String fieldName) {
        try {
            return findField(obj.getClass(), fieldName).getInt(obj);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static void setIntField(Object obj, String fieldName, int value) {
        try {
            findField(obj.getClass(), fieldName).setInt(obj, value);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static boolean getBooleanField(Object obj, String fieldName) {
        try {
            return findField(obj.getClass(), fieldName).getBoolean(obj);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static void setBooleanField(Object obj, String fieldName, boolean value) {
        try {
            findField(obj.getClass(), fieldName).setBoolean(obj, value);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static Object getStaticObjectField(Class<?> clazz, String fieldName) {
        try {
            return findField(clazz, fieldName).get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    // ------------------------------------------------------------------ methods
    public static Method findMethodExact(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        try {
            Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError(clazz.getName() + '#' + methodName + Arrays.toString(parameterTypes) + "#exact");
        }
    }

    public static Method findMethodExact(Class<?> clazz, String methodName, Object... parameterTypes) {
        return findMethodExact(clazz, methodName, toTypes(clazz.getClassLoader(), parameterTypes));
    }

    public static Method findMethodExact(String className, ClassLoader classLoader, String methodName, Object... parameterTypes) {
        return findMethodExact(findClass(className, classLoader), methodName, parameterTypes);
    }

    public static Constructor<?> findConstructorExact(Class<?> clazz, Object... parameterTypes) {
        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor(toTypes(clazz.getClassLoader(), parameterTypes));
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError(clazz.getName() + Arrays.toString(parameterTypes) + "#exact");
        }
    }

    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        XC_MethodHook callback = callbackOf(parameterTypesAndCallback);
        Object[] types = Arrays.copyOf(parameterTypesAndCallback, parameterTypesAndCallback.length - 1);
        return XposedBridge.hookMethod(findMethodExact(clazz, methodName, toTypes(clazz.getClassLoader(), types)), callback);
    }

    public static XC_MethodHook.Unhook findAndHookMethod(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) {
        return findAndHookMethod(findClass(className, classLoader), methodName, parameterTypesAndCallback);
    }

    public static XC_MethodHook.Unhook findAndHookConstructor(Class<?> clazz, Object... parameterTypesAndCallback) {
        XC_MethodHook callback = callbackOf(parameterTypesAndCallback);
        Object[] types = Arrays.copyOf(parameterTypesAndCallback, parameterTypesAndCallback.length - 1);
        return XposedBridge.hookMethod(findConstructorExact(clazz, types), callback);
    }

    public static XC_MethodHook.Unhook findAndHookConstructor(String className, ClassLoader classLoader, Object... parameterTypesAndCallback) {
        return findAndHookConstructor(findClass(className, classLoader), parameterTypesAndCallback);
    }

    public static Object callMethod(Object obj, String methodName, Object... args) {
        return invoke(findMethodBestMatch(obj.getClass(), methodName, args), obj, args);
    }

    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
        return invoke(findMethodBestMatch(clazz, methodName, args), null, args);
    }

    public static Object newInstance(Class<?> clazz, Object... args) {
        Constructor<?> best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Constructor<?> c : clazz.getDeclaredConstructors()) {
            int score = score(c.getParameterTypes(), args);
            if (score >= 0 && score < bestScore) {
                best = c;
                bestScore = score;
            }
        }
        if (best == null) throw new NoSuchMethodError(clazz.getName() + "#<init>#bestmatch");
        try {
            best.setAccessible(true);
            return best.newInstance(args);
        } catch (InvocationTargetException e) {
            throw new InvocationTargetError(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        } catch (InstantiationException e) {
            throw new InstantiationError(e.getMessage());
        }
    }

    public static Method findMethodBestMatch(Class<?> clazz, String methodName, Object... args) {
        if (args == null) args = new Object[0];
        StringBuilder key = new StringBuilder(identity(clazz)).append('#').append(methodName);
        for (Object arg : args) key.append(',').append(arg == null ? "null" : identity(arg.getClass()));
        Method cached = METHODS.get(key.toString());
        if (cached != null) return cached;
        Method best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(methodName)) continue;
                int score = score(m.getParameterTypes(), args);
                if (score >= 0 && score < bestScore) {
                    best = m;
                    bestScore = score;
                }
            }
        }
        if (best == null) {
            for (Method m : clazz.getMethods()) {
                if (!m.getName().equals(methodName)) continue;
                int score = score(m.getParameterTypes(), args);
                if (score >= 0 && score < bestScore) {
                    best = m;
                    bestScore = score;
                }
            }
        }
        if (best == null) throw new NoSuchMethodError(clazz.getName() + '#' + methodName + "#bestmatch");
        best.setAccessible(true);
        METHODS.put(key.toString(), best);
        return best;
    }

    // ------------------------------------------------------------------ additional fields
    public static Object setAdditionalInstanceField(Object obj, String key, Object value) {
        if (obj == null) throw new NullPointerException("object must not be null");
        synchronized (ADDITIONAL) {
            HashMap<String, Object> fields = ADDITIONAL.get(obj);
            if (fields == null) {
                fields = new HashMap<>();
                ADDITIONAL.put(obj, fields);
            }
            return fields.put(key, value);
        }
    }

    public static Object getAdditionalInstanceField(Object obj, String key) {
        if (obj == null) throw new NullPointerException("object must not be null");
        synchronized (ADDITIONAL) {
            HashMap<String, Object> fields = ADDITIONAL.get(obj);
            return fields == null ? null : fields.get(key);
        }
    }

    public static Object removeAdditionalInstanceField(Object obj, String key) {
        if (obj == null) throw new NullPointerException("object must not be null");
        synchronized (ADDITIONAL) {
            HashMap<String, Object> fields = ADDITIONAL.get(obj);
            return fields == null ? null : fields.remove(key);
        }
    }

    // ------------------------------------------------------------------ internals
    private static String identity(Class<?> c) {
        return c.getName() + '@' + System.identityHashCode(c);
    }

    private static XC_MethodHook callbackOf(Object[] parameterTypesAndCallback) {
        if (parameterTypesAndCallback.length == 0
                || !(parameterTypesAndCallback[parameterTypesAndCallback.length - 1] instanceof XC_MethodHook)) {
            throw new IllegalArgumentException("no callback defined");
        }
        return (XC_MethodHook) parameterTypesAndCallback[parameterTypesAndCallback.length - 1];
    }

    private static Class<?>[] toTypes(ClassLoader classLoader, Object[] parameterTypes) {
        Class<?>[] types = new Class<?>[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Object type = parameterTypes[i];
            if (type instanceof Class) types[i] = (Class<?>) type;
            else if (type instanceof String) types[i] = findClass((String) type, classLoader);
            else throw new IllegalArgumentException("parameter type must be Class or String: " + type);
        }
        return types;
    }

    private static Object invoke(Method method, Object receiver, Object[] args) {
        try {
            return method.invoke(receiver, args);
        } catch (InvocationTargetException e) {
            throw new InvocationTargetError(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    private static Class<?> box(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == boolean.class) return Boolean.class;
        if (c == byte.class) return Byte.class;
        if (c == char.class) return Character.class;
        if (c == short.class) return Short.class;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == float.class) return Float.class;
        if (c == double.class) return Double.class;
        return Void.class;
    }

    private static int rank(Class<?> c) {
        if (c == Byte.class) return 1;
        if (c == Short.class || c == Character.class) return 2;
        if (c == Integer.class) return 3;
        if (c == Long.class) return 4;
        if (c == Float.class) return 5;
        if (c == Double.class) return 6;
        return -1;
    }

    /** -1 表示不匹配；否则越小越精确。 */
    private static int score(Class<?>[] params, Object[] args) {
        if (args == null) args = new Object[0];
        if (params.length != args.length) return -1;
        int total = 0;
        for (int i = 0; i < params.length; i++) {
            Object arg = args[i];
            if (arg == null) {
                if (params[i].isPrimitive()) return -1;
                total += 1;
                continue;
            }
            Class<?> want = box(params[i]);
            Class<?> have = arg.getClass();
            if (want == have) continue;
            if (want.isAssignableFrom(have)) {
                int distance = 1;
                for (Class<?> c = have; c != null && c != want; c = c.getSuperclass()) distance++;
                total += Math.min(distance, 50);
                continue;
            }
            if (params[i].isPrimitive() && rank(have) > 0 && rank(want) > rank(have)
                    && !(have == Character.class && want == Short.class)) {
                total += 60;
                continue;
            }
            return -1;
        }
        return total;
    }
}
