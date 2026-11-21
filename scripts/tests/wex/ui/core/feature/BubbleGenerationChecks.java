package wex.ui.core.feature;

public final class BubbleGenerationChecks {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        BubbleGeneration state = new BubbleGeneration();
        long first = state.next(true, 0);
        check(state.accepts(first, 0, true), "mode zero rendered");
        check(!state.accepts(first, 0, false), "detached view rejects result");
        long second = state.next(true, 0);
        check(!state.accepts(first, 0, true), "host overwrite invalidates old result");
        check(state.accepts(second, 0, true), "host overwrite can redraw");
        long image = state.next(true, 1);
        check(!state.accepts(second, 0, true), "mode change invalidates blur");
        check(state.accepts(image, 1, true), "image result valid");
        long transparent = state.next(true, 2);
        check(!state.accepts(image, 1, true), "image cannot overwrite transparent mode");
        check(state.accepts(transparent, 2, true), "transparent result valid");
        state.cancel();
        check(!state.accepts(transparent, 2, true), "cancel on detach/disable");
        long disabled = state.next(false, 0);
        check(!state.accepts(disabled, 0, true), "disabled rejects all work");
        long resumed = state.next(true, 0);
        check(state.accepts(resumed, 0, true), "re-enable works");
        System.out.println("PASS: bubble mode changes, host overwrite, stale results, detach, disable and re-enable");
    }
}
