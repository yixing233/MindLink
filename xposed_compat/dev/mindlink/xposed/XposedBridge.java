package dev.mindlink.xposed;

import android.util.Log;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * de.robv XposedBridge 的同形实现，底层只走 libxposed（参照 WeKit LxpHookWrapper）。
 * hook 构建器、HookHandle 与 Invoker 按名称反射调用，不依赖它们的确切类型签名。
 */
public final class XposedBridge {
    public static final String TAG = "MindLink";

    private static volatile XposedModule module;
    private static Method sHook;
    private static Method sSetPriority;
    private static Method sSetExceptionMode;
    private static Object sPassthrough;
    private static Method sIntercept;
    private static Method sUnhook;

    private XposedBridge() {
    }

    static synchronized void attach(XposedModule m) {
        module = m;
    }

    private static XposedModule module() {
        XposedModule m = module;
        if (m == null) throw new IllegalStateException("libxposed module is not attached");
        return m;
    }

    public static int getXposedVersion() {
        XposedModule m = module;
        try {
            return m == null ? 0 : m.getApiVersion();
        } catch (Throwable t) {
            return 0;
        }
    }

    public static void log(String text) {
        XposedModule m = module;
        if (m != null) {
            try {
                m.log(Log.INFO, TAG, String.valueOf(text), null);
                return;
            } catch (Throwable ignored) {
            }
        }
        Log.i(TAG, String.valueOf(text));
    }

    public static void log(Throwable t) {
        XposedModule m = module;
        if (m != null) {
            try {
                m.log(Log.ERROR, TAG, String.valueOf(t), t);
                return;
            } catch (Throwable ignored) {
            }
        }
        Log.e(TAG, Log.getStackTraceString(t));
    }

    public static XC_MethodHook.Unhook hookMethod(Member hookMethod, XC_MethodHook callback) {
        if (!(hookMethod instanceof Method) && !(hookMethod instanceof Constructor)) {
            throw new IllegalArgumentException("Only methods and constructors can be hooked: " + hookMethod);
        }
        if (Modifier.isAbstract(hookMethod.getModifiers())) {
            throw new IllegalArgumentException("Cannot hook abstract methods: " + hookMethod);
        }
        if (callback == null) throw new IllegalArgumentException("callback should not be null!");
        Dispatcher dispatcher = new Dispatcher(callback);
        HookToken token = new HookToken(dispatcher, install((Executable) hookMethod, dispatcher));
        return new XC_MethodHook.Unhook(hookMethod, callback, token);
    }

