package wex.ui.core.feature;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Captures only stage text in memory; never opens Android storage or a network. */
final class HostStageLog {
    static final List<String> stages = new CopyOnWriteArrayList<>();
    static volatile java.util.function.Consumer<String> afterWrite;
    private HostStageLog() { }
    static void write(String component, String stage) {
        stages.add(component + " " + stage);
        java.util.function.Consumer<String> callback = afterWrite;
        if (callback != null) callback.accept(component + " " + stage);
    }
}
