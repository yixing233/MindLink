"""Isolated real Header/Toolbar init, lifecycle, discovery and mount checks (no APK/device).

Reuses footer test source doubles read-only, adds separate doubles in a temporary
source tree, and compiles both current production features + unchanged lifecycle
and view finder. FloatingHeaderStyle is a drawing/ownership double; rendering is
not claimed by these JVM checks.
"""
import argparse
from pathlib import Path
import runpy
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
FEATURE = ROOT / "java_src/wex/ui/core/feature"
SOURCES = dict(runpy.run_path(str(ROOT / "scripts/tests/chat-footer-checks.py"))["SOURCES"])

# Expand only this test's temporary Android doubles, never shared fixtures.
SOURCES["android/content/res/Resources.java"] = '''package android.content.res; public class Resources {private final android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();private final Configuration config=new Configuration();public android.util.DisplayMetrics getDisplayMetrics(){return metrics;}public Configuration getConfiguration(){return config;}}'''
SOURCES["android/util/DisplayMetrics.java"] = '''package android.util; public class DisplayMetrics {public float density=1f;public int widthPixels=400,heightPixels=800;}'''
SOURCES["android/content/res/Configuration.java"] = '''package android.content.res;public class Configuration {public int uiMode;}'''
SOURCES["android/content/res/AssetManager.java"] = '''package android.content.res;public class AssetManager {public java.io.InputStream open(String name)throws java.io.IOException{return new java.io.ByteArrayInputStream(new byte[0]);}}'''
SOURCES["android/R.java"] = '''package android;public class R {public static class id {public static final int content=16908290;}}'''
SOURCES["android/app/Activity.java"] = SOURCES["android/app/Activity.java"].replace("protected void onResume()", "public android.view.View findViewById(int id){return window.getDecorView().findViewById(id);}protected void onResume()")
SOURCES["android/os/Handler.java"] = SOURCES["android/os/Handler.java"].replace("delayed.add(r);return true;", "if(delay==0)immediate.add(r);else delayed.add(r);return true;").replace("int cap=100;", "int cap=1000;")
SOURCES["android/graphics/Rect.java"] = '''package android.graphics;public class Rect {public int left,top,right,bottom;public Rect(){}public Rect(Rect other){set(other);}public void set(Rect other){left=other.left;top=other.top;right=other.right;bottom=other.bottom;}public boolean isEmpty(){return right<=left||bottom<=top;}public int width(){return right-left;}public int height(){return bottom-top;}}'''
SOURCES["android/graphics/Insets.java"] = '''package android.graphics;public class Insets {public int top,bottom;public Insets(int b){bottom=b;}}'''
SOURCES["android/graphics/Paint.java"] = '''package android.graphics;public class Paint {public static final int FILTER_BITMAP_FLAG=1,ANTI_ALIAS_FLAG=2;public static final class Style {public static final Style STROKE=new Style();private Style(){}}public int color;public float shadowRadius,shadowDy,strokeWidth;public int shadowColor;public boolean hasShadow;public final java.util.List<String> ops=new java.util.ArrayList<>();public Paint(){}public Paint(int flags){}public int getAlpha(){return 255;}public void setAlpha(int a){}public void setColorFilter(ColorFilter c){}public void setColor(int value){color=value;ops.add("color");}public void setShadowLayer(float radius,float dx,float dy,int color){hasShadow=true;shadowRadius=radius;shadowDy=dy;shadowColor=color;ops.add("shadow");}public void clearShadowLayer(){hasShadow=false;ops.add("clearShadow");}public void setStyle(Style value){}public void setStrokeWidth(float value){strokeWidth=value;}}'''
SOURCES["android/graphics/Color.java"] = '''package android.graphics;public class Color {public static final int TRANSPARENT=0;public static int alpha(int color){return color>>>24;}}'''
SOURCES["android/graphics/Bitmap.java"] = '''package android.graphics;public class Bitmap {public int getWidth(){return 100;}public int getHeight(){return 100;}public boolean isRecycled(){return false;}}'''
SOURCES["android/graphics/BitmapFactory.java"] = '''package android.graphics;public class BitmapFactory {public static Bitmap decodeFile(String p){return null;}}'''
SOURCES["android/graphics/ColorFilter.java"] = '''package android.graphics;public class ColorFilter {}'''
SOURCES["android/graphics/PixelFormat.java"] = '''package android.graphics;public class PixelFormat {public static final int OPAQUE=-1;}'''
SOURCES["android/graphics/RectF.java"] = '''package android.graphics;public class RectF {public float left,top,right,bottom;public RectF(){}public RectF(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}public void set(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}public void set(RectF s){left=s.left;top=s.top;right=s.right;bottom=s.bottom;}public boolean isEmpty(){return right<=left||bottom<=top;}public void setEmpty(){left=top=right=bottom=0;}public float centerX(){return (left+right)/2f;}public float centerY(){return (top+bottom)/2f;}public float width(){return right-left;}public float height(){return bottom-top;}public void union(RectF s){if(s.left<left)left=s.left;if(s.top<top)top=s.top;if(s.right>right)right=s.right;if(s.bottom>bottom)bottom=s.bottom;}public void offset(float dx,float dy){left+=dx;top+=dy;right+=dx;bottom+=dy;}}'''
SOURCES["android/graphics/Canvas.java"] = '''package android.graphics;public class Canvas {public final java.util.List<RectF> roundRects=new java.util.ArrayList<>();public final java.util.List<boolean[]> roundPaints=new java.util.ArrayList<>();public void save(){}public void translate(float x,float y){}public void clipRect(int l,int t,int r,int b){}public void drawBitmap(Bitmap b,Rect src,RectF dst,Paint p){}public void restore(){}public void drawRoundRect(RectF rect,float rx,float ry,Paint p){roundRects.add(new RectF(rect.left,rect.top,rect.right,rect.bottom));roundPaints.add(new boolean[]{p.hasShadow,p.color==0});}}'''
SOURCES["android/graphics/drawable/Drawable.java"] = '''package android.graphics.drawable;public class Drawable {private android.graphics.Rect bounds=new android.graphics.Rect();public void draw(android.graphics.Canvas c){}public android.graphics.Rect getBounds(){return bounds;}public void setBounds(android.graphics.Rect b){bounds=b;}public void setBounds(int l,int t,int r,int b){bounds=new android.graphics.Rect();bounds.left=l;bounds.top=t;bounds.right=r;bounds.bottom=b;}public Object getCallback(){return null;}public void setAlpha(int a){}public void setColorFilter(android.graphics.ColorFilter c){}public int getOpacity(){return 0;}}'''
SOURCES["android/graphics/drawable/GradientDrawable.java"] = SOURCES["android/graphics/drawable/GradientDrawable.java"].replace("public int color,changes;", "public void setStroke(int width,int color){}public int color,changes;")
for name in ("BitmapDrawable", "NinePatchDrawable", "LayerDrawable"):
    SOURCES[f"android/graphics/drawable/{name}.java"] = f"package android.graphics.drawable;public class {name} extends Drawable {{}}"
