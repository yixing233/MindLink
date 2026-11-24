package android.os;

public final class CancellationSignal {
    public boolean cancelled;
    public void cancel() { cancelled = true; }
}
