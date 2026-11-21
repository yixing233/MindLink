"""Compile the real floating-footer feature with isolated Android/Xposed host doubles.

No APK/build/device access; all generated classes and the pre-fix replay live in a
TemporaryDirectory. Shared test fixtures and regression.py are not modified.
"""
import argparse
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
FEATURE = ROOT / "java_src/wex/ui/core/feature"

SOURCES = {
"android/content/Context.java": r'''package android.content;
public class Context { private final android.content.res.Resources resources=new android.content.res.Resources(); public android.content.res.Resources getResources(){return resources;} }
''',
"android/content/ContextWrapper.java": r'''package android.content;
public class ContextWrapper extends Context { private final Context base; public ContextWrapper(Context base){this.base=base;} public Context getBaseContext(){return base;} }
''',
"android/content/res/Resources.java": r'''package android.content.res;
public class Resources { private final android.util.DisplayMetrics metrics=new android.util.DisplayMetrics(); public android.util.DisplayMetrics getDisplayMetrics(){return metrics;} }
''',
"android/util/DisplayMetrics.java": r'''package android.util; public class DisplayMetrics { public float density=1f; }''',
    "android/graphics/Rect.java": r'''package android.graphics; public class Rect { public int left,top,right,bottom; public Rect(){} public Rect(Rect other){set(other);} public void set(Rect other){left=other.left;top=other.top;right=other.right;bottom=other.bottom;} public void offset(int dx,int dy){left+=dx;right+=dx;top+=dy;bottom+=dy;} public void setEmpty(){left=top=right=bottom=0;} public int width(){return right-left;} public int height(){return bottom-top;} public boolean isEmpty(){return right<=left||bottom<=top;} }''',
"android/graphics/Insets.java": r'''package android.graphics; public class Insets { public int left,top,right,bottom; public static final Insets NONE=new Insets(0,0,0,0); public Insets(int b){bottom=b;} public Insets(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;} }''',
"android/graphics/Paint.java": r'''package android.graphics; public class Paint { private int alpha=255; public int getAlpha(){return alpha;} public void setAlpha(int a){alpha=a;} }''',
"android/graphics/Outline.java": r'''package android.graphics;
public class Outline { public int left,top,right,bottom; public float radius; public void setRoundRect(int l,int t,int r,int b,float radius){left=l;top=t;right=r;bottom=b;this.radius=radius;}public void setEmpty(){left=top=right=bottom=0;} }
''',
"android/graphics/drawable/Drawable.java": r'''package android.graphics.drawable; public class Drawable { }''',
"android/graphics/drawable/GradientDrawable.java": r'''package android.graphics.drawable;
public class GradientDrawable extends Drawable { public int color,changes; public float radius; public void setColor(int c){color=c;changes++;} public void setCornerRadius(float r){radius=r;changes++;} }
''',
"android/os/Looper.java": r'''package android.os; public class Looper { static final Looper MAIN=new Looper(); public static boolean main=true; public static Looper getMainLooper(){return MAIN;} public static Looper myLooper(){return main?MAIN:null;} }''',
"android/os/Handler.java": r'''package android.os;
import java.util.*;
public class Handler {
 static final ArrayDeque<Runnable> immediate=new ArrayDeque<>(), delayed=new ArrayDeque<>();
 public Handler(Looper l){} public boolean post(Runnable r){immediate.add(r);return true;}
 public boolean postDelayed(Runnable r,long delay){delayed.add(r);return true;}
 public void removeCallbacks(Runnable r){immediate.removeIf(v->v==r);delayed.removeIf(v->v==r);}
 public static int pending(){return immediate.size()+delayed.size();}
 public static void drain(){int cap=100;while(!immediate.isEmpty()){if(--cap<0)throw new AssertionError("unbounded immediate loop");immediate.remove().run();}}
 public static void tick(){int size=delayed.size();for(int i=0;i<size;i++)immediate.add(delayed.remove());drain();}
 public static void finish(){int cap=20;while(pending()>0){if(--cap<0)throw new AssertionError("unbounded retry");drain();tick();}}
}
''',
"android/view/ViewParent.java": r'''package android.view; public interface ViewParent {}''',
"android/view/ViewTreeObserver.java": r'''package android.view;
import java.util.*;
public class ViewTreeObserver {
 public interface OnPreDrawListener {boolean onPreDraw();}
 public final List<OnPreDrawListener> listeners=new ArrayList<>(); public boolean alive=true;
 public boolean isAlive(){return alive;} public void addOnPreDrawListener(OnPreDrawListener l){listeners.add(l);}
 public void removeOnPreDrawListener(OnPreDrawListener l){listeners.remove(l);}
 public void draw(){for(OnPreDrawListener l:new ArrayList<>(listeners))l.onPreDraw();}
}
''',
"android/view/ViewOutlineProvider.java": r'''package android.view;
public abstract class ViewOutlineProvider {public abstract void getOutline(View view,android.graphics.Outline outline);}
''',
"android/view/WindowInsets.java": r'''package android.view;
public class WindowInsets {public int nav,ime,tap; public android.graphics.Insets getInsets(int type){return new android.graphics.Insets(type==1?nav:type==4?tap:ime);} public static class Type {public static int navigationBars(){return 1;}public static int ime(){return 2;}public static int tappableElement(){return 4;}} public static final class Builder {private int nav,ime,tap;public Builder(WindowInsets source){nav=source.nav;ime=source.ime;tap=source.tap;}public Builder setInsets(int type,android.graphics.Insets value){if(type==1)nav=value.bottom;else if(type==2)ime=value.bottom;else if(type==4)tap=value.bottom;return this;}public WindowInsets build(){WindowInsets copy=new WindowInsets();copy.nav=nav;copy.ime=ime;copy.tap=tap;return copy;}}}
''',
"android/view/View.java": r'''package android.view;
import android.content.Context; import android.graphics.*; import android.graphics.drawable.Drawable; import java.util.*;
public class View implements ViewParent {
 public static final int VISIBLE=0,INVISIBLE=4,GONE=8,NO_ID=-1; private static int nextId=100;
 private final Context context; ViewParent parent; ViewGroup.LayoutParams params; private boolean attached;
 public final List<OnAttachStateChangeListener> attachListeners=new ArrayList<>(); private final ViewTreeObserver observer=new ViewTreeObserver();
  private Drawable background; private ViewOutlineProvider outline; private boolean clip; private float elevation,translationY,alpha=1;
 private int visibility,id=NO_ID,l,t,r=400,b=60,pl,pt,pr,pb,measuredWidth,measuredHeight; public int layouts,outlines,backgroundWrites; public WindowInsets insets;
 public View(Context c){context=c;} public Context getContext(){return context;} public android.content.res.Resources getResources(){return context.getResources();}
 public ViewParent getParent(){return parent;} public View getRootView(){View v=this;while(v.parent instanceof View)v=(View)v.parent;return v;}
 public ViewGroup.LayoutParams getLayoutParams(){return params;} public void setLayoutParams(ViewGroup.LayoutParams p){params=p;layouts++;}
 public interface OnAttachStateChangeListener {void onViewAttachedToWindow(View view);void onViewDetachedFromWindow(View view);}
 public void addOnAttachStateChangeListener(OnAttachStateChangeListener l){attachListeners.add(l);} public void removeOnAttachStateChangeListener(OnAttachStateChangeListener l){attachListeners.remove(l);}
 protected void onAttachedToWindow(){} protected void onDetachedFromWindow(){}
 public void attach(){attached=true;onAttachedToWindow();for(OnAttachStateChangeListener l:new ArrayList<>(attachListeners))l.onViewAttachedToWindow(this);if(this instanceof ViewGroup)for(View v:new ArrayList<>(((ViewGroup)this).children))v.attach();}
 public void detach(){attached=false;for(OnAttachStateChangeListener l:new ArrayList<>(attachListeners))l.onViewDetachedFromWindow(this);if(this instanceof ViewGroup)for(View v:new ArrayList<>(((ViewGroup)this).children))v.detach();onDetachedFromWindow();}
 public boolean isAttachedToWindow(){return attached;} public ViewTreeObserver getViewTreeObserver(){return observer;}
 public boolean isShown(){if(!attached)return false;for(View v=this;v!=null;v=v.parent instanceof View?(View)v.parent:null)if(v.visibility!=VISIBLE)return false;return true;}
 public int getVisibility(){return visibility;} public void setVisibility(int value){visibility=value;} public float getAlpha(){return alpha;}public void setAlpha(float value){alpha=value;}
 public Drawable getBackground(){return background;} public void setBackground(Drawable value){background=value;backgroundWrites++;}
 public ViewOutlineProvider getOutlineProvider(){return outline;} public void setOutlineProvider(ViewOutlineProvider value){outline=value;}
 public boolean getClipToOutline(){return clip;} public void setClipToOutline(boolean value){clip=value;}
 public float getElevation(){return elevation;} public void setElevation(float value){elevation=value;} public void invalidateOutline(){outlines++;}
  public float getTranslationY(){return translationY;} public void setTranslationY(float value){translationY=value;}
  private Rect clipBounds;public Rect getClipBounds(){return clipBounds==null?null:new Rect(clipBounds);}public void setClipBounds(Rect value){clipBounds=value==null?null:new Rect(value);}
 private boolean focusable,focusableInTouchMode,laidOut;public boolean isFocusable(){return focusable;}public void setFocusable(boolean value){focusable=value;}public boolean isFocusableInTouchMode(){return focusableInTouchMode;}public void setFocusableInTouchMode(boolean value){focusableInTouchMode=value;}public boolean isLaidOut(){return laidOut;}
 public void layout(int left,int top,int right,int bottom){l=left;t=top;r=right;b=bottom;laidOut=true;onLayout(left,top,right,bottom);} protected void onLayout(int l,int t,int r,int b){} public int getWidth(){return r-l;} public int getHeight(){return b-t;}
 public void measure(int width,int height){onMeasure(width,height);} protected void onMeasure(int width,int height){setMeasuredDimension(resolveSize(400,width),resolveSize(60,height));}
 protected final void setMeasuredDimension(int width,int height){measuredWidth=width;measuredHeight=height;}public int getMeasuredWidth(){return measuredWidth;}public int getMeasuredHeight(){return measuredHeight;}
 public static int resolveSize(int desired,int spec){int mode=MeasureSpec.getMode(spec),size=MeasureSpec.getSize(spec);return mode==MeasureSpec.EXACTLY?size:mode==MeasureSpec.AT_MOST?Math.min(desired,size):desired;}
 public static class MeasureSpec {public static final int UNSPECIFIED=0,EXACTLY=0x40000000,AT_MOST=0x80000000;public static int makeMeasureSpec(int size,int mode){return (size&0x3fffffff)|mode;}public static int getSize(int spec){return spec&0x3fffffff;}public static int getMode(int spec){return spec&0xc0000000;}}
 public <T extends View> T findViewById(int value){if(id==value)return (T)this;if(this instanceof ViewGroup)for(View v:((ViewGroup)this).children){T found=v.findViewById(value);if(found!=null)return found;}return null;}
 public int getId(){return id;} public void setId(int value){id=value;} public static int generateViewId(){return nextId++;}
 public void setPadding(int l,int t,int r,int b){pl=l;pt=t;pr=r;pb=b;} public int getPaddingLeft(){return pl;}public int getPaddingTop(){return pt;}public int getPaddingRight(){return pr;}public int getPaddingBottom(){return pb;}
 public WindowInsets getRootWindowInsets(){return getRootView().insets;} public WindowInsets onApplyWindowInsets(WindowInsets insets){return insets;} public boolean getGlobalVisibleRect(Rect rect){if(!isShown()||getWidth()<=0||getHeight()<=0)return false;View root=getRootView();int[] rootLoc=new int[2],loc=new int[2];root.getLocationOnScreen(rootLoc);getLocationOnScreen(loc);rect.left=loc[0]-rootLoc[0];rect.top=loc[1]-rootLoc[1];rect.right=rect.left+getWidth();rect.bottom=rect.top+getHeight();for(View v=parent instanceof View?(View)parent:null;v!=null;v=v.parent instanceof View?(View)v.parent:null){if(v==root||v instanceof ViewGroup&&((ViewGroup)v).getClipChildren()){v.getLocationOnScreen(loc);int pl=v instanceof ViewGroup&&((ViewGroup)v).getClipToPadding()?v.getPaddingLeft():0,pt=v instanceof ViewGroup&&((ViewGroup)v).getClipToPadding()?v.getPaddingTop():0,pr=v instanceof ViewGroup&&((ViewGroup)v).getClipToPadding()?v.getPaddingRight():0,pb=v instanceof ViewGroup&&((ViewGroup)v).getClipToPadding()?v.getPaddingBottom():0;rect.left=Math.max(rect.left,loc[0]-rootLoc[0]+pl);rect.top=Math.max(rect.top,loc[1]-rootLoc[1]+pt);rect.right=Math.min(rect.right,loc[0]-rootLoc[0]+v.getWidth()-pr);rect.bottom=Math.min(rect.bottom,loc[1]-rootLoc[1]+v.getHeight()-pb);}}return rect.right>rect.left&&rect.bottom>rect.top;}
 public void getLocationOnScreen(int[] loc){float x=l,y=t+translationY;for(View v=parent instanceof View?(View)parent:null;v!=null;v=v.parent instanceof View?(View)v.parent:null){x+=v.l;y+=v.t+v.translationY;}loc[0]=Math.round(x);loc[1]=Math.round(y);}public boolean canScrollVertically(int d){return false;}public void scrollBy(int x,int y){}public int requests;public void requestApplyInsets(){requests++;}
 public boolean post(Runnable r){return new android.os.Handler(android.os.Looper.getMainLooper()).post(r);}
}
''',
"android/view/ViewGroup.java": r'''package android.view;
import android.content.Context;import java.util.*;
public class ViewGroup extends View {
 public final List<View> children=new ArrayList<>(); public int additions,removals; private boolean clip=true,clipChildren=true;public static final int FOCUS_BLOCK_DESCENDANTS=0x60000;private int descendantFocusability;
 public void setDescendantFocusability(int value){descendantFocusability=value;}public int getDescendantFocusability(){return descendantFocusability;}
 public ViewGroup(Context c){super(c);} public int getChildCount(){return children.size();}public View getChildAt(int i){return children.get(i);}public int indexOfChild(View v){return children.indexOf(v);}
 public void addView(View v){addView(v,children.size(),v.params==null?new LayoutParams(-1,-2):v.params);} public void addView(View v,LayoutParams lp){addView(v,children.size(),lp);}
 public void addView(View v,int index,LayoutParams lp){if(v.parent!=null)throw new IllegalStateException("already parented");v.setLayoutParams(lp);children.add(index,v);v.parent=this;additions++;if(isAttachedToWindow())v.attach();}
 public void removeView(View v){if(children.remove(v)){v.detach();v.parent=null;removals++;}}
 public boolean getClipToPadding(){return clip;}public void setClipToPadding(boolean value){clip=value;}public boolean getClipChildren(){return clipChildren;}public void setClipChildren(boolean value){clipChildren=value;}
 // These are footer-private measurement doubles, not the shared Android fixtures.
 protected static int childSpec(int spec,int used,int dimension){int mode=View.MeasureSpec.getMode(spec),size=Math.max(0,View.MeasureSpec.getSize(spec)-used);if(dimension>=0)return View.MeasureSpec.makeMeasureSpec(dimension,View.MeasureSpec.EXACTLY);return View.MeasureSpec.makeMeasureSpec(size,dimension==LayoutParams.MATCH_PARENT&&mode==View.MeasureSpec.EXACTLY?View.MeasureSpec.EXACTLY:mode==View.MeasureSpec.UNSPECIFIED?View.MeasureSpec.UNSPECIFIED:View.MeasureSpec.AT_MOST);}
 protected static int leftMargin(View v){return v.params instanceof MarginLayoutParams?((MarginLayoutParams)v.params).leftMargin:0;}protected static int rightMargin(View v){return v.params instanceof MarginLayoutParams?((MarginLayoutParams)v.params).rightMargin:0;}protected static int topMargin(View v){return v.params instanceof MarginLayoutParams?((MarginLayoutParams)v.params).topMargin:0;}protected static int bottomMargin(View v){return v.params instanceof MarginLayoutParams?((MarginLayoutParams)v.params).bottomMargin:0;}
 @Override protected void onMeasure(int width,int height){int w=0,h=0;for(View v:children)if(v.getVisibility()!=GONE){v.measure(childSpec(width,getPaddingLeft()+getPaddingRight()+leftMargin(v)+rightMargin(v),v.params.width),childSpec(height,getPaddingTop()+getPaddingBottom()+topMargin(v)+bottomMargin(v),v.params.height));w=Math.max(w,v.getMeasuredWidth()+leftMargin(v)+rightMargin(v));h=Math.max(h,v.getMeasuredHeight()+topMargin(v)+bottomMargin(v));}setMeasuredDimension(resolveSize(w+getPaddingLeft()+getPaddingRight(),width),resolveSize(h+getPaddingTop()+getPaddingBottom(),height));}
 @Override protected void onLayout(int l,int t,int r,int b){for(View v:children)if(v.getVisibility()!=GONE){int x=getPaddingLeft()+leftMargin(v),y=getPaddingTop()+topMargin(v);v.layout(x,y,x+v.getMeasuredWidth(),y+v.getMeasuredHeight());}}
 public static class LayoutParams {public static final int MATCH_PARENT=-1,WRAP_CONTENT=-2;public int width,height;public LayoutParams(int w,int h){width=w;height=h;}}
 public static class MarginLayoutParams extends LayoutParams {public int leftMargin,rightMargin,topMargin,bottomMargin;public MarginLayoutParams(int w,int h){super(w,h);}}
}
''',
"android/widget/LinearLayout.java": r'''package android.widget;
public class LinearLayout extends android.view.ViewGroup {
 public LinearLayout(android.content.Context c){super(c);}
 @Override protected void onMeasure(int width,int height){int w=0,h=0;for(android.view.View v:children)if(v.getVisibility()!=GONE){android.view.ViewGroup.LayoutParams lp=v.getLayoutParams();v.measure(childSpec(width,getPaddingLeft()+getPaddingRight()+leftMargin(v)+rightMargin(v),lp.width),childSpec(height,getPaddingTop()+getPaddingBottom()+topMargin(v)+bottomMargin(v),lp.height));w=Math.max(w,v.getMeasuredWidth()+leftMargin(v)+rightMargin(v));h+=v.getMeasuredHeight()+topMargin(v)+bottomMargin(v);}setMeasuredDimension(resolveSize(w+getPaddingLeft()+getPaddingRight(),width),resolveSize(h+getPaddingTop()+getPaddingBottom(),height));}
 @Override protected void onLayout(int l,int t,int r,int b){int y=getPaddingTop();for(android.view.View v:children)if(v.getVisibility()!=GONE){y+=topMargin(v);int x=getPaddingLeft()+leftMargin(v);v.layout(x,y,x+v.getMeasuredWidth(),y+v.getMeasuredHeight());y+=v.getMeasuredHeight()+bottomMargin(v);}}
}
''',
"android/widget/RelativeLayout.java": r'''package android.widget;
public class RelativeLayout extends android.view.ViewGroup {public static final int BELOW=3;public RelativeLayout(android.content.Context c){super(c);}
 @Override protected void onMeasure(int width,int height){int w=0,h=0;java.util.Map<Integer,Integer> bottoms=new java.util.HashMap<>();for(android.view.View v:children)if(v.getVisibility()!=GONE){android.view.ViewGroup.LayoutParams lp=v.getLayoutParams();v.measure(childSpec(width,getPaddingLeft()+getPaddingRight()+leftMargin(v)+rightMargin(v),lp.width),childSpec(height,getPaddingTop()+getPaddingBottom()+topMargin(v)+bottomMargin(v),lp.height));int anchor=lp instanceof LayoutParams?((LayoutParams)lp).getRule(BELOW):0;int bottom=bottoms.getOrDefault(anchor,0)+topMargin(v)+v.getMeasuredHeight()+bottomMargin(v);bottoms.put(v.getId(),bottom);w=Math.max(w,v.getMeasuredWidth()+leftMargin(v)+rightMargin(v));h=Math.max(h,bottom);}setMeasuredDimension(resolveSize(w+getPaddingLeft()+getPaddingRight(),width),resolveSize(h+getPaddingTop()+getPaddingBottom(),height));}
 @Override protected void onLayout(int l,int t,int r,int b){java.util.Map<Integer,Integer> bottoms=new java.util.HashMap<>();for(android.view.View v:children)if(v.getVisibility()!=GONE){android.view.ViewGroup.LayoutParams lp=v.getLayoutParams();int anchor=lp instanceof LayoutParams?((LayoutParams)lp).getRule(BELOW):0;int x=getPaddingLeft()+leftMargin(v),y=bottoms.getOrDefault(anchor,getPaddingTop())+topMargin(v);v.layout(x,y,x+v.getMeasuredWidth(),y+v.getMeasuredHeight());bottoms.put(v.getId(),y+v.getMeasuredHeight()+bottomMargin(v));}}
 public static class LayoutParams extends android.view.ViewGroup.MarginLayoutParams {private final java.util.Map<Integer,Integer> rules=new java.util.HashMap<>();public LayoutParams(int w,int h){super(w,h);}public int getRule(int key){return rules.getOrDefault(key,0);}public void addRule(int key,int val){rules.put(key,val);}public void removeRule(int key){rules.remove(key);}}
}
''',
"android/view/Window.java": r'''package android.view;public class Window {private final View decor;public boolean fits=true;public int fitWrites;public Window(android.content.Context c){decor=new ViewGroup(c);}public View getDecorView(){return decor;}public void setDecorFitsSystemWindows(boolean value){if(fits!=value){fits=value;fitWrites++;}decor.requestApplyInsets();}}''',
"android/view/WindowManager.java": r'''package android.view;public class WindowManager {public final android.graphics.Rect bounds=new android.graphics.Rect();public WindowManager(){bounds.right=400;bounds.bottom=1000;}public WindowMetrics getCurrentWindowMetrics(){return new WindowMetrics(bounds);}}''',
"android/view/WindowMetrics.java": r'''package android.view;public class WindowMetrics {private final android.graphics.Rect bounds;public WindowMetrics(android.graphics.Rect b){bounds=b;}public android.graphics.Rect getBounds(){return bounds;}}''',
"android/app/Activity.java": r'''package android.app;
public class Activity extends android.content.Context {private final android.view.Window window=new android.view.Window(this);private final android.view.WindowManager manager=new android.view.WindowManager();public boolean finishing,destroyed;public android.view.Window getWindow(){return window;}public android.view.WindowManager getWindowManager(){return manager;}public boolean isFinishing(){return finishing;}public boolean isDestroyed(){return destroyed;}protected void onStart(){}protected void onResume(){}protected void onNewIntent(android.content.Intent intent){}protected void onPause(){}protected void onDestroy(){destroyed=true;}}
''',
"android/content/Intent.java": r'''package android.content;public class Intent {private String action;public Intent(){}public Intent(String action){this.action=action;}public String getAction(){return action;}}''',
"com/tencent/mm/ui/MMActivity.java": r'''package com.tencent.mm.ui;public class MMActivity extends android.app.Activity { @Override protected void onResume(){} @Override protected void onPause(){} @Override protected void onDestroy(){destroyed=true;} }''',
"com/tencent/mm/ui/LauncherUI.java": r'''package com.tencent.mm.ui;public class LauncherUI extends MMActivity {}''',
"com/tencent/mm/ui/chatting/ChattingUI.java": r'''package com.tencent.mm.ui.chatting;public class ChattingUI extends com.tencent.mm.ui.MMActivity {}''',
"com/tencent/mm/pluginsdk/ui/chat/ChatFooter.java": r'''package com.tencent.mm.pluginsdk.ui.chat;
public class ChatFooter extends android.view.ViewGroup {
 public int switchCalls,configCalls,keyboardCalls,beforeSwitchVisibility,scrollFlagA,scrollFlagB,requestedMode=-1,imeRequests;public boolean failSwitch,failConfig;public Runnable switchProbe;
 public ChatFooter(android.content.Context c){super(c);}
 public void refreshHost(){}
 public void switchHost(int mode,boolean animate,int flag){switchCalls++;ChatFooterBottom panel=findPanel(this);beforeSwitchVisibility=panel==null?-1:panel.getVisibility();if(switchProbe!=null)switchProbe.run();if(failSwitch)throw new IllegalStateException("native switch failed");if(panel!=null&&(mode==2||mode==3))panel.setVisibility(VISIBLE);if(getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams&&panel!=null)((android.view.ViewGroup.MarginLayoutParams)getLayoutParams()).bottomMargin=-Math.max(0,panel.getLayoutParams().height);for(android.view.View v=getParent() instanceof android.view.View?(android.view.View)getParent():null;v!=null;v=v.getParent() instanceof android.view.View?(android.view.View)v.getParent():null)if(v instanceof ChattingScrollLayout){int ime=getRootWindowInsets()==null?0:getRootWindowInsets().ime;try{de.robv.android.xposed.XposedBridge.invoke(v,"scrollHost",new Class<?>[]{int.class,boolean.class,int.class,int.class},mode==2||mode==3?Math.max(360,ime):ime,animate,scrollFlagA,scrollFlagB);}catch(Throwable t){throw new RuntimeException(t);}break;}}
 public void configHost(int mode,boolean animate,int flag){configCalls++;if(failConfig)throw new IllegalStateException("native config failed");requestedMode=mode;if(mode==1&&(getRootWindowInsets()==null||getRootWindowInsets().ime==0))imeRequests++;invokeNative("switchHost",new Class<?>[]{int.class,boolean.class,int.class},mode,animate,flag);}public void keyboardHost(){keyboardCalls++;invokeNative("configHost",new Class<?>[]{int.class,boolean.class,int.class},1,true,-1);}public void fullscreenHost(){}
 private void invokeNative(String name,Class<?>[] types,Object... args){try{de.robv.android.xposed.XposedBridge.invoke(this,name,types,args);}catch(RuntimeException|Error error){throw error;}catch(Throwable error){throw new RuntimeException(error);}}
 private static ChatFooterBottom findPanel(android.view.View v){if(v instanceof ChatFooterBottom)return (ChatFooterBottom)v;if(v instanceof android.view.ViewGroup)for(android.view.View child:((android.view.ViewGroup)v).children){ChatFooterBottom found=findPanel(child);if(found!=null)return found;}return null;}
 // OPTIONAL_Y
}
''',
"com/tencent/mm/pluginsdk/ui/chat/ChatFooterBottom.java": r'''package com.tencent.mm.pluginsdk.ui.chat;public class ChatFooterBottom extends android.view.ViewGroup {public ChatFooterBottom(android.content.Context c){super(c);} }''',
"com/tencent/mm/ui/chatting/view/MMChattingListView.java": r'''package com.tencent.mm.ui.chatting.view;public class MMChattingListView extends android.view.ViewGroup {public MMChattingListView(android.content.Context c){super(c);} }''',
"com/tencent/mm/pluginsdk/ui/tools/ChattingRecyclerView.java": r'''package com.tencent.mm.pluginsdk.ui.tools;public class ChattingRecyclerView extends android.view.ViewGroup {public int scrolled;public ChattingRecyclerView(android.content.Context c){super(c);}public void scrollBy(int x,int y){scrolled+=y;} }''',
"com/tencent/mm/pluginsdk/ui/chat/AppPanel.java": r'''package com.tencent.mm.pluginsdk.ui.chat;public class AppPanel extends android.view.ViewGroup {public int calls,lastHeight;public boolean attachedInitialized,lazy;public AppPanel(android.content.Context c){super(c);}@Override protected void onAttachedToWindow(){attachedInitialized=true;}public void setPortHeighPx(int height){if(lazy&&!attachedInitialized)throw new AssertionError("AppPanel setter before attach");calls++;lastHeight=height;} }''',
"com/tencent/mm/pluginsdk/ui/chat/ChattingUILayout.java": r'''package com.tencent.mm.pluginsdk.ui.chat;
public class ChattingUILayout extends android.view.ViewGroup {public final android.graphics.Paint paint=new android.graphics.Paint();public int extraPadding;public boolean noOpFit,failFit;public ChattingUILayout(android.content.Context c){super(c);}
 // OPTIONAL_FIT
}
''',
"com/tencent/mm/pluginsdk/ui/chat/ChattingScrollLayout.java": r'''package com.tencent.mm.pluginsdk.ui.chat;
public class ChattingScrollLayout extends android.widget.LinearLayout {public int lastY,scrollCalls,lastFlagA,lastFlagB;public boolean lastAnimate;public ChattingScrollLayout(android.content.Context c){super(c);}public void scrollHost(int y,boolean animate,int a,int b){lastY=y;lastAnimate=animate;lastFlagA=a;lastFlagB=b;scrollCalls++;for(android.view.View child:children)child.setTranslationY(-y);} }
''',
"wex/ui/core/WexStore.java": r'''package wex.ui.core;public class WexStore {public static final WexStore INSTANCE=new WexStore();public final java.util.Map<String,Boolean> flags=new java.util.HashMap<>();public boolean getBool(String key,boolean def){return flags.getOrDefault(key,def);} }''',
"wex/ui/core/feature/BubbleSettings.java": r'''package wex.ui.core.feature;public class BubbleSettings {public static int readInt(String key,int def,int min,int max){return def;} }''',
"wex/ui/core/feature/ChatGlassSurface.java": r'''package wex.ui.core.feature;
/** Integration double: records the material contract; rendering lives in the backdrop suite. */
public class ChatGlassSurface extends android.graphics.drawable.Drawable {
 public static final int PLAIN=0,FROSTED=1,LIQUID=2;
 public static int forced=PLAIN,constructions,stops;
 public int color,mode,glassBlur;public float radius,density;public boolean dark;
 public ChatGlassSurface(android.view.View consumer){constructions++;}
 public static int mode(){return forced;}
 public static int blurRadiusDp(int value){return 8;}
 public void configure(int color,float radius,float density,boolean dark,int mode,int blur){this.color=color;this.radius=radius;this.density=density;this.dark=dark;this.mode=mode;glassBlur=blur;}
 public void stop(){stops++;}
}
''',
"wex/ui/core/feature/SettingsTheme.java": r'''package wex.ui.core.feature;public class SettingsTheme {public static boolean isDark(android.content.Context c){return false;} }''',
"wex/ui/core/feature/ChatWindowInsetsCoordinator.java": r'''package wex.ui.core.feature;
public class ChatWindowInsetsCoordinator {public static final int BOTTOM=2;public static int acquired,released;public static boolean fail,owned;public static android.view.Window acquire(android.app.Activity a,int mask){if(fail)throw new IllegalStateException("test");acquired++;owned=true;return a.getWindow();}public static void release(android.view.Window w,int mask){if(w!=null){released++;owned=false;}}public static boolean isBottomOwned(android.view.Window w){return owned;}public static boolean needsBottomReassert(android.app.Activity a){return false;}public static void reassertBottom(android.app.Activity a){} }
''',
"de/robv/android/xposed/XC_MethodHook.java": r'''package de.robv.android.xposed;
public class XC_MethodHook {protected void beforeHookedMethod(MethodHookParam p)throws Throwable{}protected void afterHookedMethod(MethodHookParam p)throws Throwable{}
 public static class MethodHookParam {public java.lang.reflect.Member method;public Object thisObject;public Object[] args=new Object[0];public Object result;public Throwable throwable;public boolean early;public Object getResult(){return result;}public void setResult(Object value){result=value;early=true;}public Throwable getThrowable(){return throwable;}public boolean hasThrowable(){return throwable!=null;}}
}
''',
"de/robv/android/xposed/XposedHelpers.java": r'''package de.robv.android.xposed;public class XposedHelpers {public static Class<?> findClass(String name,ClassLoader loader)throws ClassNotFoundException{return Class.forName(name,false,loader);} }''',
"de/robv/android/xposed/XposedBridge.java": r'''package de.robv.android.xposed;
import java.lang.reflect.*;import java.util.*;
public class XposedBridge {
 public static final Map<Method,List<XC_MethodHook>> hooks=new LinkedHashMap<>(); public static final Map<Class<?>,List<XC_MethodHook>> constructors=new LinkedHashMap<>();public static final List<String> logs=new ArrayList<>();
 public static void log(String msg){logs.add(msg);}public static void hookMethod(Method m,XC_MethodHook cb){hooks.computeIfAbsent(m,key->new ArrayList<>()).add(cb);}
 public static void hookAllConstructors(Class<?> c,XC_MethodHook cb){constructors.computeIfAbsent(c,key->new ArrayList<>()).add(cb);}
 public static void hookAllMethods(Class<?> c,String name,XC_MethodHook cb){for(Method m:c.getDeclaredMethods())if(name.equals(m.getName()))hookMethod(m,cb);}
 public static void constructed(Object object)throws Throwable{XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.thisObject=object;for(XC_MethodHook cb:constructors.getOrDefault(object.getClass(),List.of()))cb.afterHookedMethod(p);}
 public static Object invoke(Object owner,String name,Class<?>[] types,Object... args)throws Throwable{
 Method m=null;for(Class<?> c=owner.getClass();c!=null&&m==null;c=c.getSuperclass())try{m=c.getDeclaredMethod(name,types);}catch(NoSuchMethodException ignored){}
 if(m==null)throw new NoSuchMethodException(name);m.setAccessible(true);XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.method=m;p.thisObject=owner;p.args=args;
 List<XC_MethodHook> callbacks=hooks.getOrDefault(m,List.of());for(XC_MethodHook cb:callbacks)cb.beforeHookedMethod(p);
 if(!p.early)try{p.result=m.invoke(owner,p.args);}catch(InvocationTargetException e){p.throwable=e.getCause();}
 for(int i=callbacks.size()-1;i>=0;i--)callbacks.get(i).afterHookedMethod(p);if(p.throwable!=null)throw p.throwable;return p.result;}
}
''',
"wex/ui/core/feature/HostDatabaseBridge.java": r'''package wex.ui.core.feature;
public class HostDatabaseBridge {static boolean matchesRuntimeKernel(ClassLoader loader){return loader==HostDatabaseBridge.class.getClassLoader();}}
''',
"wex/ui/core/feature/HostTargetResolver.java": r'''package wex.ui.core.feature;
import java.lang.reflect.*;import java.util.*;
public class HostTargetResolver {
 public static int calls; public static boolean fail=true;
 public static ClassLoader hostClassLoader(){return HostTargetResolver.class.getClassLoader();}
 public interface MethodCallback {void onResolved(Method m);void onFailure(Throwable t);}
 public static class MethodSpec {public MethodSpec packages(String... s){return this;}public MethodSpec returnType(String s){return this;}public MethodSpec usingExactStrings(String... s){return this;}public MethodSpec parameterTypes(String... s){return this;}public MethodSpec parameterCount(int n){return this;}}
 public static void resolveMethod(String id,MethodSpec s,MethodCallback cb){calls++;if(fail){if(id.endsWith("refresh"))throw new IllegalStateException("sync optional missing");cb.onFailure(new NoSuchMethodException());return;}
 try{Class<?> footer=com.tencent.mm.pluginsdk.ui.chat.ChatFooter.class;Method m;
 switch(id){case "floating_footer_refresh":m=footer.getDeclaredMethod("refreshHost");break;case "floating_footer_switch":m=footer.getDeclaredMethod("switchHost",int.class,boolean.class,int.class);break;case "floating_footer_config":m=footer.getDeclaredMethod("configHost",int.class,boolean.class,int.class);break;case "floating_footer_keyboard":m=footer.getDeclaredMethod("keyboardHost");break;case "floating_footer_fullscreen":m=footer.getDeclaredMethod("fullscreenHost");break;default:m=com.tencent.mm.pluginsdk.ui.chat.ChattingScrollLayout.class.getDeclaredMethod("scrollHost",int.class,boolean.class,int.class,int.class);}
 cb.onResolved(m);}catch(Exception e){throw new IllegalStateException(e);}}
}
''',
}

