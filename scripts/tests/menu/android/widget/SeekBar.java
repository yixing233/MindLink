package android.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;

public class SeekBar extends View {
    private ColorStateList progress, secondary, background, thumb, tick;
    private int value;
    public SeekBar(Context context) { super(context); }
    public void setProgressTintList(ColorStateList value) { progress = value; }
    public ColorStateList getProgressTintList() { return progress; }
    public void setSecondaryProgressTintList(ColorStateList value) { secondary = value; }
    public ColorStateList getSecondaryProgressTintList() { return secondary; }
    public void setProgressBackgroundTintList(ColorStateList value) { background = value; }
    public ColorStateList getProgressBackgroundTintList() { return background; }
    public void setThumbTintList(ColorStateList value) { thumb = value; }
    public ColorStateList getThumbTintList() { return thumb; }
    public void setTickMarkTintList(ColorStateList value) { tick = value; }
    public ColorStateList getTickMarkTintList() { return tick; }
    public void setProgress(int value) { this.value = value; }
    public int getProgress() { return value; }
}
