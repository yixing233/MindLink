package wex.ui.core.feature;

public final class BackdropCaptureStateChecks {
    private static int assertions;

    public static void main(String[] args) {
        baseline();
        explicitFailuresAndBackoff();
        successfulCaptureSurvivesFailure();
        environmentsResetBudget();
        contentRevisionResetsFiniteBudget();
        lostCapturePreservesFailureBudget();
        System.out.println("PASS: backdrop state (" + assertions
                + " assertions): baseline attempt suppression, explicit failures, 80/240ms retry,"
                + " bounded source-content budget, safe reuse and invalidation");
    }

    private static void baseline() {
        Object source = new Object(), window = new Object();
        BackdropCaptureState state = new BackdropCaptureState();
        BackdropCaptureState.Key first = key(source, window, 1, 100, 200, 0);
        check(state.decide(first) == BackdropCaptureState.Decision.CAPTURE, "new frame captures");
        check(state.decide(first) == BackdropCaptureState.Decision.SKIP,
                "same attempted key is suppressed until succeeded or explicitly failed");
        state.succeeded(first);
        check(state.decide(first) == BackdropCaptureState.Decision.REUSE, "successful frame reuses");
        BackdropCaptureState.Key redraw = key(source, window, 2, 100, 200, 0);
        check(state.decide(redraw) == BackdropCaptureState.Decision.CAPTURE, "generation redraw captures");
        check(state.canDraw(redraw), "old capture remains drawable in same source window while redraw is attempted");
        BackdropCaptureState.Key resized = key(source, window, 3, 101, 200, 0);
        check(!state.canDraw(resized), "old pixels cannot draw after source size changes");
        BackdropCaptureState.Key switched = key(source, new Object(), 4, 100, 200, 0);
        check(!state.canDraw(switched), "old pixels cannot cross windows");
        state.invalidate();
        check(!state.canDraw(first) && state.decide(first) == BackdropCaptureState.Decision.CAPTURE,
                "invalidation clears capture and failure suppression");
    }

    private static void explicitFailuresAndBackoff() {
        Object source = new Object(), window = new Object();
        BackdropCaptureState state = new BackdropCaptureState();
        BackdropCaptureState.Key first = key(source, window, 1, 100, 200, 0);
        check(state.decide(first, 1000L) == BackdropCaptureState.Decision.CAPTURE, "initial attempt");
        state.failed(first, 1000L);
        check(!state.canDraw(first), "failed initial frame cannot be drawn");
        check(state.retryDelay(first, 1000L) == 80L, "first explicit failure waits 80ms");
        check(state.decide(first, 1079L) == BackdropCaptureState.Decision.SKIP, "same key waits until retry due");
        BackdropCaptureState.Key second = key(source, window, 200, 100, 200, 17);
        check(state.decide(second, 1079L) == BackdropCaptureState.Decision.SKIP,
                "generation and scroll cannot bypass first backoff");
        check(state.decide(second, 1080L) == BackdropCaptureState.Decision.CAPTURE, "retry starts exactly at 80ms");
        state.failed(second, 1080L);
        check(state.retryDelay(second, 1080L) == 240L, "second explicit failure waits 240ms");
        BackdropCaptureState.Key third = key(source, window, 300, 100, 200, 41);
        check(state.decide(third, 1319L) == BackdropCaptureState.Decision.SKIP,
                "new generations do not bypass second backoff");
        check(state.decide(third, 1320L) == BackdropCaptureState.Decision.CAPTURE, "third attempt at 240ms boundary");
        state.failed(third, 1320L);
        check(state.retryDelay(third, 1320L) == -1L, "third failure schedules no retry");
        for (int i = 0; i < 12; i++) {
            check(state.decide(key(source, window, 400 + i, 100, 200, 100 + i), 100000L + i)
                    == BackdropCaptureState.Decision.SKIP, "same environment remains exhausted despite new generation/scroll");
        }
        state.invalidate();
        check(state.decide(first, 100001L) == BackdropCaptureState.Decision.CAPTURE, "invalidation resets exhausted budget");
        state.failed(first, 100001L);
        check(state.retryDelay(first, 100001L) == 80L, "new budget starts with 80ms delay");

        BackdropCaptureState retrySame = new BackdropCaptureState();
        check(retrySame.decide(first, 500L) == BackdropCaptureState.Decision.CAPTURE, "same-key retry initial attempt");
        retrySame.failed(first, 500L);
        check(retrySame.decide(first, 580L) == BackdropCaptureState.Decision.CAPTURE,
                "explicit failed(key, now) permits the exact same key to retry");
        retrySame.succeeded(first);
        check(retrySame.decide(first, 580L) == BackdropCaptureState.Decision.REUSE, "successful retry reuses");
        check(retrySame.retryDelay(first, 580L) == -1L, "success cancels retry eligibility");
        check(retrySame.decide(second, 580L) == BackdropCaptureState.Decision.CAPTURE,
                "success permits immediate next generation");
        retrySame.failed(second, 580L);
        check(retrySame.retryDelay(second, 580L) == 80L, "success resets consecutive failures");
    }

