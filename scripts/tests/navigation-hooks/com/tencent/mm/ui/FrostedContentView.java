package com.tencent.mm.ui;

public final class FrostedContentView extends android.widget.FrameLayout {
    public int bottom=120,drawnBottom=-1,writes;
    public boolean failConfiguration;
    public FrostedContentView(android.content.Context context){super(context);}
    public int getBottomBlurAreaHeight(){return bottom;}
    public void setBottomBlurAreaHeight(int value){bottom=value;writes++;}
    public void a(boolean enabled,int height,float offset){bottom=height-(int)offset;if(failConfiguration)throw new IllegalStateException("host config failed");}
    public void dispatchDraw(android.graphics.Canvas canvas){drawnBottom=bottom;}
}
