package android.os;

import java.util.ArrayDeque;

public final class Handler {
    private static final ArrayDeque<Runnable> QUEUE = new ArrayDeque<>();
    public Handler(Looper looper) { }
    public boolean post(Runnable task) { QUEUE.add(task); return true; }
    public boolean postDelayed(Runnable task, long delay) { return post(task); }
    public static void drainForTest() {
        int limit = 100;
        while (!QUEUE.isEmpty()) {
            if (--limit < 0) throw new AssertionError("recursive notify/refresh queue");
            QUEUE.removeFirst().run();
        }
    }
    public static void stepForTest() { if (!QUEUE.isEmpty()) QUEUE.removeFirst().run(); }
    public static int pendingForTest() { return QUEUE.size(); }
}
