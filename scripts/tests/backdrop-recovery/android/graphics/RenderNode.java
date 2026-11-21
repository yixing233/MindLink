package android.graphics;

import java.util.ArrayList;
import java.util.List;

/** Destructive beginRecording semantics deliberately expose unsafe single-node rerecording. */
public final class RenderNode {
    public static final List<RenderNode> instances = new ArrayList<>();
    public final String name;
    public final int id;
    public int left, top, right, bottom;
    public int beginCalls, endCalls, discardCalls, effectAttempts, successfulEffectSets;
    public int failBegins, failEnds, emptyEnds, failEffects;
    public RenderEffect renderEffect;
    private RecordingCanvas recording;
    private List<String> displayList = List.of();
    private boolean hasDisplayList;
    public RenderNode(String name) { this.name = name; id = instances.size() + 1; instances.add(this); }
    public void setPosition(int left, int top, int right, int bottom) {
        this.left = left; this.top = top; this.right = right; this.bottom = bottom;
    }
    public RecordingCanvas beginRecording(int width, int height) {
        beginCalls++;
        hasDisplayList = false; displayList = List.of();
        if (failBegins > 0) { failBegins--; throw new IllegalStateException("injected beginRecording failure"); }
        if (recording != null) throw new AssertionError("begin while recording");
        recording = new RecordingCanvas();
        return recording;
    }
    public void endRecording() {
        endCalls++;
        if (recording == null) throw new AssertionError("end without begin");
        displayList = List.copyOf(recording.operations);
        recording = null;
        hasDisplayList = true;
        // Publish a partial list before throwing: production must still discard, never swap it.
        if (failEnds > 0) { failEnds--; throw new IllegalStateException("injected endRecording failure"); }
        if (emptyEnds > 0) { emptyEnds--; hasDisplayList = false; displayList = List.of(); }
    }
    public boolean hasDisplayList() { return hasDisplayList; }
    public List<String> content() { return displayList; }
    public void discardDisplayList() { discardCalls++; hasDisplayList = false; displayList = List.of(); }
    public void setRenderEffect(RenderEffect value) {
        effectAttempts++;
        if (failEffects > 0) { failEffects--; throw new IllegalStateException("injected effect installation failure"); }
        renderEffect = value; successfulEffectSets++;
    }
    public static void reset() { instances.clear(); }
}