SOURCES["android/graphics/drawable/ColorDrawable.java"] = '''package android.graphics.drawable;public class ColorDrawable extends Drawable {private final int color;public ColorDrawable(int c){color=c;}public int getColor(){return color;}}'''
SOURCES["android/util/TypedValue.java"] = '''package android.util;public class TypedValue {public static final int COMPLEX_UNIT_SP=2;}'''
SOURCES["android/view/Gravity.java"] = '''package android.view;public class Gravity {public static final int CENTER_VERTICAL=16;}'''
SOURCES["android/view/MotionEvent.java"] = '''package android.view;public class MotionEvent {public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3;public int getActionMasked(){return 0;}public float getRawX(){return 0;}}'''
SOURCES["android/view/ViewTreeObserver.java"] = SOURCES["android/view/ViewTreeObserver.java"].replace("public interface OnPreDrawListener", "public interface OnGlobalLayoutListener {void onGlobalLayout();}public final java.util.List<OnGlobalLayoutListener> globals=new java.util.ArrayList<>();public void addOnGlobalLayoutListener(OnGlobalLayoutListener l){globals.add(l);}public void removeOnGlobalLayoutListener(OnGlobalLayoutListener l){globals.remove(l);}public void global(){for(OnGlobalLayoutListener l:new java.util.ArrayList<>(globals))l.onGlobalLayout();}public interface OnPreDrawListener")
SOURCES["android/view/View.java"] = SOURCES["android/view/View.java"].replace("public View(Context c)", '''private Object tag;private final java.util.Map<Integer,Object> tags=new java.util.HashMap<>();private final java.util.List<OnLayoutChangeListener> layoutListeners=new java.util.ArrayList<>();private CharSequence description;public int invalidations;
 public interface OnLayoutChangeListener {void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob);}
 public void addOnLayoutChangeListener(OnLayoutChangeListener l){layoutListeners.add(l);}public void removeOnLayoutChangeListener(OnLayoutChangeListener l){layoutListeners.remove(l);}
 public void setTag(Object value){tag=value;}public Object getTag(){return tag;}public void setTag(int key,Object value){tags.put(key,value);}public Object getTag(int key){return tags.get(key);}
 public View findViewWithTag(Object value){if(value.equals(tag))return this;if(this instanceof ViewGroup)for(View child:((ViewGroup)this).children){View found=child.findViewWithTag(value);if(found!=null)return found;}return null;}
 public interface OnClickListener {void onClick(View v);}public interface OnLongClickListener {boolean onLongClick(View v);}public interface OnTouchListener {boolean onTouch(View v,MotionEvent e);}
 public void setOnClickListener(OnClickListener c){}public void setOnLongClickListener(OnLongClickListener c){}public void setOnTouchListener(OnTouchListener c){}
 public void setClickable(boolean v){}public void setMinimumHeight(int value){}public void setBackgroundColor(int color){setBackground(new android.graphics.drawable.ColorDrawable(color));}public void setBackgroundDrawable(Drawable value){setBackground(value);}public void setBackgroundResource(int id){}
 public void setContentDescription(CharSequence value){description=value;}public CharSequence getContentDescription(){return description;}
 public void removeCallbacks(Runnable r){new android.os.Handler(android.os.Looper.getMainLooper()).removeCallbacks(r);}public boolean postDelayed(Runnable r,long delay){return new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(r,delay);}
 public void invalidate(){invalidations++;}protected void onLayout(boolean changed,int l,int t,int r,int b){}
 public android.animation.StateListAnimator stateListAnimator;public android.animation.StateListAnimator getStateListAnimator(){return stateListAnimator;}public void setStateListAnimator(android.animation.StateListAnimator value){stateListAnimator=value;}
 public float translationZ;public float getTranslationZ(){return translationZ;}public void setTranslationZ(float value){translationZ=value;}
 public int layerType;public int getLayerType(){return layerType;}public void setLayerType(int type,Object factory){layerType=type;}
 public static final int LAYER_TYPE_NONE=0,LAYER_TYPE_SOFTWARE=1,LAYER_TYPE_HARDWARE=2,IMPORTANT_FOR_ACCESSIBILITY_NO=2;
 public void setImportantForAccessibility(int value){}public int getImportantForAccessibility(){return 0;}
 public void getLocationInWindow(int[] location){float x=l,y=t;for(View v=parent instanceof View?(View)parent:null;v!=null;v=v.parent instanceof View?(View)v.parent:null){x+=v.l;y+=v.t;}location[0]=Math.round(x);location[1]=Math.round(y);}
 protected void onDraw(android.graphics.Canvas canvas){}
 public View(Context c)''')
