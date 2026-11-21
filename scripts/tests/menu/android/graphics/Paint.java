package android.graphics;

public class Paint {
    public static final int ANTI_ALIAS_FLAG=1;
    public enum Style { FILL, STROKE }
    public enum Cap { ROUND }
    public enum Join { ROUND }
    public Paint(int flags) {}
    public void setColor(int value) {}
    public void setAlpha(int value) {}
    public void setColorFilter(ColorFilter filter) {}
    public void setStyle(Style value) {}
    public void setStrokeCap(Cap value) {}
    public void setStrokeJoin(Join value) {}
    public void setStrokeWidth(float value) {}
    public void setShader(Shader shader) {}
}