SOURCES.update({
    "android/widget/EditText.java": r'''package android.widget;
public class EditText extends android.view.View {public EditText(android.content.Context c){super(c);}}''',
    "android/graphics/drawable/ColorDrawable.java": r'''package android.graphics.drawable;
public class ColorDrawable extends Drawable {private final int color;public ColorDrawable(int value){color=value;}public int getColor(){return color;}}''',
    "androidx/recyclerview/widget/p2.java": r'''package androidx.recyclerview.widget;
public class p2 {public void getItemOffsets(android.graphics.Rect r,android.view.View v,RecyclerView p,h3 s){}}''',
    "androidx/recyclerview/widget/h3.java": r'''package androidx.recyclerview.widget;
public class h3 {public int count;public int b(){return count;}}''',
    "androidx/recyclerview/widget/RecyclerView.java": r'''package androidx.recyclerview.widget;
public class RecyclerView extends android.view.ViewGroup {
public final java.util.List<p2> decorations=new java.util.ArrayList<>();public int invalidations,position;public final Manager manager=new Manager();
public static class Manager {public boolean h;}
public RecyclerView(android.content.Context c){super(c);}public void N(p2 d){decorations.add(d);}public void D0(){invalidations++;}
public int u0(android.view.View v){return position;}public Object getLayoutManager(){return manager;}}''',
    "dalvik/system/InMemoryDexClassLoader.java": r'''package dalvik.system;
public class InMemoryDexClassLoader extends ClassLoader {
public InMemoryDexClassLoader(java.nio.ByteBuffer bytes,ClassLoader parent){super(parent);if(bytes.get(0)!='d'||bytes.get(1)!='e'||bytes.get(2)!='x')throw new AssertionError("invalid DEX");}}''',
    "mdl/spacer/TailDecoration.java": r'''package mdl.spacer;
public class TailDecoration extends androidx.recyclerview.widget.p2 {
private final java.lang.reflect.InvocationHandler callback;
public TailDecoration(java.lang.reflect.InvocationHandler c){callback=c;}
@Override public void getItemOffsets(android.graphics.Rect r,android.view.View v,androidx.recyclerview.widget.RecyclerView p,androidx.recyclerview.widget.h3 s){
try{callback.invoke(this,null,new Object[]{r,v,p,s});}catch(Throwable e){throw new AssertionError(e);}}}''',
})
SOURCES["android/view/View.java"] = SOURCES["android/view/View.java"].replace(
    "public int getHeight(){return b-t;}",
    "public int getHeight(){return b-t;}public boolean layoutRequested;"
    "public boolean isLayoutRequested(){return layoutRequested;}public void postInvalidateOnAnimation(){}"
    "public void getDrawingRect(Rect out){out.left=0;out.top=0;out.right=getWidth();out.bottom=getHeight();}")
