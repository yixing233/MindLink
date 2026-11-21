package android.graphics;

import java.util.ArrayList;
import java.util.List;

/** Host command recorder only, not a rasterizer or a hardware/GPU emulator. */
public class Canvas {
    public boolean hardwareAccelerated = true;
    public final List<String> operations = new ArrayList<>();
    public final List<RenderNode> renderedNodes = new ArrayList<>();
    public final List<List<String>> renderedContents = new ArrayList<>();
    public final List<Integer> rectColors = new ArrayList<>();
    private int saveCount;
    public boolean isHardwareAccelerated() { return hardwareAccelerated; }
    public int save() { return ++saveCount; }
    public void restoreToCount(int count) { saveCount = count - 1; }
    public void translate(float x, float y) { operations.add("translate:" + x + "," + y); }
    public void clipPath(Path path) { operations.add("clip"); }
    public boolean clipRect(int left, int top, int right, int bottom) {
        operations.add("clipRect:" + left + "," + top + "," + right + "," + bottom); return true;
    }
    public void drawRenderNode(RenderNode node) {
        renderedNodes.add(node);
        renderedContents.add(node.content());
        operations.add("node:" + node.id + ":" + node.content());
    }
    public void drawRect(RectF rect, Paint paint) { rectColors.add(paint.getColor()); operations.add("rect:" + paint.getColor()); }
    public void drawRoundRect(RectF rect, float x, float y, Paint paint) { rectColors.add(paint.getColor()); }
    public void mark(String value) { operations.add("content:" + value); }
}