SOURCES["android/animation/StateListAnimator.java"] = '''package android.animation;public class StateListAnimator {public int clones;}'''
SOURCES["android/view/View.java"] = SOURCES["android/view/View.java"].replace(
    "public float translationZ;",
    "public float translationX;public float getTranslationX(){return translationX;}public void setTranslationX(float value){translationX=value;}public float translationZ;"
).replace("float x=l,y=t;", "float x=l+translationX,y=t;").replace("x+=v.l;", "x+=v.l+v.translationX;")
SOURCES["android/view/ViewGroup.java"] = SOURCES["android/view/ViewGroup.java"].replace("public void addView(View v){", "public void addView(View v,int index){addView(v,index,v.params==null?new LayoutParams(-1,-2):v.params);}public void addView(View v){").replace("public LayoutParams(int w,int h){width=w;height=h;}", "public LayoutParams(int w,int h){width=w;height=h;}public LayoutParams(LayoutParams p){this(p.width,p.height);}").replace("public MarginLayoutParams(int w,int h){super(w,h);}", "public MarginLayoutParams(int w,int h){super(w,h);}public MarginLayoutParams(MarginLayoutParams p){super(p);setMargins(p.leftMargin,p.topMargin,p.rightMargin,p.bottomMargin);}public void setMargins(int l,int t,int r,int b){leftMargin=l;topMargin=t;rightMargin=r;bottomMargin=b;}")
SOURCES["android/widget/RelativeLayout.java"] = SOURCES["android/widget/RelativeLayout.java"].replace("BELOW=3;", "BELOW=3,ALIGN_PARENT_TOP=10,CENTER_IN_PARENT=13,CENTER_VERTICAL=14,ALIGN_PARENT_RIGHT=11,ALIGN_PARENT_END=21;").replace("public LayoutParams(int w,int h){super(w,h);}", "public LayoutParams(int w,int h){super(w,h);}public LayoutParams(LayoutParams p){super(p);rules.putAll(p.rules);}public void addRule(int key){addRule(key,-1);}")
SOURCES["android/widget/LinearLayout.java"] = '''package android.widget;public class LinearLayout extends android.view.ViewGroup {public static final int HORIZONTAL=0,VERTICAL=1;private int orientation;public LinearLayout(android.content.Context c){super(c);}public void setOrientation(int o){orientation=o;}public int getOrientation(){return orientation;}public void setGravity(int g){}public static class LayoutParams extends android.view.ViewGroup.MarginLayoutParams {public LayoutParams(int w,int h){super(w,h);}}
 @Override protected void onMeasure(int width,int height){int w=0,h=0;for(android.view.View v:children)if(v.getVisibility()!=GONE){android.view.ViewGroup.LayoutParams lp=v.getLayoutParams();v.measure(childSpec(width,getPaddingLeft()+getPaddingRight()+leftMargin(v)+rightMargin(v),lp.width),childSpec(height,getPaddingTop()+getPaddingBottom()+topMargin(v)+bottomMargin(v),lp.height));w=Math.max(w,v.getMeasuredWidth()+leftMargin(v)+rightMargin(v));h+=v.getMeasuredHeight()+topMargin(v)+bottomMargin(v);}setMeasuredDimension(resolveSize(w+getPaddingLeft()+getPaddingRight(),width),resolveSize(h+getPaddingTop()+getPaddingBottom(),height));}
 @Override protected void onLayout(int l,int t,int r,int b){int x=getPaddingLeft();for(android.view.View v:children)if(v.getVisibility()!=GONE){x+=leftMargin(v);v.layout(x,getPaddingTop()+topMargin(v),x+v.getMeasuredWidth(),getPaddingTop()+topMargin(v)+v.getMeasuredHeight());x+=v.getMeasuredWidth()+rightMargin(v);}}}'''