    private static void successfulCaptureSurvivesFailure() {
        Object source = new Object(), window = new Object();
        BackdropCaptureState state = new BackdropCaptureState();
        BackdropCaptureState.Key a = key(source, window, 1, 100, 200, 0);
        check(state.decide(a, 0L) == BackdropCaptureState.Decision.CAPTURE, "capture A");
        state.succeeded(a);
        BackdropCaptureState.Key b = key(source, window, 2, 100, 200, 9);
        check(state.decide(b, 0L) == BackdropCaptureState.Decision.CAPTURE, "capture B");
        state.failed(b, 0L);
        check(state.canDraw(b), "failure of B retains drawable A in same environment");
        check(state.decide(a, 1L) == BackdropCaptureState.Decision.REUSE, "last exact successful key still reuses A");
        check(state.retryDelay(a, 1L) == -1L, "old successful key itself does not need a retry");
        check(state.decide(b, 80L) == BackdropCaptureState.Decision.CAPTURE, "retry B at boundary");
        state.succeeded(b);
        check(state.canDraw(b) && state.decide(b, 80L) == BackdropCaptureState.Decision.REUSE,
                "successful B becomes reusable capture");
    }

    private static void environmentsResetBudget() {
        Object source = new Object(), window = new Object();
        BackdropCaptureState.Key original = key(source, window, 1, 100, 200, 0);
        BackdropCaptureState.Key[] changed = {
            key(new Object(), window, 1, 100, 200, 0),
            key(source, new Object(), 1, 100, 200, 0),
            key(source, window, 1, 101, 200, 0),
            key(source, window, 1, 100, 201, 0),
            new BackdropCaptureState.Key(source, window, 1, 100, 200, 0, 0, 4f, 1f, 0),
            new BackdropCaptureState.Key(source, window, 1, 100, 200, 0, 0, 3f, 1.2f, 0),
            new BackdropCaptureState.Key(source, window, 1, 100, 200, 0, 0, 3f, 1f, 1)
        };
        String[] labels = { "source", "window", "width", "height", "density", "font scale", "layout direction" };
        for (int i = 0; i < changed.length; i++) {
            BackdropCaptureState state = new BackdropCaptureState();
            state.succeeded(original);
            check(!state.canDraw(changed[i]), "capture cannot cross " + labels[i] + " change");
            BackdropCaptureState.Key failed = key(source, window, 2, 100, 200, 0);
            check(state.decide(failed, 0L) == BackdropCaptureState.Decision.CAPTURE, "first old-environment failure");
            state.failed(failed, 0L);
            check(state.decide(failed, 80L) == BackdropCaptureState.Decision.CAPTURE, "second old-environment failure");
            state.failed(failed, 80L);
            check(state.decide(failed, 320L) == BackdropCaptureState.Decision.CAPTURE, "third old-environment failure");
            state.failed(failed, 320L);
            check(state.decide(failed, 10000L) == BackdropCaptureState.Decision.SKIP, "old environment exhausted");
            check(state.decide(changed[i], 10000L) == BackdropCaptureState.Decision.CAPTURE,
                    labels[i] + " change starts an independent failure budget");
            state.failed(changed[i], 10000L);
            check(state.retryDelay(changed[i], 10000L) == 80L, labels[i] + " new budget starts at 80ms");
        }
    }

