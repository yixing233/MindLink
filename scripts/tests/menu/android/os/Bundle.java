package android.os;

public class Bundle {
    private final java.util.Map<String,Object> values=new java.util.HashMap<>();
    public void putString(String key,String value){values.put(key,value);}
    public String getString(String key){return (String)values.get(key);}
    public void putIntArray(String key,int[] value){values.put(key,value);}
    public int[] getIntArray(String key){return (int[])values.get(key);}
}
