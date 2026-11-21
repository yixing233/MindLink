package wex.ui.core.feature;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Desktop-only preference language, based on HyperChanger SettingsHome and Group layout. */
final class ModulePreferenceViews {
    private ModulePreferenceViews(){}
    static int dp(Context context,float value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    static int page(Context context){return SettingsTheme.isDark(context)?0xFF0E100F:0xFFF7F7F7;}
    static int panel(Context context){return SettingsTheme.isDark(context)?0xFF242824:0xFFFFFFFF;}
    static TextView text(Context context,String value,float size,boolean bold,int color){
        TextView text=new TextView(context);text.setText(value);text.setTextSize(size);text.setTextColor(color);
        text.setIncludeFontPadding(false);if(bold)text.setTypeface(Typeface.DEFAULT_BOLD);return text;
    }
    static LinearLayout column(Context context){LinearLayout view=new LinearLayout(context);view.setOrientation(LinearLayout.VERTICAL);return view;}
    static LinearLayout.LayoutParams block(Context context){LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(context,12);return params;}
    static LinearLayout panel(Context context,boolean translucent){
        LinearLayout view=column(context);int color=panel(context);
        if(translucent)color=(color&0xFFFFFF)|(SettingsTheme.isDark(context)?0x99000000:0xA6000000);
        view.setBackground(new Surface(color,dp(context,22.5f)));view.setTag("mdl_preference_panel");return view;
    }
    static LinearLayout group(LinearLayout root,Context context,String title,boolean translucent){
        LinearLayout group=column(context);
        TextView heading=text(context,title,14,false,SettingsTheme.sub(context));heading.setPadding(dp(context,12),dp(context,4),dp(context,12),dp(context,4));
        group.addView(heading,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout panel=panel(context,translucent);group.addView(panel,new LinearLayout.LayoutParams(-1,-2));root.addView(group,block(context));return panel;
    }
    static View row(LinearLayout panel,Context context,String key,String title,String summary,Runnable action){
        LinearLayout row=new LinearLayout(context);row.setGravity(Gravity.CENTER_VERTICAL);row.setOrientation(LinearLayout.HORIZONTAL);
        row.setTag("mdl_row_"+key);row.setMinimumHeight(dp(context,56));row.setPadding(dp(context,16),dp(context,16),dp(context,16),dp(context,16));
        LinearLayout labels=column(context);labels.addView(text(context,title,17,false,SettingsTheme.ink(context)),new LinearLayout.LayoutParams(-1,-2));
        if(summary!=null&&!summary.isEmpty()){
            TextView detail=text(context,summary,14,false,SettingsTheme.sub(context));detail.setPadding(0,dp(context,4),0,0);labels.addView(detail,new LinearLayout.LayoutParams(-1,-2));
        }
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        if(action!=null){
            View arrow=new View(context);arrow.setBackground(new Chevron(SettingsTheme.sub(context),false));
            LinearLayout.LayoutParams icon=new LinearLayout.LayoutParams(dp(context,10),dp(context,16));icon.setMarginStart(dp(context,16));row.addView(arrow,icon);
            row.setClickable(true);row.setFocusable(true);row.setOnClickListener(view->action.run());
            row.setForeground(feedback(context));
        }
        panel.addView(row,new LinearLayout.LayoutParams(-1,-2));return row;
    }
    static void info(LinearLayout panel,Context context,String label,String value){
        LinearLayout row=column(context);row.setPadding(dp(context,16),dp(context,12),dp(context,16),dp(context,12));row.setTag("mdl_info_"+label);
        row.addView(text(context,value,17,false,SettingsTheme.ink(context)),new LinearLayout.LayoutParams(-1,-2));
        TextView caption=text(context,label,14,false,SettingsTheme.sub(context));caption.setPadding(0,dp(context,3),0,0);row.addView(caption,new LinearLayout.LayoutParams(-1,-2));
        panel.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }
    static FrameLayout back(Context context,Runnable action){
        FrameLayout button=new FrameLayout(context);button.setTag("mdl_back");button.setContentDescription("返回");button.setClickable(true);button.setFocusable(true);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf((SettingsTheme.accent(context)&0xFFFFFF)|0x22000000),null,SettingsTheme.round(0xFFFFFFFF,dp(context,23))));
        View icon=new View(context);icon.setBackground(new Chevron(SettingsTheme.ink(context),true));
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(dp(context,12),dp(context,22));params.gravity=Gravity.CENTER;button.addView(icon,params);
        button.setOnClickListener(view->action.run());return button;
    }
    static Drawable feedback(Context context){return new RippleDrawable(ColorStateList.valueOf((SettingsTheme.accent(context)&0xFFFFFF)|0x1A000000),null,new Surface(0xFFFFFFFF,dp(context,14)));}

    /** Superelliptic corners join straight edges tangentially; no host resources or R class needed. */
    static final class Surface extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path=new Path();
        private final float radius;
        Surface(int color,float radius){paint.setColor(color);this.radius=radius;}
        @Override protected void onBoundsChange(Rect bounds){
            path.reset();float r=Math.min(radius,Math.min(bounds.width(),bounds.height())/2f);
            float[] cx={bounds.right-r,bounds.right-r,bounds.left+r,bounds.left+r};
            float[] cy={bounds.top+r,bounds.bottom-r,bounds.bottom-r,bounds.top+r};
            for(int corner=0;corner<4;corner++)for(int step=0;step<=24;step++){
                double angle=-Math.PI/2+corner*Math.PI/2+step*Math.PI/48;
                double x=Math.cos(angle),y=Math.sin(angle);
                float px=cx[corner]+r*(float)Math.copySign(Math.sqrt(Math.abs(x)),x);
                float py=cy[corner]+r*(float)Math.copySign(Math.sqrt(Math.abs(y)),y);
                if(corner==0&&step==0)path.moveTo(px,py);else path.lineTo(px,py);
            }
            path.close();
        }
        @Override public void draw(Canvas canvas){canvas.drawPath(path,paint);}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private static final class Chevron extends Drawable {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final Path path=new Path();final boolean back;
        Chevron(int color,boolean back){paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);this.back=back;}
        @Override public void draw(Canvas canvas){Rect b=getBounds();float inset=b.width()*.12f;paint.setStrokeWidth(Math.max(1,b.width()*.17f));path.reset();float edge=back?b.right-inset:b.left+inset,tip=back?b.left+inset:b.right-inset;path.moveTo(edge,b.top+inset);path.lineTo(tip,b.exactCenterY());path.lineTo(edge,b.bottom-inset);canvas.drawPath(path,paint);}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