    private static void contentRevisionResetsFiniteBudget() {
        Object source = new Object(), window = new Object(); BackdropCaptureState state = new BackdropCaptureState();
        BackdropCaptureState.Key a = contentKey(source, window, 1, 10);
        check(state.decide(a, 0L) == BackdropCaptureState.Decision.CAPTURE, "old source first attempt"); state.failed(a, 0L);
        BackdropCaptureState.Key b = contentKey(source, window, 2, 11);
        check(state.decide(b, 79L) == BackdropCaptureState.Decision.SKIP, "actual new content cannot bypass80ms");
        check(state.decide(b, 80L) == BackdropCaptureState.Decision.CAPTURE, "new content gets bounded second attempt"); state.failed(b, 80L);
        BackdropCaptureState.Key c = contentKey(source, window, 3, 12);
        check(state.decide(c, 319L) == BackdropCaptureState.Decision.SKIP && state.retryDelay(c, 80L) == 240L,
                "content every frame cannot reset the second failure backoff");
        check(state.decide(c, 320L) == BackdropCaptureState.Decision.CAPTURE, "third attempt at240ms"); state.failed(c, 320L);
        check(state.retryDelay(c, 320L) == -1L && state.decide(contentKey(source, window, 999, 12), 1000L) == BackdropCaptureState.Decision.SKIP,
                "exhausted content unchanged despite consumer generation");
        BackdropCaptureState.Key next = contentKey(source, window, 4, 13);
        check(state.decide(next, 559L) == BackdropCaptureState.Decision.SKIP && state.retryDelay(next, 320L) == 240L,
                "new source budget still honors last failure timer");
        check(state.decide(next, 560L) == BackdropCaptureState.Decision.CAPTURE, "true source revision replenishes finite exhausted budget");
        state.failed(next, 560L);
        check(state.retryDelay(next, 560L) == 80L, "fresh content budget starts with80ms");
        check(state.decide(next, 640L) == BackdropCaptureState.Decision.CAPTURE, "fresh source second attempt"); state.failed(next, 640L);
        check(state.decide(next, 880L) == BackdropCaptureState.Decision.CAPTURE, "fresh source third attempt"); state.failed(next, 880L);
        check(state.decide(next, 2000L) == BackdropCaptureState.Decision.SKIP && state.retryDelay(next, 2000L) == -1L,
                "same revised source exhausts again without endless retry");
    }

    private static void lostCapturePreservesFailureBudget() {
        Object source = new Object(), window = new Object(); BackdropCaptureState state = new BackdropCaptureState();
        BackdropCaptureState.Key a = key(source, window, 1, 100, 200, 0);
        state.succeeded(a); state.captureUnavailable();
        check(!state.canDraw(a) && state.decide(a, 0L) == BackdropCaptureState.Decision.CAPTURE,
                "lost display list revokes exact success and its success attempt suppression");
        state.succeeded(a);
        BackdropCaptureState.Key b = key(source, window, 2, 100, 200, 0);
        check(state.decide(b, 1000L) == BackdropCaptureState.Decision.CAPTURE, "B replacement before loss");
        state.failed(b, 1000L); state.captureUnavailable();
        check(!state.canDraw(b) && state.retryDelay(b, 1000L) == 80L
                && state.decide(a, 1079L) == BackdropCaptureState.Decision.SKIP,
                "eviction of retained A does not erase B failure or bypass backoff via A key");
        check(state.decide(b, 1080L) == BackdropCaptureState.Decision.CAPTURE, "B second attempt"); state.failed(b, 1080L);
        state.captureUnavailable();
        check(state.retryDelay(b, 1080L) == 240L, "repeated eviction does not reset second timer");
        check(state.decide(b, 1320L) == BackdropCaptureState.Decision.CAPTURE, "B third attempt"); state.failed(b, 1320L);
        state.captureUnavailable();
        check(state.retryExhausted(b) && state.retryDelay(b, 10000L) == -1L
                && state.decide(a, 10000L) == BackdropCaptureState.Decision.SKIP,
                "eviction never replenishes exhausted independent recording budget");
    }

    private static BackdropCaptureState.Key contentKey(Object source, Object window, long generation, long content) {
        return new BackdropCaptureState.Key(source, window, generation, content, 100, 200, 0, 0, 3f, 1f, 0);
    }

    private static BackdropCaptureState.Key key(Object source, Object window, long generation,
                                                 int width, int height, int scroll) {
        return new BackdropCaptureState.Key(source, window, generation, width, height,
                scroll, 0, 3f, 1f, 0);
    }
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