SOURCES["android/view/ViewGroup.java"] = SOURCES["android/view/ViewGroup.java"].replace(
    " // These are footer-private",
    " public void offsetDescendantRectToMyCoords(View child,android.graphics.Rect rect){"
    "int[] a=new int[2],b=new int[2];child.getLocationOnScreen(a);getLocationOnScreen(b);"
    "int dx=a[0]-b[0],dy=a[1]-b[1];rect.left+=dx;rect.right+=dx;rect.top+=dy;rect.bottom+=dy;}"
    "\n // These are footer-private")
SOURCES["android/graphics/Outline.java"] = SOURCES["android/graphics/Outline.java"].replace(
    "public int left", "public float alpha=1f;public void setAlpha(float a){alpha=a;}public int left")
SOURCES["wex/ui/core/feature/ChatGlassSurface.java"] = SOURCES["wex/ui/core/feature/ChatGlassSurface.java"].replace(
    "public void stop()", "public int visible=-1;public void visibleHeight(int h){visible=h;}public void stop()")
SOURCES["com/tencent/mm/pluginsdk/ui/chat/AppPanel.java"] = SOURCES[
    "com/tencent/mm/pluginsdk/ui/chat/AppPanel.java"].replace(
    "public int calls,lastHeight;", "public int calls,lastHeight,p0,layoutCalls;public void y(){layoutCalls++;}").replace(
    "calls++;lastHeight=height;", "calls++;lastHeight=height;p0=height;")
