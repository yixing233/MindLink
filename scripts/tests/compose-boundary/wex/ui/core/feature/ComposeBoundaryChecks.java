package wex.ui.core.feature;

import android.app.Activity;
import android.app.Application;
import android.content.res.AssetManager;
import android.os.Bundle;
import java.nio.file.Files;
import java.nio.file.Path;

/** Desktop safety boundary only; does not simulate Material3 rendering or native DEX execution. */
public final class ComposeBoundaryChecks {
    private static int checks;
    private static void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
    private static void denied(Activity activity,String phrase){
        try{AboutPageBuilder.build(activity);throw new AssertionError("unexpected Java UI fallback");}
        catch(IllegalStateException expected){check(expected.toString().contains(phrase)||(expected.getCause()!=null&&expected.getCause().toString().contains(phrase)),"failure evidence: "+phrase);}
    }
    public static void main(String[] args)throws Exception{
        Path directory=Path.of(args[0]);AssetManager.root=directory;
        Activity desktop=new Activity();
        Application.process="com.tencent.mm";
        denied(desktop,"desktop-process only");check(dalvik.system.InMemoryDexClassLoader.attempts==0,"host process never loads payload");
        Application.process="dev.mindlink";
        denied(new Activity(){@Override public String getPackageName(){return"com.tencent.mm";}},"desktop-process only");
        denied(desktop,"Material3/Compose desktop failed");check(!Files.exists(directory.resolve("compose-payload/index.txt")),"missing asset not created");
        Path payload=directory.resolve("compose-payload");Files.createDirectories(payload);
        Files.writeString(payload.resolve("index.txt"),"../external.dex bad\n");denied(desktop,"Invalid packaged payload manifest");
        Files.writeString(payload.resolve("index.txt"),"classes.bin "+"0".repeat(64)+"\n");
        Files.write(payload.resolve("classes.bin"),new byte[]{'d','e','x'});denied(desktop,"integrity failed");
        check(dalvik.system.InMemoryDexClassLoader.attempts==0,"invalid payload rejected before loader");
        Files.writeString(payload.resolve("index.txt"),"");denied(desktop,"payload missing");
        byte[] bytes=new byte[112];bytes[0]='d';bytes[1]='e';bytes[2]='x';Files.write(payload.resolve("classes.bin"),bytes);
        StringBuilder hash=new StringBuilder();for(byte value:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format("%02x",value&255));
        Files.writeString(payload.resolve("index.txt"),"classes.bin "+hash+"\n");
        try{AboutPageBuilder.build(desktop);throw new AssertionError("JVM executed DEX");}
        catch(UnsupportedOperationException expected){check(expected.getMessage().contains("device-only"),"runtime gate remains device-only");}
        check(dalvik.system.InMemoryDexClassLoader.attempts==1,"verified payload reaches loader");
        AboutPageBuilder.save(desktop,new Bundle());check(!AboutPageBuilder.back(desktop),"absent desktop root back safely falls through");AboutPageBuilder.dispose(desktop);
        check(wex.ui.core.WexStore.calls==0&&FeatureDiagnostics.calls==0&&ImagePicker.calls==0,"no host access on failures");
        System.out.println("PASS: "+checks+" Compose loader/process/integrity/fail-closed boundary assertions; DEX execution and UI NOT tested on JVM");
    }
}
