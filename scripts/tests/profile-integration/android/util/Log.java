package android.util;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
/** Memory-only Android log boundary for the real HostStageLog. */
public final class Log {
    public static final List<String> stages = new CopyOnWriteArrayList<>();
    public static int i(String tag, String value) { stages.add(value); return 0; }
}