SOURCES["android/widget/FrameLayout.java"] = '''package android.widget;public class FrameLayout extends android.view.ViewGroup {public FrameLayout(android.content.Context c){super(c);}public static class LayoutParams extends android.view.ViewGroup.MarginLayoutParams {public LayoutParams(int w,int h){super(w,h);}}}'''
SOURCES["android/widget/HorizontalScrollView.java"] = '''package android.widget;public class HorizontalScrollView extends FrameLayout {public HorizontalScrollView(android.content.Context c){super(c);}public void setHorizontalScrollBarEnabled(boolean v){}}'''
SOURCES["android/widget/TextView.java"] = '''package android.widget;public class TextView extends android.view.View {private CharSequence text;private int maxWidth=Integer.MAX_VALUE;public TextView(android.content.Context c){super(c);}public void setText(CharSequence s){text=s;}public CharSequence getText(){return text;}public void setMaxWidth(int width){maxWidth=width;}public int getMaxWidth(){return maxWidth;}public void setTextSize(int unit,float size){}public void setTextColor(int color){}public void setSingleLine(boolean v){}}'''
SOURCES["android/widget/EditText.java"] = '''package android.widget;public class EditText extends TextView {public EditText(android.content.Context c){super(c);}}'''
SOURCES["android/widget/ImageView.java"] = '''package android.widget;public class ImageView extends android.view.View {private android.graphics.drawable.Drawable drawable;public ImageView(android.content.Context c){super(c);}public void setImageDrawable(android.graphics.drawable.Drawable d){drawable=d;}public android.graphics.drawable.Drawable getDrawable(){return drawable;}}'''
SOURCES["com/tencent/mm/ui/widget/WXActionMenuView.java"] = '''package com.tencent.mm.ui.widget;public class WXActionMenuView extends android.view.ViewGroup {public WXActionMenuView(android.content.Context c){super(c);}}'''
SOURCES["android/widget/AdapterView.java"] = '''package android.widget;public class AdapterView extends android.view.ViewGroup {public AdapterView(android.content.Context c){super(c);}public interface OnItemClickListener {void onItemClick(AdapterView parent,android.view.View view,int pos,long id);}public interface OnItemLongClickListener {boolean onItemLongClick(AdapterView parent,android.view.View view,int pos,long id);}private OnItemClickListener mOnItemClickListener;public OnItemLongClickListener getOnItemLongClickListener(){return null;}}'''
SOURCES["android/widget/ListAdapter.java"] = '''package android.widget;public interface ListAdapter {int getCount();android.view.View getView(int pos,android.view.View old,android.view.ViewGroup parent);}'''
SOURCES["android/widget/AbsListView.java"] = '''package android.widget;public class AbsListView extends AdapterView {public AbsListView(android.content.Context c){super(c);}public ListAdapter getAdapter(){return null;}}'''
SOURCES["androidx/appcompat/widget/ActionBarContainer.java"] = '''package androidx.appcompat.widget;public class ActionBarContainer extends android.view.ViewGroup {public ActionBarContainer(android.content.Context c){super(c);}}'''
SOURCES["androidx/appcompat/widget/Toolbar.java"] = '''package androidx.appcompat.widget;public class Toolbar extends android.view.ViewGroup {public Toolbar(android.content.Context c){super(c);}}'''
SOURCES["wex/ui/core/WexStore.java"] = '''package wex.ui.core;public class WexStore {public static final WexStore INSTANCE=new WexStore();public final java.util.Map<String,Boolean> flags=new java.util.HashMap<>();public final java.util.Map<String,String> strings=new java.util.HashMap<>();public boolean getBool(String key,boolean def){return flags.getOrDefault(key,def);}public String getString(String key,String def){return strings.getOrDefault(key,def);}public void putString(String key,String value){strings.put(key,value);}}'''
SOURCES["wex/ui/core/feature/TablerIcons.java"] = '''package wex.ui.core.feature;public class TablerIcons {public static boolean has(String name){return true;}public static int themeColor(){return 0;}public static android.graphics.drawable.Drawable drawableDp(String name,float size,int color){return new android.graphics.drawable.Drawable();}}'''
SOURCES["wex/ui/core/feature/SettingsTheme.java"] = '''package wex.ui.core.feature;public class SettingsTheme {public static boolean dark;public static boolean isDark(android.content.Context c){return dark;}}'''
SOURCES["wex/ui/core/feature/ImagePicker.java"] = '''package wex.ui.core.feature;public class ImagePicker {public static final ImagePicker INSTANCE=new ImagePicker();public void toast(android.app.Activity a,String s){}}'''
SOURCES["wex/ui/core/feature/FloatingChatFooterFeature.java"] = '''package wex.ui.core.feature;public class FloatingChatFooterFeature {public static boolean enabled(){return true;}}'''
SOURCES["wex/ui/core/feature/SystemBarsCoordinator.java"] = '''package wex.ui.core.feature;public class SystemBarsCoordinator {public static android.graphics.Insets bars(android.view.View v){return new android.graphics.Insets(0);}}'''
SOURCES["wex/ui/core/feature/ChatWindowInsetsCoordinator.java"] = SOURCES["wex/ui/core/feature/ChatWindowInsetsCoordinator.java"].replace("public static final int BOTTOM=2;", "public static final int TOP=1,BOTTOM=2;")
SOURCES["android/view/WindowInsetsController.java"] = '''package android.view;public class WindowInsetsController {public static final int APPEARANCE_LIGHT_STATUS_BARS=16;public int appearance,appearanceMask;public void setSystemBarsAppearance(int value,int mask){appearance=value;appearanceMask=mask;}}'''
SOURCES["android/view/Window.java"] = SOURCES["android/view/Window.java"].replace("public View getDecorView(){return decor;}", "public int statusBarColor;public android.view.WindowInsetsController insetsController=new android.view.WindowInsetsController();public void setStatusBarColor(int color){statusBarColor=color;}public android.view.WindowInsetsController getInsetsController(){return insetsController;}public View getDecorView(){return decor;}")
SOURCES["wex/ui/core/feature/ChatGlassSurface.java"] = SOURCES["wex/ui/core/feature/ChatGlassSurface.java"].replace("public static int blurRadiusDp(int value){return 8;}", "public static int blurRadiusDp(int value){return 8;}public int lastMode(){return mode;}@Override public void draw(android.graphics.Canvas c){android.graphics.Rect b=getBounds();c.drawRoundRect(new android.graphics.RectF(b.left,b.top,b.right,b.bottom),radius,radius,new android.graphics.Paint());}")
SOURCES["wex/ui/core/feature/ChatGlassSurface.java"] = SOURCES["wex/ui/core/feature/ChatGlassSurface.java"].replace("public void stop(){stops++;}", "public void stop(){stops++;}public void backdrop(android.view.View source,android.view.View content){}")
SOURCES["wex/ui/core/feature/LiquidGlassDecoration.java"] = '''package wex.ui.core.feature;
final class LiquidGlassDecoration {
 public void dropShadow(android.graphics.Canvas c,android.graphics.RectF rect,float radius,float density,boolean dark){}
 public void release(){}
}'''
SOURCES["wex/ui/core/feature/FloatingHeaderStyle.java"] = '''package wex.ui.core.feature;
import android.view.View;import android.graphics.drawable.Drawable;
public class FloatingHeaderStyle {
 public static int cards,edges;public static class CardState {View view;Drawable background;float elevation;CardState(View v){view=v;background=v.getBackground();elevation=v.getElevation();}}
 public static class WindowHeaderState {}public static class SeparatedState {}public static class TopEdgeState {}
 public static TopEdgeState applyTopEdgeToEdge(View v,android.app.Activity a,TopEdgeState s){edges++;return s==null?new TopEdgeState():s;}
 public static WindowHeaderState applyWindowHeader(View l,View h,android.app.Activity a,WindowHeaderState s){return s==null?new WindowHeaderState():s;}
 public static CardState applyCard(View v,CardState s,int corner,int elevation){cards++;if(s==null)s=new CardState(v);v.setElevation(elevation);return s;}
 public static CardState applyCard(View v,CardState s,int corner,int elevation,int glass,int blur){cards++;if(s==null)s=new CardState(v);v.setElevation(elevation);return s;}
 public static SeparatedState applySeparated(View v,SeparatedState s,int corner,int gap,int elevation,View backdrop){return new SeparatedState();}
 public static void restoreSeparated(SeparatedState s){}public static void restoreWindowHeader(WindowHeaderState s){}public static void restoreTopEdge(TopEdgeState s){}
 public static void restore(CardState s){if(s!=null){s.view.setBackground(s.background);s.view.setElevation(s.elevation);}}
}'''
SOURCES["de/robv/android/xposed/callbacks/XC_LoadPackage.java"] = '''package de.robv.android.xposed.callbacks;public class XC_LoadPackage {public static class LoadPackageParam {public ClassLoader classLoader;}}'''
SOURCES["de/robv/android/xposed/XposedHelpers.java"] = '''package de.robv.android.xposed;public class XposedHelpers {public static Class<?> findClass(String name,ClassLoader loader)throws ClassNotFoundException{return Class.forName(name,false,loader);}public static java.lang.reflect.Field findField(Class<?> c,String name)throws NoSuchFieldException{return c.getDeclaredField(name);}}'''
SOURCES["de/robv/android/xposed/XposedBridge.java"] = SOURCES["de/robv/android/xposed/XposedBridge.java"].replace("Map<Method,List<XC_MethodHook>>", "Map<Member,List<XC_MethodHook>>").replace("public static void hookMethod(Method m,XC_MethodHook cb){", "public static boolean failGrid;public static void hookMethod(Member m,XC_MethodHook cb){if(failGrid&&m.getName().equals(\"initGrid\"))throw new LinkageError(\"test optional hook failure\");").replace("for(XC_MethodHook cb:constructors.getOrDefault(object.getClass(),List.of()))cb.afterHookedMethod(p);", "for(XC_MethodHook cb:constructors.getOrDefault(object.getClass(),List.of()))cb.afterHookedMethod(p);for(java.lang.reflect.Constructor<?> c:object.getClass().getDeclaredConstructors())for(XC_MethodHook cb:hooks.getOrDefault(c,List.of()))cb.afterHookedMethod(p);")
SOURCES["com/tencent/mm/pluginsdk/ui/chat/AppPanel.java"] = '''package com.tencent.mm.pluginsdk.ui.chat;public class AppPanel extends android.view.ViewGroup {public static int initialized;public AppPanel(android.content.Context c){super(c);}public static void initGrid(AppPanel panel){initialized++;}}'''
SOURCES["com/tencent/mm/ui/tipsbar/ChatTipsBarGroup.java"] = '''package com.tencent.mm.ui.tipsbar;
public class ChatTipsBarGroup extends android.widget.FrameLayout {
 public android.view.View target;public int observedTop;
 public ChatTipsBarGroup(android.content.Context c){super(c);}
 private void setListViewPaddingTop(int extra){
  if(target==null)return;observedTop=target.getPaddingTop();
  Integer base=(Integer)target.getTag(0);if(base==null){base=observedTop;target.setTag(0,base);}
  target.setPadding(target.getPaddingLeft(),base+extra,target.getPaddingRight(),target.getPaddingBottom());
 }
}'''
SOURCES["com/tencent/mm/ui/chatting/view/MMChattingListView.java"] = '''package com.tencent.mm.ui.chatting.view;public class MMChattingListView extends android.view.ViewGroup {public MMChattingListView(android.content.Context c){super(c);}}'''
SOURCES["com/tencent/mm/pluginsdk/ui/tools/ChattingRecyclerView.java"] = '''package com.tencent.mm.pluginsdk.ui.tools;public class ChattingRecyclerView extends android.view.ViewGroup {public ChattingRecyclerView(android.content.Context c){super(c);}}'''
SOURCES["wex/ui/core/feature/HostTargetResolver.java"] = '''package wex.ui.core.feature;public class HostTargetResolver {public static int calls;public static boolean fail;public interface MethodCallback {void onResolved(java.lang.reflect.Method m);void onFailure(Throwable t);}public static class MethodSpec {public MethodSpec declaredClass(String s){return this;}public MethodSpec returnType(String s){return this;}public MethodSpec parameterTypes(String... s){return this;}public MethodSpec usingExactStrings(String... s){return this;}}public static void resolveMethod(String id,MethodSpec spec,MethodCallback cb){calls++;if(fail){cb.onFailure(new NoSuchMethodException());return;}try{cb.onResolved(com.tencent.mm.pluginsdk.ui.chat.AppPanel.class.getDeclaredMethod("initGrid",com.tencent.mm.pluginsdk.ui.chat.AppPanel.class));}catch(Exception e){throw new IllegalStateException(e);}}}'''

