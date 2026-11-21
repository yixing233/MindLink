package de.robv.android.xposed;

public final class XposedHelpers {
    public static Class<?> findClass(String name,ClassLoader loader){try{return Class.forName(name,false,loader);}catch(ClassNotFoundException error){throw new IllegalStateException(error);}}
    public static Object callMethod(Object owner,String name,Object...args){try{return owner.getClass().getMethod(name).invoke(owner,args);}catch(Exception error){throw new IllegalStateException(error);}}
    public static Object getObjectField(Object owner,String name){try{java.lang.reflect.Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}catch(Exception error){throw new IllegalStateException(error);}}
}
