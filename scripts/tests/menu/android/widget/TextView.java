package android.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;

public class TextView extends View {
    private CharSequence text = "";
    private float textSize = 16, letterSpacing, textScale = 1;
    private ColorStateList colors = ColorStateList.valueOf(0xFF333333);
    private Typeface typeface = new Typeface();
    private boolean fontPadding = true, singleLine;
    private int maxLines = Integer.MAX_VALUE;
    private TextUtils.TruncateAt ellipsize;
    public int lastTextSizeUnit = -1;

    public TextView(Context context) { super(context); }
    public void setText(CharSequence value) { text = value; }
    public CharSequence getText() { return text; }
    public void setGravity(int value) {}
    public void setTextSize(float value) { setTextSize(TypedValue.COMPLEX_UNIT_SP, value); }
    public void setTextIsSelectable(boolean value) {}
    public void setTextSize(int unit, float value) {
        lastTextSizeUnit = unit;
        textSize = unit == TypedValue.COMPLEX_UNIT_PX ? value : value * getContext().getResources().getDisplayMetrics().scaledDensity;
    }
    public float getTextSize() { return textSize; }
    public void setTypeface(Typeface value) { typeface = value; }
    public Typeface getTypeface() { return typeface; }
    public void setTextColor(ColorStateList value) { colors = value; }
    public void setTextColor(int value) { colors = ColorStateList.valueOf(value); }
    public ColorStateList getTextColors() { return colors; }
    public void setIncludeFontPadding(boolean value) { fontPadding = value; }
    public boolean getIncludeFontPadding() { return fontPadding; }
    public void setLetterSpacing(float value) { letterSpacing = value; }
    public float getLetterSpacing() { return letterSpacing; }
    public void setTextScaleX(float value) { textScale = value; }
    public float getTextScaleX() { return textScale; }
    public void setSingleLine(boolean value) { singleLine = value; maxLines = value ? 1 : Integer.MAX_VALUE; }
    public boolean isSingleLine() { return singleLine; }
    public void setMaxLines(int value) { maxLines = value; }
    public int getMaxLines() { return maxLines; }
    public void setEllipsize(TextUtils.TruncateAt value) { ellipsize = value; }
    public TextUtils.TruncateAt getEllipsize() { return ellipsize; }
}