SOURCES["wex/ui/core/feature/BubbleSettings.java"] = r'''package wex.ui.core.feature;
public class BubbleSettings {public static final java.util.Map<String,Integer> values=new java.util.HashMap<>();
public static int readInt(String key,int def,int min,int max){return Math.max(min,Math.min(max,values.getOrDefault(key,def)));}}'''
SOURCES["mdl/spacer/TailDecoration.java"] = (
    ROOT / "scripts/chat-tail/mdl/spacer/TailDecoration.java").read_text(encoding="utf-8")
SOURCES["android/util/Log.java"] = r'''package android.util;
public class Log {public static int w(String tag,String message,Throwable error){return 0;}}'''

# Exact original mutually recursive methods, replayed against the otherwise current
# production file to isolate this root cause from unrelated optional-hook fixes.
PRE_FIX_OBSERVE = '''    private static State existing(View footer) {
        WeakReference<State> reference = STATES.get(footer);
        return reference == null ? null : reference.get();
    }

    private static void observe(View footer) {
        if (!FOOTER.equals(footer.getClass().getName())) return;
        if (Looper.myLooper() != Looper.getMainLooper()) { WeakReference<View> reference = new WeakReference<>(footer); MAIN.post(() -> observe(reference.get())); return; }
        State state = state(footer);
        if (state == null) {
            state = new State(footer); STATES.put(footer, new WeakReference<>(state)); footer.addOnAttachStateChangeListener(state);
        }
        state.watch(); state.apply();
    }

    private static State state(Object footer) {
        if (!(footer instanceof View)) return State.EMPTY;
        WeakReference<State> reference = STATES.get((View) footer);
        State state = reference == null ? null : reference.get();
        if (state == null && FOOTER.equals(footer.getClass().getName())) { observe((View) footer); reference = STATES.get((View) footer); state = reference == null ? null : reference.get(); }
        return state == null ? State.EMPTY : state;
    }

'''


