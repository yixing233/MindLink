package android.graphics;

public class Paint {
    public static final int ANTI_ALIAS_FLAG=1;
    public enum Style { FILL, STROKE }
    public enum Cap { ROUND }
    public enum Join { ROUND }
    public enum Align { CENTER }
    public static class FontMetrics { public float ascent, descent; }
    public Paint() { }
    public Paint(int flags) {}
    public void setTextAlign(Align value) { }
    public void setFilterBitmap(boolean value) { }
    public void setTypeface(Typeface value) { }
    public void setTextSize(float value) { }
    public float measureText(String value) { return value.length()*10; }
    public FontMetrics getFontMetrics() { return new FontMetrics(); }
    public int getAlpha() { return 255; }
    public ColorFilter getColorFilter() { return null; }
    private int color;
    public int getColor() { return color; }
    public void setColor(int value) { color = value; }
    public void setAlpha(int value) {}
    public void setColorFilter(ColorFilter filter) {}
    public void setStyle(Style value) {}
    public void setStrokeCap(Cap value) {}
    public void setStrokeJoin(Join value) {}
    public void setStrokeWidth(float value) {}
    public void setShader(Shader shader) {}
}