ORIGINAL_HEADER_INIT = '''    public static synchronized void init(ClassLoader loader) throws Exception {
        if (initialized) return;
        config = new Config();
        Class<?> layout = XposedHelpers.findClass(LAYOUT, loader);
        XposedBridge.hookAllConstructors(layout, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) { if (param.thisObject instanceof View) observe((View) param.thisObject); }
        });
        java.lang.reflect.Method onLayout = inherited(layout, "onLayout", boolean.class, int.class, int.class, int.class, int.class);
        XposedBridge.hookMethod(onLayout, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) { if (layout.isInstance(param.thisObject)) observe((View) param.thisObject); }
        });
        java.lang.reflect.Method fit = layout.getDeclaredMethod("fitSystemWindows", Rect.class);
        fit.setAccessible(true);
        XposedBridge.hookMethod(fit, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) { if (param.thisObject instanceof View) observe((View) param.thisObject); }
        });
        Class<?> footer = XposedHelpers.findClass(FOOTER, loader);
        XposedBridge.hookMethod(inherited(footer, "onAttachedToWindow"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) { }
        });
        XposedBridge.hookAllMethods(Activity.class, "onResume", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) { refresh(); }
        });
        initialized = true;
    }

'''


def run(command):
    result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace")
    print(result.stdout + result.stderr, end="")
    if result.returncode:
        raise SystemExit(result.returncode)


