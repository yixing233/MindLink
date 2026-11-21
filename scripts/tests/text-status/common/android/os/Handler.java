package android.os;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class Handler {
    private static final Queue<Runnable> MAIN = new ConcurrentLinkedQueue<>();
    private static final Queue<Runnable> DELAYED = new ConcurrentLinkedQueue<>();
    public static volatile boolean rejectPosts;
    public static volatile Throwable postFailure;
    public Handler(Looper ignored) { }
    public boolean post(Runnable task) {
        if (postFailure != null) Handler.<RuntimeException>raise(postFailure);
        if (rejectPosts) return false;
        MAIN.add(task); return true;
    }
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void raise(Throwable failure) throws E { throw (E) failure; }
    public boolean postDelayed(Runnable task, long delay) { DELAYED.add(task); return true; }
    public static int pending() { return MAIN.size(); }
    public static void runOne() {
        Runnable task = MAIN.poll();
        if (task == null) throw new AssertionError("No queued MAIN task");
        if (Looper.myLooper() != Looper.getMainLooper()) throw new AssertionError("MAIN task on worker");
        task.run();
    }
    public static void drain() {
        int limit = 100;
        while (!MAIN.isEmpty()) {
            if (--limit < 0) throw new AssertionError("Unbounded MAIN queue");
            runOne();
        }
    }
}