    public static Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> hookClass, String methodName, XC_MethodHook callback) {
        Set<XC_MethodHook.Unhook> unhooks = new HashSet<>();
        for (Method method : hookClass.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && !Modifier.isAbstract(method.getModifiers())) {
                unhooks.add(hookMethod(method, callback));
            }
        }
        return unhooks;
    }

    public static Set<XC_MethodHook.Unhook> hookAllConstructors(Class<?> hookClass, XC_MethodHook callback) {
        Set<XC_MethodHook.Unhook> unhooks = new HashSet<>();
        for (Constructor<?> constructor : hookClass.getDeclaredConstructors()) {
            unhooks.add(hookMethod(constructor, callback));
        }
        return unhooks;
    }

    /**
     * The framework's ORIGIN invoker could not be used for this call. Never wraps an exception
     * thrown by the target method itself, so a caller may safely fall back to a plain call.
     */
    public static final class OriginalUnavailable extends RuntimeException {
        OriginalUnavailable(String message, Throwable cause) { super(message, cause); }
    }

    private static Method sGetInvoker;
    private static Method sSetType;
    private static Object sOrigin;
    /** Assigned last in {@link #resolveInvoker}; its volatile write publishes the other three. */
    private static volatile Method sInvoke;
    /** ORIGIN-typed invokers are reused: getInvoker + setType per call cost two framework calls per row. */
    private static final ConcurrentHashMap<Method, Object> INVOKERS = new ConcurrentHashMap<>();
    private static volatile boolean originFailureLogged;

    /**
     * 绕过所有 hook 调用原方法（libxposed 102 {@code getInvoker(Method).setType(Type.ORIGIN).invoke}）。
     * 目标方法自身抛出的异常原样抛出；框架侧不可用时抛 {@link OriginalUnavailable}。
     */
    public static Object invokeOriginalMethod(Member method, Object thisObject, Object[] args) throws Throwable {
        if (!(method instanceof Method)) throw new IllegalArgumentException("Only methods are supported: " + method);
        Object invoker;
        try {
            invoker = originInvoker((Method) method);
        } catch (Throwable t) {
            throw originUnavailable(method, t instanceof InvocationTargetException && t.getCause() != null ? t.getCause() : t);
        }
        try {
            return sInvoke.invoke(invoker, thisObject, args == null ? new Object[0] : args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            // Invoker.invoke wraps the target's own exception in a further InvocationTargetException.
            if (cause instanceof InvocationTargetException && cause.getCause() != null) throw cause.getCause();
            throw originUnavailable(method, cause != null ? cause : e);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw originUnavailable(method, e);
        }
    }

    private static Object originInvoker(Method method) throws ReflectiveOperationException {
        Object cached = INVOKERS.get(method);
        if (cached != null) return cached;
        resolveInvoker();
        Object invoker = sGetInvoker.invoke(module(), method);
        if (invoker == null) throw new IllegalStateException("getInvoker returned null");
        Object typed = sSetType.invoke(invoker, sOrigin);
        if (typed != null) invoker = typed;
        Object previous = INVOKERS.putIfAbsent(method, invoker);
        return previous != null ? previous : invoker;
    }

    private static synchronized void resolveInvoker() throws ReflectiveOperationException {
        if (sInvoke != null) return;
        ClassLoader api = XposedInterface.class.getClassLoader();
        Class<?> invokerType = Class.forName("io.github.libxposed.api.XposedInterface$Invoker", true, api);
        Class<?> typeType = Class.forName("io.github.libxposed.api.XposedInterface$Invoker$Type", true, api);
        // Declared on the public interface (XposedInterfaceWrapper implements it as public final).
        sGetInvoker = XposedInterface.class.getMethod("getInvoker", Method.class);
        sSetType = invokerType.getMethod("setType", typeType);
        sOrigin = typeType.getField("ORIGIN").get(null);
        sInvoke = invokerType.getMethod("invoke", Object.class, Object[].class);
    }

    private static OriginalUnavailable originUnavailable(Member method, Throwable cause) {
        if (!originFailureLogged) {
            originFailureLogged = true;
            // Framework-side failure only (never the target's own exception); type and message
            // describe the hook API, not host data.
            log("invokeOriginalMethod unavailable for " + method.getDeclaringClass().getName() + "#" + method.getName()
                    + ": " + cause.getClass().getName() + (cause.getMessage() == null ? "" : " " + cause.getMessage()));
        }
        return new OriginalUnavailable("ORIGIN invoker unavailable", cause);
    }

    static void unhook(HookToken token) {
        token.dispatcher.active = false;
        Object handle = token.handle;
        if (handle == null || sUnhook == null) return;
        try {
            sUnhook.invoke(handle);
        } catch (Throwable t) {
            log(t);
        }
    }

    private static synchronized void resolve(XposedModule m) throws ReflectiveOperationException {
        if (sIntercept != null) return;
        Method hook = m.getClass().getMethod("hook", Executable.class);
        Class<?> builder = hook.getReturnType();
        Method setPriority = null;
        Method setExceptionMode = null;
        Method intercept = null;
        for (Method candidate : builder.getMethods()) {
            Class<?>[] params = candidate.getParameterTypes();
            if (params.length != 1) continue;
            if (candidate.getName().equals("setPriority") && params[0] == int.class) setPriority = candidate;
            if (candidate.getName().equals("setExceptionMode") && params[0].isEnum()) setExceptionMode = candidate;
            if (candidate.getName().equals("intercept") && params[0].isAssignableFrom(Dispatcher.class)) intercept = candidate;
        }
        if (intercept == null) throw new NoSuchMethodException(builder.getName() + "#intercept(Hooker)");
        Object passthrough = null;
        if (setExceptionMode != null) {
            for (Object constant : setExceptionMode.getParameterTypes()[0].getEnumConstants()) {
                if ("PASSTHROUGH".equals(((Enum<?>) constant).name())) passthrough = constant;
            }
        }
        Method unhook = null;
        try {
            unhook = intercept.getReturnType().getMethod("unhook");
        } catch (NoSuchMethodException ignored) {
        }
        sHook = hook;
        sSetPriority = setPriority;
        sSetExceptionMode = passthrough == null ? null : setExceptionMode;
        sPassthrough = passthrough;
        sUnhook = unhook;
        sIntercept = intercept;
    }

    private static Object step(Method method, Object builder, Object arg) throws ReflectiveOperationException {
        if (method == null) return builder;
        Object next = method.invoke(builder, arg);
        return next != null && method.getDeclaringClass().isInstance(next) ? next : builder;
    }

    private static Object install(Executable target, Dispatcher dispatcher) {
        XposedModule m = module();
        try {
            resolve(m);
            Object builder = sHook.invoke(m, target);
            builder = step(sSetPriority, builder, dispatcher.callback.priority);
            builder = step(sSetExceptionMode, builder, sPassthrough);
            return sIntercept.invoke(builder, dispatcher);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("hook failed: " + target, cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("libxposed hook API unavailable for " + target, e);
        }
    }

    static final class HookToken {
        final Dispatcher dispatcher;
        final Object handle;

        HookToken(Dispatcher dispatcher, Object handle) {
            this.dispatcher = dispatcher;
            this.handle = handle;
        }
    }

    /** 单个回调对应一个 libxposed 拦截器，按 Xposed 语义执行 before → 原方法/下游链 → after。 */
    static final class Dispatcher implements XposedInterface.Hooker {
        final XC_MethodHook callback;
        volatile boolean active = true;

        Dispatcher(XC_MethodHook callback) {
            this.callback = callback;
        }

        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            if (!active) return chain.proceed();
            XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
            param.method = chain.getExecutable();
            param.thisObject = chain.getThisObject();
            param.args = chain.getArgs().toArray();
            try {
                callback.beforeHookedMethod(param);
            } catch (Throwable t) {
                log(t);
                param.outcome(null, null);
                param.returnEarly = false;
            }
            if (!param.returnEarly) {
                try {
                    param.outcome(chain.proceed(param.args), null);
                } catch (Throwable t) {
                    param.outcome(null, t);
                }
            }
            Object lastResult = param.getResult();
            Throwable lastThrowable = param.getThrowable();
            try {
                callback.afterHookedMethod(param);
            } catch (Throwable t) {
                log(t);
                param.outcome(lastResult, lastThrowable);
            }
            Throwable thrown = param.getThrowable();
            if (thrown != null) throw thrown;
            return param.getResult();
        }
    }
}
