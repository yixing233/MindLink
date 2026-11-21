package hm0;
public final class j1 {
    static { Probe.initialized++; }
    public b0 d;
    public m e;
    public j1() { }
    public static j1 i() { Probe.getterCalls++; throw new AssertionError("No active singleton invocation"); }
    public static b0 u() { Probe.getterCalls++; throw new AssertionError("No active core invocation"); }
    public static m b() { Probe.getterCalls++; throw new AssertionError("No active account invocation"); }
}
