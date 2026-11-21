package android.window;

public class OnBackInvokedDispatcher {
    public static final int PRIORITY_DEFAULT=0;
    private final java.util.Set<OnBackInvokedCallback> callbacks=new java.util.LinkedHashSet<>();
    public void registerOnBackInvokedCallback(int priority,OnBackInvokedCallback callback){callbacks.add(callback);}
    public void unregisterOnBackInvokedCallback(OnBackInvokedCallback callback){callbacks.remove(callback);}
    public int countForTest(){return callbacks.size();}
    public void backForTest(){for(OnBackInvokedCallback callback:new java.util.ArrayList<>(callbacks))callback.onBackInvoked();}
}
