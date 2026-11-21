package hm0;
public final class f implements Runnable {
    public final m d;
    public f(m account) { d = account; }
    public void run() { throw new AssertionError("Ready task must be passive"); }
}
