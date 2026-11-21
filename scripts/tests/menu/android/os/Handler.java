package android.os;

public class Handler {
    private static final java.util.concurrent.ConcurrentLinkedQueue<Runnable> QUEUE = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final java.util.Set<Runnable> DELAYED = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    public static boolean delayedForTest(Runnable task) { DELAYED.add(task);return true; }
    public static void removeForTest(Runnable task) { QUEUE.remove(task);DELAYED.remove(task); }
    public static int delayedCountForTest() { return DELAYED.size(); }
    public Handler(Looper looper) {}
    public boolean post(Runnable task) { QUEUE.add(task); return true; }
    public static void drainForTest() { for (Runnable task; (task = QUEUE.poll()) != null;) task.run(); }
    public static boolean hasPendingForTest() { return !QUEUE.isEmpty(); }
}
