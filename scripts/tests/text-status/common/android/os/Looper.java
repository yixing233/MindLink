package android.os;

public final class Looper {
    private static final Looper MAIN = new Looper();
    private static final Thread MAIN_THREAD = Thread.currentThread();
    private static final ThreadLocal<Boolean> ON_MAIN = ThreadLocal.withInitial(() -> Thread.currentThread() == MAIN_THREAD);
    public static Looper getMainLooper() { return MAIN; }
    public static Looper myLooper() { return ON_MAIN.get() ? MAIN : null; }
    public static void runOffMain(Runnable task) {
        boolean old = ON_MAIN.get(); ON_MAIN.set(false);
        try { task.run(); } finally { ON_MAIN.set(old); }
    }
}
