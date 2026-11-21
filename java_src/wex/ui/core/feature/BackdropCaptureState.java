package wex.ui.core.feature;

/** Capture identity and failure suppression shared by the native-view backdrop sampler. */
final class BackdropCaptureState {
    enum Decision { CAPTURE, REUSE, SKIP }

    static final class Key {
        final Object source, window;
        final long generation, contentGeneration;
        final int width, height, scrollX, scrollY, layoutDirection;
        final float density, fontScale;

        Key(Object source, Object window, long generation, int width, int height,
            int scrollX, int scrollY, float density, float fontScale, int layoutDirection) {
            this(source, window, generation, 0L, width, height, scrollX, scrollY, density, fontScale, layoutDirection);
        }

        Key(Object source, Object window, long generation, long contentGeneration, int width, int height,
            int scrollX, int scrollY, float density, float fontScale, int layoutDirection) {
            this.source = source; this.window = window; this.generation = generation;
            this.contentGeneration = contentGeneration;
            this.width = width; this.height = height; this.scrollX = scrollX; this.scrollY = scrollY;
            this.density = density; this.fontScale = fontScale; this.layoutDirection = layoutDirection;
        }

        boolean sameEnvironment(Key other) {
            return other != null && source == other.source && window == other.window
                    && width == other.width && height == other.height
                    && Float.compare(density, other.density) == 0
                    && Float.compare(fontScale, other.fontScale) == 0
                    && layoutDirection == other.layoutDirection;
        }

        @Override public boolean equals(Object value) {
            if (!(value instanceof Key)) return false;
            Key other = (Key) value;
            return sameEnvironment(other) && generation == other.generation
                    && contentGeneration == other.contentGeneration
                    && scrollX == other.scrollX && scrollY == other.scrollY;
        }

        @Override public int hashCode() {
            int result = System.identityHashCode(source) * 31 + System.identityHashCode(window);
            result = result * 31 + Long.hashCode(generation);
            result = result * 31 + Long.hashCode(contentGeneration);
            result = result * 31 + width; result = result * 31 + height;
            result = result * 31 + scrollX; result = result * 31 + scrollY;
            result = result * 31 + Float.hashCode(density);
            result = result * 31 + Float.hashCode(fontScale);
            return result * 31 + layoutDirection;
        }
    }

    private static final int MAX_FAILURES = 3;
    private Key captured, attempted, failed;
    private int failures;
    private long retryAt, budgetGeneration;

    Decision decide(Key key) { return decide(key, 0L); }

    private void updateBudget(Key key) {
        if (failed == null) return;
        if (!failed.sameEnvironment(key)) {
            failed = null; failures = 0; retryAt = 0L;
        } else if (failures >= MAX_FAILURES && budgetGeneration != key.contentGeneration) {
            // Only a verified host-content revision replenishes the finite budget.
            // Keep backoff: content arriving every frame must not bypass the timer.
            failures = 0;
            budgetGeneration = key.contentGeneration;
        }
    }

    Decision decide(Key key, long now) {
        if (key.equals(captured)) return Decision.REUSE;
        updateBudget(key);
        if (failed != null && (failures >= MAX_FAILURES || now < retryAt)) return Decision.SKIP;
        if (key.equals(attempted) && !key.equals(failed)) return Decision.SKIP;
        attempted = key;
        return Decision.CAPTURE;
    }

    void succeeded(Key key) {
        attempted = captured = key;
        failed = null; failures = 0; retryAt = 0L;
    }

    void failed(Key key, long now) {
        if (failed == null || !failed.sameEnvironment(key)) failures = 0;
        attempted = failed = key;
        budgetGeneration = key.contentGeneration;
        failures++;
        retryAt = now + (failures == 1 ? 80L : 240L);
    }

    long retryDelay(Key key, long now) {
        updateBudget(key);
        if (failed == null || !failed.sameEnvironment(key) || failures >= MAX_FAILURES
                || key.equals(captured)) return -1L;
        return Math.max(0L, retryAt - now);
    }

    boolean canDraw(Key key) { return captured != null && captured.sameEnvironment(key); }

    boolean retryExhausted(Key key) { updateBudget(key); return failures >= MAX_FAILURES; }

    /** RenderNode eviction revokes success, never its independent failure budget/backoff. */
    void captureUnavailable() {
        if (captured != null && captured.equals(attempted)) attempted = null;
        captured = null;
    }

    void invalidate() {
        captured = attempted = failed = null;
        failures = 0; retryAt = 0L;
    }
}
