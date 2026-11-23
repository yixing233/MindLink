package wex.ui.core.feature;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Bounded diagnostic stages only: callers must never pass host messages, SQL or profile values. */
final class HostStageLog {
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1, 1, 20L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), runnable -> {
                Thread thread = new Thread(runnable, "MindLink-stage-log"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.DiscardPolicy());
    static { IO.allowCoreThreadTimeOut(true); }

    private HostStageLog() { }

    static void write(String component, String stage) {
        String evidence = component + " " + stage + " pid=" + android.os.Process.myPid()
                + " runtimeDiag=profile-evidence-20261115-v1 api=" + xposedApi();
        try { Log.i("MindLinkStage", evidence); }
        catch (Throwable ignored) { }
        try {
            IO.execute(() -> {
                try {
                    File directory = new File("/sdcard/Android/media/com.tencent.mm/Mdl");
                    if (!directory.isDirectory()) return;
                    File output = new File(directory, "feature-stages.txt");
                    if (output.length() >= 256L * 1024L) return;
                    byte[] line = (System.currentTimeMillis() + " " + evidence + "\n")
                            .getBytes(StandardCharsets.UTF_8);
                    try (FileOutputStream stream = new FileOutputStream(output, true)) { stream.write(line); }
                } catch (Throwable ignored) { }
            });
        } catch (Throwable ignored) { }
    }

    /** Framework API level of the running hook container; recorded so the desktop can show it. */
    private static String xposedApi() {
        try {
            int version = dev.mindlink.xposed.XposedBridge.getXposedVersion();
            return version > 0 ? Integer.toString(version) : "none";
        } catch (Throwable unavailable) {
            return "none";
        }
    }
}
