package wex.ui.core.feature;

import java.util.ArrayList;
import java.util.List;

/** Memory-only diagnostic seam for real database source compilation. */
final class HostStageLog {
    static final List<String> stages = new ArrayList<>();
    private HostStageLog() { }
    static void write(String component, String stage) { stages.add(component + " " + stage); }
}
