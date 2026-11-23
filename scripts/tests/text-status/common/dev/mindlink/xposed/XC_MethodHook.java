package dev.mindlink.xposed;
public class XC_MethodHook {
    public static class MethodHookParam { public Object thisObject; }
    protected void afterHookedMethod(MethodHookParam param) throws Throwable { }
}
