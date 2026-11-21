package wex.ui.core.feature;

/** 固定栏挂载只在已识别槽位内执行，失败时恢复本次占位。 */
final class FilterBarMount {
    interface Target {
        boolean isMounted();
        boolean prepare();
        void install();
        void restore();
        void failed(Throwable error);
    }

    private final Target target;

    FilterBarMount(Target target) {
        this.target = target;
    }

    boolean mount() {
        if (target.isMounted()) {
            return true;
        }
        boolean prepared = false;
        try {
            if (!target.prepare()) {
                return false;
            }
            prepared = true;
            target.install();
            if (target.isMounted()) {
                return true;
            }
        } catch (Throwable error) {
            target.failed(error);
        }
        if (prepared) {
            unmount();
        }
        return false;
    }

    void unmount() {
        try {
            target.restore();
        } catch (Throwable error) {
            target.failed(error);
        }
    }
}
