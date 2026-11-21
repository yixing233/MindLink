package wex.ui.core.feature;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Memory-only diagnostic sink; no profile content, file access or network implementation. */
final class HostStageLog {
    static final List<String> stages = new CopyOnWriteArrayList<>();
    private HostStageLog() { }
    static void write(String component, String stage) { stages.add(component + " " + stage); }
}
