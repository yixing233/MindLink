package android.os;

/** Explicit host test clock. No sleeping, wall-clock dependence or production scheduling logic. */
public final class SystemClock {
    private static long now;
    public static long uptimeMillis() { return now; }
    public static void setUptimeMillis(long value) { now = value; }
    public static void advance(long value) { if (value < 0) throw new IllegalArgumentException(); now += value; }
}
