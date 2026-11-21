package android.os;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/** Main queue and Choreographer frames are deliberately separate; a frame never drains itself. */
public class Handler {
    private static final ArrayDeque<Runnable> QUEUE = new ArrayDeque<>();
    private static final Set<Runnable> FRAMES = new LinkedHashSet<>(), DELAYED = new LinkedHashSet<>();
    public Handler(Looper looper) { }
    public boolean post(Runnable task) { QUEUE.add(task); return true; }
    public void removeCallbacks(Runnable task) { removeForTest(task); }
    public static void removeForTest(Runnable task) { QUEUE.removeIf(value -> value == task); FRAMES.remove(task); DELAYED.remove(task); }
    public static boolean delayedForTest(Runnable task) { DELAYED.add(task); return true; }
    public static int delayedCountForTest() { return DELAYED.size(); }
    public static void frameForTest(Runnable task) { FRAMES.add(task); }
    public static int frameCountForTest() { return FRAMES.size(); }
    public static void nextFrameForTest() {
        ArrayList<Runnable> tasks = new ArrayList<>(FRAMES); FRAMES.clear();
        for (Runnable task : tasks) task.run();
    }
    public static void drainForTest() { while (!QUEUE.isEmpty()) QUEUE.removeFirst().run(); }
    public static boolean hasPendingForTest() { return !QUEUE.isEmpty(); }
}