def run(command):
    result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace")
    print(result.stdout + result.stderr, end="")
    if result.returncode:
        raise SystemExit(result.returncode)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", required=True, type=Path)
    parser.add_argument("--variants", nargs="+", default=["missing", "present", "pre-fix-observe", "pre-fix-panel",
                                                         "pre-fix-pill", "pre-fix-glass"])
    parser.add_argument("--geometry-only", action="store_true")
    parser.add_argument("--repair-only", action="store_true")
    parser.add_argument("--repair-case", choices=["owned", "lazy", "keyboard", "native-ime"], default="owned")
    parser.add_argument("--footer-source", type=Path, help="Replay an exact frozen footer source with the same real hooks")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="mindlink-chat-footer-") as temporary:
        temp = Path(temporary)
        for variant in args.variants:
            src = temp / variant / "src"
            classes = temp / variant / "classes"
            classes.mkdir(parents=True)
            for name, value in SOURCES.items():
                if name == "android/view/View.java":
                    value = value.replace("public int getHeight(){return b-t;}",
                                          "public int getHeight(){return b-t;}public int getTop(){return t;}")
                if variant in ("present", "pre-fix-panel", "pre-fix-pill", "pre-fix-glass"):
                    value = value.replace("// OPTIONAL_Y", "public int getYFromBottom(){return 300;}")
                    value = value.replace("// OPTIONAL_FIT", "protected boolean fitSystemWindows(android.graphics.Rect rect){if(failFit)throw new IllegalStateException(\"native fit failed\");if(!noOpFit)setPadding(getPaddingLeft(),getPaddingTop(),getPaddingRight(),rect.bottom+extraPadding);return true;}")
                file = src / name
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text(value, encoding="utf-8")
            real = args.footer_source or FEATURE / "FloatingChatFooterFeature.java"
            production = [real, FEATURE / "FloatingFooterLayout.java", FEATURE / "FloatingFooterGeometry.java",
                          FEATURE / "InsetGeometry.java", FEATURE / "ChatToolbarLifecycle.java",
                          FEATURE / "ChatFooterOverlay.java", FEATURE / "ChatTailSpacer.java"]
            if variant == "pre-fix-observe":
                current = real.read_text(encoding="utf-8")
                start = current.index("    private static void observe(View footer) {")
                # Keep the fit-state enum introduced after observe: only the
                # observe/recursion defect is replayed, not the layout helpers.
                end = current.index("    private enum FitState", start)
                replay = src / "wex/ui/core/feature/FloatingChatFooterFeature.java"
                replay.write_text(current[:start] + PRE_FIX_OBSERVE + current[end:], encoding="utf-8")
                production = production[1:]
            elif variant == "pre-fix-pill":
                # Replay the shipped margins()/geometric diagnostic without the
                # gesture-band floor and its band memory.
                current = real.read_text(encoding="utf-8")
                floor = """                int clearance = panelMode == PANEL_SMILEY ? 0 : FloatingFooterLayout.pillClearance(host);
                if (clearance > remaining) remaining = clearance;"""
                assert floor in current, "pill floor missing from production margins"
                current = current.replace(floor, "")
                current = current.replace(
                    "int clearance = panelMode == PANEL_SMILEY ? 0 : FloatingFooterLayout.pillClearance(host);",
                    "int clearance = 0;")
                replay = src / "wex/ui/core/feature/FloatingChatFooterFeature.java"
                replay.write_text(current, encoding="utf-8")
                production = production[1:]
            elif variant == "pre-fix-panel":
                # Replay the production path, not the unused helper that already
                # had GONE. Remove both newly added collapsed-state safeguards.
                current = real.read_text(encoding="utf-8")
                current = current.replace("visibilityOwned = true; panel.setVisibility(View.GONE);", "visibilityOwned = true;")
                current = current.replace("if (panelReady && panelSwitchDepth == 0) syncPanelVisibility();", "// Original apply did not reconcile native collapsed visibility.")
                current = current.replace("syncPanelVisibility();", "// Original successful after did not reconcile collapsed visibility.")
                replay = src / "wex/ui/core/feature/FloatingChatFooterFeature.java"
                replay.write_text(current, encoding="utf-8")
                production = production[1:]
            elif variant == "pre-fix-glass":
                # Replay the shipped card fill: the config never asks for the
                # shared material, so style() can only paint its gradient.
                current = real.read_text(encoding="utf-8")
                glass = ("    final int glass = ChatGlassSurface.mode(), "
                         "glassBlur = ChatGlassSurface.blurRadiusDp(glass);")
                assert glass in current, "card material config missing from production"
                current = current.replace(glass, "    final int glass = ChatGlassSurface.PLAIN, glassBlur = 0;")
                replay = src / "wex/ui/core/feature/FloatingChatFooterFeature.java"
                replay.write_text(current, encoding="utf-8")
                production = production[1:]
            sources = sorted(src.rglob("*.java")) + production + sorted((ROOT / "scripts/tests/chat-footer-hooks").rglob("*.java"))
            print(f"[{variant}] compiling actual FloatingChatFooterFeature + structural helpers")
            run([str(args.jdk / "bin/javac.exe"), "-J-Duser.language=en", "-encoding", "UTF-8", "-d", str(classes), *map(str, sources)])
            run([str(args.jdk / "bin/java.exe"), "-cp", str(classes), "wex.ui.core.feature.ChatFooterHookChecks", variant,
                 *(["repair-only", args.repair_case] if args.repair_only else ["geometry-only"] if args.geometry_only else [])])
            if variant == "present" and not args.geometry_only and not args.repair_only:
                run([str(args.jdk / "bin/java.exe"), "-cp", str(classes),
                     "wex.ui.core.feature.ChatFooterOverlayChecks"])
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    raise SystemExit(main())