def tips_card(source):
    """Static contract for the group-chat pinned/announcement card (no JVM doubles needed).

    The card is host chrome the feature does not own, so what matters is structural: which host
    class it targets, that it is parked below the title, that it carries the shared material, and
    that the message-list padding is additive instead of a whole-value overwrite of the host's
    {@code setListViewPaddingTop()} baseline.
    """
    checks = [
        ("targets the pinned/announcement stack by host class name",
         'TIPS = "com.tencent.mm.ui.tipsbar.ChatTipsBarGroup"'),
        ("parks the card below the floating title and the status bar",
         "+ header.getHeight() + Math.round(config.extra * density)"),
        ("dresses the card with the shared home-bar material",
         "FloatingHeaderStyle.applyCard(tips, tipsStyle, config.corner, config.elevation,\n                    config.glass, config.glassBlur)"),
        ("detaches the host panel chrome so the material is visible",
         "child.setBackground(null)"),
        ("adds its inset on top of the host's own padding",
         "if (host == listWrittenTop) host -= listExtra;"),
        ("never writes the list padding as a whole value",
         "originalPadding.top + SystemBarsCoordinator.bars(layout).top"),
        ("applies the card before the list padding is recomputed",
         "applyTips(layout, header);\n                applyListPadding(layout, header);"),
        ("restores the card when the feature releases the chat",
         "restoreHeader(); restoreTips(); restoreRecycler();"),
    ]
    for label, needle in checks:
        present = needle in source
        if label == "never writes the list padding as a whole value":
            if present:
                raise SystemExit("RED: list padding is still overwritten as a whole value")
            print(f"PASS: tips card {label}")
            continue
        if not present:
            raise SystemExit(f"RED: tips card {label} ({needle!r})")
        print(f"PASS: tips card {label}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", required=True, type=Path)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="mindlink-chat-header-toolbar-") as temporary:
        for variant in ("missing", "present", "pre-fix-header", "pre-fix-toolbar", "pre-fix-entry", "pre-fix-scan", "pre-fix-padding", "separated-geometry"):
            base = Path(temporary) / variant
            src, classes = base / "src", base / "classes"
            classes.mkdir(parents=True)
            for name, value in SOURCES.items():
                if variant == "present":
                    value = value.replace("// OPTIONAL_FIT", "protected boolean fitSystemWindows(android.graphics.Rect rect){return true;}")
                if variant == "separated-geometry" and name == "wex/ui/core/feature/FloatingHeaderStyle.java":
                    continue  # compile the real production style for this variant
                file = src / name
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text(value, encoding="utf-8")
            production = [FEATURE / name for name in ("FloatingChatHeaderFeature.java", "ChatToolbarFeature.java", "ChatToolbarLifecycle.java", "ChatToolbarViews.java")]
            if variant == "separated-geometry":
                production.append(FEATURE / "FloatingHeaderStyle.java")
            if variant == "pre-fix-header":
                current = production[0].read_text(encoding="utf-8")
                start = current.index("    public static synchronized void init(ClassLoader loader)")
                end = current.index("    private static void optionalHook", start)
                replay = src / "wex/ui/core/feature/FloatingChatHeaderFeature.java"
                replay.write_text(current[:start] + ORIGINAL_HEADER_INIT + current[end:], encoding="utf-8")
                production.pop(0)
            if variant == "pre-fix-toolbar":
                current = production[1].read_text(encoding="utf-8")
                start = current.index("        ChatToolbarLifecycle existing = FOOTERS.get(footer);")
                end = current.index("        final WeakReference<ViewGroup> ref", start)
                replay = src / "wex/ui/core/feature/ChatToolbarFeature.java"
                replay.write_text(current[:start] + "        if (footer.getTag(TAG_LIFECYCLE) != null) return;\n        Object nativeTag = null;\n" + current[end:], encoding="utf-8")
                production.pop(1)
            if variant == "pre-fix-scan":
                current = production[1].read_text(encoding="utf-8")
                start = current.index("    private static ViewGroup findInputContainer(ViewGroup root)")
                end = current.index("    private static final WeakHashMap<ViewGroup, String> MOUNT_STAGES", start)
                gate = '''    private static ViewGroup findInputContainer(ViewGroup root) {
        if (root == null) return null;
        java.util.ArrayDeque<View> queue = new java.util.ArrayDeque<>(); queue.add(root); int admitted = 1;
        while (!queue.isEmpty()) {
            View view = queue.removeFirst();
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                if (group.getChildCount() > 512 - admitted) return null;
                for (int i = 0; i < group.getChildCount(); i++) { queue.addLast(group.getChildAt(i)); admitted++; }
            }
        }
        return ChatToolbarViews.findInputContainer(root);
    }

'''
                replay = src / "wex/ui/core/feature/ChatToolbarFeature.java"
                replay.write_text(current[:start] + gate + current[end:], encoding="utf-8")
                production.pop(1)
            if variant == "pre-fix-padding":
                current = production[0].read_text(encoding="utf-8")
                needle = "if (host == listWrittenTop) host -= listExtra;"
                if needle not in current:
                    raise SystemExit("padding replay needle missing")
                replay = src / "wex/ui/core/feature/FloatingChatHeaderFeature.java"
                replay.write_text(current.replace(needle, "host -= listExtra;"), encoding="utf-8")
                production.pop(0)
            if variant == "pre-fix-entry":
                # Replay the pre-fix entry path: onResume was the only Activity hook, so a
                # notification tap that only delivers onNewIntent/onStart stayed undecorated.
                for index, needles in ((0, ('optionalHook(type, "onNewIntent", entered, Intent.class);',
                                            'optionalHook(type, "onStart", entered);')),
                                       (1, ('hookLifecycle(type, "onNewIntent", entered, Intent.class);',
                                            'hookLifecycle(type, "onStart", entered);'))):
                    name = production[index].name
                    current = production[index].read_text(encoding="utf-8")
                    for needle in needles:
                        if needle not in current:
                            raise SystemExit(f"pre-fix-entry replay needle missing: {needle}")
                        current = current.replace(needle, "")
                    replay = src / "wex/ui/core/feature" / name
                    replay.parent.mkdir(parents=True, exist_ok=True)
                    replay.write_text(current, encoding="utf-8")
                production.pop(1)
                production.pop(0)
            sources = sorted(src.rglob("*.java")) + production + sorted((ROOT / "scripts/tests/chat-header-toolbar-hooks").rglob("*.java"))
            print(f"[{variant}] compile actual Header/Toolbar + unchanged lifecycle/view finder")
            if variant == "separated-geometry":
                print("[separated-geometry] compile real FloatingHeaderStyle drawing geometry")
            run([str(args.jdk / "bin/javac.exe"), "-J-Duser.language=en", "-encoding", "UTF-8", "-d", str(classes), *map(str, sources)])
            run([str(args.jdk / "bin/java.exe"), "-cp", str(classes), "wex.ui.core.feature.ChatHeaderToolbarChecks", variant])
        tips_card((FEATURE / "FloatingChatHeaderFeature.java").read_text(encoding="utf-8"))
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    raise SystemExit(main())
