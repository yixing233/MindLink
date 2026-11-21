package wex.ui.core.feature;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/** Original green material gradient, not a backdrop blur or a MIUI-private blend implementation. */
final class ModuleAboutBackdrop extends View implements Runnable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean dark;
    private boolean resumed,running;
    private long previous;
    private float phase;
    ModuleAboutBackdrop(Context context){super(context);dark=SettingsTheme.isDark(context);setTag("mdl_about_material");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void resume(boolean value){resumed=value;sync();}
    boolean running(){return running;}
    private void sync(){
        boolean wanted=resumed&&isAttachedToWindow()&&getWindowVisibility()==VISIBLE&&android.animation.ValueAnimator.areAnimatorsEnabled();
        if(wanted==running)return;running=wanted;removeCallbacks(this);previous=0;if(running)postDelayed(this,33);
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();sync();}
    @Override protected void onDetachedFromWindow(){running=false;previous=0;removeCallbacks(this);super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);sync();}
    @Override public void run(){
        if(!running)return;long now=SystemClock.uptimeMillis();if(previous!=0)phase+=(Math.min(100,now-previous)/1000f)*.12f;previous=now;
        if(phase>Math.PI*2)phase-=(float)(Math.PI*2);invalidate();postDelayed(this,33);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.drawColor(ModulePreferenceViews.page(getContext()));
        if(getWidth()<=0||getHeight()<=0)return;
        int[] colors=dark?new int[]{0x99385143,0x99413F58,0x99444733,0x99344349}:new int[]{0xBBD9EBD6,0xBBE3DDEF,0xBBE8EACB,0xBBD4E9E5};
        float[] x={.12f,.88f,.78f,.18f},y={.12f,.35f,.76f,.85f};float radius=Math.max(getWidth(),getHeight())*.7f;
        for(int i=0;i<4;i++){
            float cx=getWidth()*(x[i]+.08f*(float)Math.sin(phase+i*1.7f));float cy=getHeight()*(y[i]+.06f*(float)Math.cos(phase+i));
            paint.setShader(new RadialGradient(cx,cy,radius,colors[i],colors[i]&0xFFFFFF,Shader.TileMode.CLAMP));canvas.drawRect(0,0,getWidth(),getHeight(),paint);
        }
        paint.setShader(null);
    }
}
