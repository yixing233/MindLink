package wex.ui.core.feature;

import android.app.Application;
import android.content.Context;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Shared access to WeChat's conversation storage. */
public final class ConversationStorageBridge {
    private static final String TAG = "WEX: 会话存储桥: ";
    private static volatile ClassLoader hostClassLoader;
    private static volatile Object storage;
    private static volatile Method markRead;
    private static volatile Object redHintStorage;
    private static volatile Method redHintRemove;

    private ConversationStorageBridge() {
    }

    public static void initialize(XC_LoadPackage.LoadPackageParam lpparam, Context context) {
        HostTargetResolver.initialize(lpparam, context);
        hostClassLoader = HostTargetResolver.hostClassLoader();
        if (hostClassLoader == null && lpparam != null) hostClassLoader = lpparam.classLoader;
    }

    public static Object storage() {
        Object current = storage;
        if (current != null) {
            return current;
        }
        synchronized (ConversationStorageBridge.class) {
            if (storage == null) {
                storage = resolveStorage();
            }
            return storage;
        }
    }

    public static Method markReadMethod() {
        Object current = storage();
        if (current == null) {
            return null;
        }
        Method method = markRead;
        if (method != null) {
            return method;
        }
        synchronized (ConversationStorageBridge.class) {
            if (markRead == null) {
                markRead = findMarkRead(current.getClass());
            }
            return markRead;
        }
    }

    public static boolean markRead(String talker) {
        if (talker == null || talker.length() == 0) {
            return false;
        }
        try {
            Object current = storage();
            Method method = markReadMethod();
            if (current == null || method == null) {
                return false;
            }
            Object result = method.invoke(current, talker);
            clearRedHint(talker);
            return method.getReturnType() == void.class || Boolean.TRUE.equals(result);
        } catch (Throwable error) {
            log("标记失败 " + talker + " " + error.getClass().getSimpleName());
            return false;
        }
    }

    public static void notifyChanged() {
        Object current = storage();
        if (current == null) {
            return;
        }
        try {
            Class<?> type = current.getClass().getSuperclass();
            while (type != null && type != Object.class) {
                for (Method method : type.getDeclaredMethods()) {
                    Class<?>[] parameters = method.getParameterTypes();
                    if (parameters.length == 3 && parameters[0] == int.class
                            && parameters[1].isAssignableFrom(current.getClass())
                            && parameters[2] == Object.class
                            && method.getReturnType() == void.class) {
                        method.setAccessible(true);
                        method.invoke(current, 3, current, "");
                        return;
                    }
                }
                type = type.getSuperclass();
            }
        } catch (Throwable error) {
            log("通知刷新失败 " + error.getClass().getSimpleName());
        }
    }

    private static Object resolveStorage() {
        ClassLoader loader = hostClassLoader;
        if (loader == null) {
            loader = HostTargetResolver.hostClassLoader();
        }
        if (loader == null) {
            return null;
        }
        try {
            Class<?> foundationType = Class.forName(
                    "com.tencent.mm.plugin.messenger.foundation.h2", false, loader);
            Object service = resolveService(loader, foundationType);
            if (service == null) {
                log("未获取到 messenger foundation 服务");
                return null;
            }
            Method provider = null;
            for (Method method : service.getClass().getMethods()) {
                if (method.getParameterTypes().length == 0
                        && "com.tencent.mm.storage.l4".equals(method.getReturnType().getName())) {
                    provider = method;
                    break;
                }
            }
            if (provider == null) {
                try {
                    provider = service.getClass().getMethod("lj");
                } catch (Throwable ignored) {
                }
            }
            if (provider == null) {
                log("未找到会话存储提供方法");
                return null;
            }
            provider.setAccessible(true);
            Object value = provider.invoke(service);
            log("已获取会话存储 " + (value == null ? "null" : value.getClass().getName()));
            return value;
        } catch (Throwable error) {
            log("获取会话存储失败 " + error.getClass().getSimpleName());
            return null;
        }
    }

    private static Object resolveService(ClassLoader loader, Class<?> serviceType) {
        for (String className : new String[]{"pa5.n0", "hm0.j1"}) {
            try {
                Class<?> manager = Class.forName(className, false, loader);
                for (Method method : manager.getDeclaredMethods()) {
                    if (!Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }
                    Class<?>[] parameters = method.getParameterTypes();
                    if (parameters.length == 1 && parameters[0] == Class.class) {
                        method.setAccessible(true);
                        Object value = method.invoke(null, serviceType);
                        if (value != null && serviceType.isInstance(value)) {
                            return value;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Method findMarkRead(Class<?> type) {
        for (String name : new String[]{"b0", "T"}) {
            try {
                Method method = type.getMethod(name, String.class);
                if (method.getReturnType() == boolean.class || method.getReturnType() == void.class) {
                    method.setAccessible(true);
                    return method;
                }
            } catch (Throwable ignored) {
            }
        }
        for (Method method : type.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 1 && parameters[0] == String.class
                    && (method.getReturnType() == boolean.class || method.getReturnType() == void.class)) {
                String name = method.getName();
                if ("b0".equals(name) || "T".equals(name)) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        log("未找到标记已读方法");
        return null;
    }

    private static void clearRedHint(String talker) {
        try {
            if (redHintStorage == null || redHintRemove == null) {
                resolveRedHint();
            }
            if (redHintStorage != null && redHintRemove != null) {
                redHintRemove.invoke(redHintStorage, talker);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void resolveRedHint() {
        ClassLoader loader = hostClassLoader;
        if (loader == null) {
            return;
        }
        try {
            Class<?> type = Class.forName("na5.u", false, loader);
            Object value = resolveService(loader, type);
            if (value == null) {
                return;
            }
            for (String name : new String[]{"g", "b"}) {
                try {
                    Method method = type.getMethod(name, String.class);
                    if (method.getReturnType() == void.class) {
                        method.setAccessible(true);
                        redHintStorage = value;
                        redHintRemove = method;
                        return;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    static Context currentApplication() {
        try {
            Method method = Class.forName("android.app.ActivityThread")
                    .getDeclaredMethod("currentApplication");
            method.setAccessible(true);
            Object value = method.invoke(null);
            return value instanceof Application ? (Application) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void log(String message) {
        try {
            XposedBridge.log(TAG + message);
        } catch (Throwable ignored) {
        }
    }
}
