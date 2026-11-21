package wex.ui.core.feature;

/** 异步气泡结果只能用于仍开启、仍附着且没有被更新的那一代视图。 */
final class BubbleGeneration {
    private long version;
    private int mode;
    private boolean enabled;

    long next(boolean enabled, int mode) {
        this.enabled = enabled;
        this.mode = mode;
        return ++version;
    }

    void cancel() {
        enabled = false;
        version++;
    }

    boolean accepts(long expected, int expectedMode, boolean attached) {
        return enabled && attached && version == expected && mode == expectedMode;
    }
}
