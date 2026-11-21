package wex.ui.core.feature;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Loads only the signed app's own Compose payload, with no parent access to the old runtime. */
final class ComposePayloadBridge {
    private static final String TAG="mdl_isolated_compose";
    private static Class<?> entry;
    private ComposePayloadBridge(){}

    static View build(Activity activity,Bundle saved){
        try{
            Class<?> ui=entry(activity);
            View view=(View)ui.getMethod("create",Activity.class,Bundle.class).invoke(null,activity,saved);
            view.setTag(TAG);return view;
        }catch(ReflectiveOperationException|java.io.IOException error){throw failure(error);}
    }
    static void save(Activity activity,Bundle state){call(activity,"save",new Class<?>[]{View.class,Bundle.class},state);}
    static void lifecycle(Activity activity,boolean resumed){call(activity,"lifecycle",new Class<?>[]{View.class,boolean.class},resumed);}
    static void dispose(Activity activity){call(activity,"dispose",new Class<?>[]{View.class});}
    static boolean back(Activity activity){Object result=call(activity,"back",new Class<?>[]{View.class});return Boolean.TRUE.equals(result);}
    private static Object call(Activity activity,String name,Class<?>[] types,Object... args){
        View view=activity.getWindow().getDecorView().findViewWithTag(TAG);
        if(view==null)return null;
        try{
            Object[] parameters=new Object[args.length+1];parameters[0]=view;System.arraycopy(args,0,parameters,1,args.length);
            Method method=entry(activity).getMethod(name,types);return method.invoke(null,parameters);
        }catch(ReflectiveOperationException|java.io.IOException error){throw failure(error);}
    }
    private static synchronized Class<?> entry(Activity activity)throws ReflectiveOperationException,java.io.IOException{
        if(!"dev.mindlink".equals(activity.getPackageName())||!"dev.mindlink".equals(Application.getProcessName()))
            throw new IllegalStateException("Compose UI is desktop-process only");
        if(entry!=null)return entry;
        entry=ComposePayloadLoader.entry(activity,"compose-payload","dev.mindlink.compose.DesktopUi");
        return entry;
    }
    private static IllegalStateException failure(Exception error){Throwable cause=error instanceof InvocationTargetException?((InvocationTargetException)error).getCause():error;return new IllegalStateException("Material3/Compose desktop failed",cause);}
}
