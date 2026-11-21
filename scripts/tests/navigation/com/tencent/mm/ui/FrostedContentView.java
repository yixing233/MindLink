package com.tencent.mm.ui;

import android.content.Context;
import android.widget.FrameLayout;

/** Host property contract, with observable setter callbacks and direct-field configuration. */
public class FrostedContentView extends FrameLayout {
    public int bottom, top = 48, radius = 50, color = 0x55444444, writes;
    public boolean enabled = true, failWrite;
    public java.util.function.IntConsumer afterSet;
    public FrostedContentView(Context context) { super(context); }
    public int getBottomBlurAreaHeight() { return bottom; }
    public int getTopBlurAreaHeight() { return top; }
    public int getBlurRadius() { return radius; }
    public int getFrostedColor() { return color; }
    public boolean getFrostedEnabled() { return enabled; }
    public void setBottomBlurAreaHeight(int value) {
        if (failWrite) throw new IllegalStateException("test setter failure");
        writes++; bottom = value;
        if (afterSet != null) afterSet.accept(value);
    }
    public void configure(int value) { bottom = value; }
}
