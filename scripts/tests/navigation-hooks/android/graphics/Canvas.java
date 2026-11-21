package android.graphics;

public class Canvas {
    public Canvas() { }
    public Canvas(Bitmap bitmap) { }
    public int save() { return 0; }
    public void translate(float x,float y) { }
    public void scale(float x,float y) { }
    public void scale(float x,float y,float cx,float cy) { }
    public void restoreToCount(int value) { }
    public boolean hardware, failNodeDraw;
    public final java.util.List<Integer> pathColors = new java.util.ArrayList<>();
    public final java.util.List<String> texts = new java.util.ArrayList<>();
    public boolean isHardwareAccelerated() { return hardware; }
    public void drawRenderNode(RenderNode node) { if (failNodeDraw) throw new IllegalStateException(); if (node.last != null) { pathColors.addAll(node.last.pathColors); texts.addAll(node.last.texts); } }
    public void drawBitmap(Bitmap bitmap,Rect source,RectF dest,Paint paint) { }
    public void drawRoundRect(RectF rect,float rx,float ry,Paint paint) { }
    public void drawCircle(float x,float y,float radius,Paint paint) { }
    public void drawText(String text,float x,float y,Paint paint) { texts.add(text); }
    public void drawPaint(Paint paint) { }
    public void drawPath(Path path,Paint paint) { pathColors.add(paint.getColor()); }
    public void drawColor(int color) {}
    public void drawRect(float left,float top,float right,float bottom,Paint paint) {}
}
