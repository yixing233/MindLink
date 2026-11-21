package de.robv.android.xposed;

public abstract class XC_MethodHook {
    public static class MethodHookParam {
        public java.lang.reflect.Member method;
        public Object thisObject;
        public Object[] args;
        Object result;
        Throwable throwable;
        public Object getResult(){return result;}
        public void setResult(Object value){result=value;}
        public boolean hasThrowable(){return throwable!=null;}
        public Throwable getThrowable(){return throwable;}
    }
    protected void beforeHookedMethod(MethodHookParam param)throws Throwable{}
    protected void afterHookedMethod(MethodHookParam param)throws Throwable{}
}
