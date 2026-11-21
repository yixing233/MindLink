package android.graphics;
public class RenderNode {
    public RecordingCanvas last;
    public int recordings;
    public boolean failRecording;
    public RenderNode(String name) { }
    public void setPosition(int l,int t,int r,int b) { }
    public RecordingCanvas beginRecording(int width,int height) { if (failRecording) throw new IllegalStateException(); recordings++; last = new RecordingCanvas(); last.hardware = true; return last; }
    public void endRecording() { }
    public void discardDisplayList() { }
}
