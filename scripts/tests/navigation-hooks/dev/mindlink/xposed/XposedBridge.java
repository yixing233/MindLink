package dev.mindlink.xposed;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executes registered before/after callbacks around a real test host method invocation. */
public class XposedBridge {
    public static final Map<Method,List<XC_MethodHook>> hooks=new LinkedHashMap<>();
    public static String lastLog;
    public static String failMethod;
    public static final Map<Class<?>, XC_MethodHook> constructors = new LinkedHashMap<>();
    public static void log(String value){lastLog=value;}
    public static void hookMethod(Method method,XC_MethodHook callback){
        if (method.getName().equals(failMethod)) throw new LinkageError("injected hook registration failure");
        hooks.computeIfAbsent(method,key->new ArrayList<>()).add(callback);
    }
    public static void hookAllConstructors(Class<?> owner,XC_MethodHook callback){constructors.put(owner,callback);}
    public static void constructed(Object owner) {
        XC_MethodHook callback = constructors.get(owner.getClass());
        if (callback == null) return;
        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam(); param.thisObject = owner;
        try { callback.afterHookedMethod(param); } catch (Throwable error) { throw new AssertionError(error); }
    }
    public static void baseLifecycle(android.app.Activity owner, String name, boolean before) {
        try {
            Method method = android.app.Activity.class.getDeclaredMethod(name);
            XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam(); param.method=method; param.thisObject=owner;param.args=new Object[0];
            for (XC_MethodHook callback : hooks.getOrDefault(method,java.util.Collections.emptyList())) {
                if (before) callback.beforeHookedMethod(param); else callback.afterHookedMethod(param);
            }
        } catch (Throwable error) { throw new AssertionError(error); }
    }
    public static void hookAllMethods(Class<?> owner,String name,XC_MethodHook callback){
        for(Method method:owner.getDeclaredMethods())if(method.getName().equals(name))hookMethod(method,callback);
    }
    public static Object invoke(Object owner,String name,Class<?>[] types,Object... args)throws Throwable{
        Method method=null;
        for(Class<?> current=owner.getClass();current!=null&&method==null;current=current.getSuperclass()){
            try{method=current.getDeclaredMethod(name,types);}catch(NoSuchMethodException ignored){}
        }
        if(method==null)throw new NoSuchMethodException(name);
        method.setAccessible(true);
        XC_MethodHook.MethodHookParam param=new XC_MethodHook.MethodHookParam();param.method=method;param.thisObject=owner;param.args=args;
        List<XC_MethodHook> callbacks=hooks.getOrDefault(method,java.util.Collections.emptyList());
        for(XC_MethodHook callback:callbacks)callback.beforeHookedMethod(param);
        try{param.result=method.invoke(owner,param.args);}catch(java.lang.reflect.InvocationTargetException error){param.throwable=error.getCause();}
        for(int i=callbacks.size()-1;i>=0;i--)callbacks.get(i).afterHookedMethod(param);
        if(param.throwable!=null)throw param.throwable;
        return param.result;
    }

    public static int getXposedVersion() { return 0; }
}
