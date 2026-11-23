"""Isolated systembar checks: both actual production coordinators in every source set.
No APK, device, network or shared fixture edits. All outputs go to a new scoped directory.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
FEATURE = ROOT / "java_src/wex/ui/core/feature"
SUITE = ROOT / "scripts/tests/systembars/wex/ui/core/feature/SystemBarsChecks.java"
SOURCES = {
"android/R.java": "package android; public final class R {public static final class id {public static final int content=1;}}",
"android/content/Context.java": "package android.content; public class Context {public boolean dark; public String packageName=\"com.tencent.mm\"; public String getPackageName(){return packageName;}}",
"android/content/ContextWrapper.java": "package android.content;public class ContextWrapper extends Context {public Context getBaseContext(){return this;}}",
"android/os/Looper.java": "package android.os;public class Looper {private static final Looper MAIN=new Looper();public static Looper getMainLooper(){return MAIN;}public static Looper myLooper(){return MAIN;}}",
"android/graphics/Color.java": "package android.graphics; public final class Color {public static final int TRANSPARENT=0;public static int alpha(int c){return c>>>24;}public static int red(int c){return c>>16&255;}public static int green(int c){return c>>8&255;}public static int blue(int c){return c&255;}}",
"android/graphics/Rect.java": "package android.graphics;public class Rect {}",
"android/graphics/Insets.java": "package android.graphics; public final class Insets {public final int left,top,right,bottom;public static final Insets NONE=new Insets(0,0,0,0);public Insets(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}}",
"android/graphics/drawable/Drawable.java": "package android.graphics.drawable; public class Drawable {}",
"android/graphics/drawable/ColorDrawable.java": "package android.graphics.drawable; public class ColorDrawable extends Drawable {private int color;public ColorDrawable(int c){color=c;}public int getColor(){return color;}public void setColor(int c){color=c;}}",
"android/view/WindowInsets.java": '''package android.view; import android.graphics.Insets;
public class WindowInsets {public int nav=24,tap,ime,status=30;public Insets getInsets(int types){return new Insets(0,(types&8)!=0?status:0,0,Math.max((types&1)!=0?nav:0,Math.max((types&2)!=0?tap:0,(types&4)!=0?ime:0)));}public static final class Type {public static int navigationBars(){return 1;}public static int tappableElement(){return 2;}public static int ime(){return 4;}public static int systemBars(){return 9;}public static int displayCutout(){return 16;}}}''',
"android/view/WindowInsetsController.java": '''package android.view; public class WindowInsetsController {public static final int APPEARANCE_LIGHT_STATUS_BARS=8,APPEARANCE_LIGHT_NAVIGATION_BARS=16;private int appearance;public int writes;public int getSystemBarsAppearance(){return appearance;}public void setSystemBarsAppearance(int value,int mask){appearance=(appearance&~mask)|(value&mask);writes++;}}''',
"android/view/ViewParent.java": "package android.view; public interface ViewParent {}",
"android/view/ViewTreeObserver.java": '''package android.view;import java.util.*;public class ViewTreeObserver {public interface OnGlobalLayoutListener{void onGlobalLayout();}public final List<OnGlobalLayoutListener> layouts=new ArrayList<>();public void addOnGlobalLayoutListener(OnGlobalLayoutListener l){layouts.add(l);}public void removeOnGlobalLayoutListener(OnGlobalLayoutListener l){layouts.remove(l);}public void layout(){for(OnGlobalLayoutListener l:new ArrayList<>(layouts))l.onGlobalLayout();}public interface OnPreDrawListener {boolean onPreDraw();}public final List<OnPreDrawListener> listeners=new ArrayList<>();public boolean isAlive(){return true;}public void addOnPreDrawListener(OnPreDrawListener l){listeners.add(l);}public void removeOnPreDrawListener(OnPreDrawListener l){listeners.remove(l);}public void draw(){for(OnPreDrawListener l:new ArrayList<>(listeners))l.onPreDraw();}}''',
"android/view/View.java": '''package android.view;
import android.content.Context;import android.graphics.drawable.Drawable;import java.util.*;
public class View implements ViewParent {
 public static final int VISIBLE=0,GONE=8,SYSTEM_UI_FLAG_LIGHT_STATUS_BAR=8192,SYSTEM_UI_FLAG_LAYOUT_STABLE=256,SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN=1024,SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION=512;
 public final Context context;public ViewParent parent;public int x,y,width=400,height=800,visibility=VISIBLE,id,ui,paddingLeft,paddingTop,paddingRight,paddingBottom;public boolean attached=true,focused=true;public float alpha=1;public Drawable background;public WindowInsets insets=new WindowInsets();public final ArrayDeque<Runnable> posts=new ArrayDeque<>();public final List<OnAttachStateChangeListener> attachListeners=new ArrayList<>();public final ViewTreeObserver observer=new ViewTreeObserver();public int requests,uiWrites;public boolean syncLegacyAppearance;public WindowInsetsController legacyController;
 public View(Context c){context=c;}public Context getContext(){return context;}public ViewParent getParent(){return parent;}public View getRootView(){View v=this;while(v.parent instanceof View)v=(View)v.parent;return v;}public WindowInsets getRootWindowInsets(){return getRootView().insets;}
 public boolean hasWindowFocus(){return focused;}public boolean isAttachedToWindow(){return attached;}public boolean isShown(){for(View v=this;v!=null;v=v.parent instanceof View?(View)v.parent:null)if(!v.attached||v.visibility!=VISIBLE)return false;return true;}public int getVisibility(){return visibility;}public float getAlpha(){return alpha;}public int getWidth(){return width;}public int getHeight(){return height;}public void getLocationInWindow(int[] out){out[0]=x;out[1]=y;for(View v=parent instanceof View?(View)parent:null;v!=null;v=v.parent instanceof View?(View)v.parent:null){out[0]+=v.x;out[1]+=v.y;}}
 public boolean getGlobalVisibleRect(android.graphics.Rect r){return isShown()&&alpha>0&&width>0&&height>0;}public Drawable getBackground(){return background;}public void setBackground(Drawable d){background=d;}public View findViewById(int target){if(id==target)return this;if(this instanceof ViewGroup)for(View v:((ViewGroup)this).children){View found=v.findViewById(target);if(found!=null)return found;}return null;}
 public int getSystemUiVisibility(){return ui;}public void setSystemUiVisibility(int value){ui=value;uiWrites++;if(syncLegacyAppearance&&legacyController!=null)legacyController.setSystemBarsAppearance((ui&SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)==0?0:8,8);}public void requestApplyInsets(){requests++;}public boolean post(Runnable r){posts.add(r);return true;}public boolean removeCallbacks(Runnable r){return posts.remove(r);}public void drain(){int limit=100;while(!posts.isEmpty()){if(--limit<0)throw new AssertionError("post loop");posts.remove().run();}}
 public interface OnAttachStateChangeListener {void onViewAttachedToWindow(View v);void onViewDetachedFromWindow(View v);}public void addOnAttachStateChangeListener(OnAttachStateChangeListener l){attachListeners.add(l);}public void removeOnAttachStateChangeListener(OnAttachStateChangeListener l){attachListeners.remove(l);}public void detach(){attached=false;for(OnAttachStateChangeListener l:new ArrayList<>(attachListeners))l.onViewDetachedFromWindow(this);if(this instanceof ViewGroup)for(View v:new ArrayList<>(((ViewGroup)this).children))v.detach();}public ViewTreeObserver getViewTreeObserver(){return observer;}
 public interface OnApplyWindowInsetsListener {WindowInsets onApplyWindowInsets(View v,WindowInsets i);}public void setOnApplyWindowInsetsListener(OnApplyWindowInsetsListener l){}public int getPaddingLeft(){return paddingLeft;}public int getPaddingTop(){return paddingTop;}public int getPaddingRight(){return paddingRight;}public int getPaddingBottom(){return paddingBottom;}public void setPadding(int l,int t,int r,int b){paddingLeft=l;paddingTop=t;paddingRight=r;paddingBottom=b;}
}''',
"android/view/ViewGroup.java": '''package android.view;import android.content.Context;import java.util.*;public class ViewGroup extends View {public boolean clipChildren=true,clipToPadding=true;public boolean getClipChildren(){return clipChildren;}public boolean getClipToPadding(){return clipToPadding;}public final List<View> children=new ArrayList<>();public ViewGroup(Context c){super(c);}public void addView(View v){children.add(v);v.parent=this;}public void removeView(View v){children.remove(v);v.detach();v.parent=null;}public int getChildCount(){return children.size();}public View getChildAt(int i){return children.get(i);}}''',
"android/view/Window.java": '''package android.view;import android.content.Context;public class Window {public final ViewGroup decor;public final WindowInsetsController controller=new WindowInsetsController();private int navigation=0xFFEFEFEF,divider=0xFFDDDDDD,status=0xFFEEEEEE;private boolean contrast=true;public boolean fits=true;public int writes;public Window(Context c){decor=new ViewGroup(c);decor.legacyController=controller;}public View getDecorView(){return decor;}public WindowInsetsController getInsetsController(){return controller;}public int getNavigationBarColor(){return navigation;}public void setNavigationBarColor(int c){navigation=c;writes++;}public int getNavigationBarDividerColor(){return divider;}public void setNavigationBarDividerColor(int c){divider=c;writes++;}public int getStatusBarColor(){return status;}public void setStatusBarColor(int c){status=c;}public boolean isNavigationBarContrastEnforced(){return contrast;}public void setNavigationBarContrastEnforced(boolean b){contrast=b;}public void setDecorFitsSystemWindows(boolean b){fits=b;int mask=View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;decor.ui=b?decor.ui&~mask:decor.ui|mask;}}''',
"android/app/Activity.java": '''package android.app;import android.content.Context;import android.view.*;public class Activity extends Context {public Window window=new Window(this);public boolean destroyed,finishing;public Window getWindow(){return window;}public ClassLoader getClassLoader(){return getClass().getClassLoader();}public boolean isDestroyed(){return destroyed;}public boolean isFinishing(){return finishing;}public View findViewById(int id){return window.getDecorView().findViewById(id);}}''',
"dev/mindlink/xposed/XC_MethodHook.java": '''package dev.mindlink.xposed;public class XC_MethodHook {public static class MethodHookParam {public Object thisObject;public Object[] args=new Object[0];}protected void beforeHookedMethod(MethodHookParam p)throws Throwable{}protected void afterHookedMethod(MethodHookParam p)throws Throwable{}}''',
"dev/mindlink/xposed/XposedBridge.java": '''package dev.mindlink.xposed;import java.util.*;public final class XposedBridge {private static final Map<String,List<XC_MethodHook>> hooks=new HashMap<>();public static void hookAllConstructors(Class<?> c,XC_MethodHook h){hooks.computeIfAbsent("construct",k->new ArrayList<>()).add(h);}public static void log(String s){}public static void hookAllMethods(Class<?> c,String n,XC_MethodHook h){hooks.computeIfAbsent(c.getName()+"#"+n,k->new ArrayList<>()).add(h);}public static void callExact(Class<?> c,String n,Object target,Object... args){dispatch(c.getName()+"#"+n,target,args);}public static void call(String n,Object target,Object... args){if(n.equals("construct")){dispatch(n,target,args);return;}for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())dispatch(c.getName()+"#"+n,target,args);}private static void dispatch(String key,Object target,Object[] args){XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.thisObject=target;p.args=args;try{for(XC_MethodHook h:hooks.getOrDefault(key,List.of()))h.beforeHookedMethod(p);for(XC_MethodHook h:hooks.getOrDefault(key,List.of()))h.afterHookedMethod(p);}catch(Throwable e){throw new AssertionError(e);}}}''',
"wex/ui/core/WexStore.java": "package wex.ui.core;public class WexStore {public static final WexStore INSTANCE=new WexStore();public boolean enabled=true;public boolean getBool(String key,boolean fallback){return enabled;}}",
"wex/ui/core/feature/SettingsTheme.java": "package wex.ui.core.feature;public class SettingsTheme {public static boolean isDark(android.content.Context c){return c.dark;}}",
"wex/ui/core/feature/GlobalBackgroundFeature.java": "package wex.ui.core.feature;public class GlobalBackgroundFeature {public static final java.util.Set<String> BLACKLISTED_ACTIVITIES=new java.util.HashSet<>();}",
"com/tencent/mm/ui/LauncherUI.java": """package com.tencent.mm.ui;public class LauncherUI extends android.app.Activity {public boolean gateAvailable,chatForeground;public final HomeUI home=new HomeUI(this);public boolean e7(){if(!gateAvailable)throw new UnsupportedOperationException(\"unknown host ABI\");return chatForeground;}public HomeUI getHomeUI(){return home;}public void onResume(){}public void onWindowFocusChanged(boolean value){}public void onSettle(boolean open,int position,boolean settle){}public void closeChat(){chatForeground=false;}}""",
"com/tencent/mm/ui/HomeUI.java": """package com.tencent.mm.ui;public class HomeUI {public final LauncherUI activity;public HomeUI(LauncherUI a){activity=a;}public void w(boolean changed){}public void v(int color){activity.window.setStatusBarColor(color);}}""",
"com/tencent/mm/pluginsdk/ui/chat/ChattingUILayout.java": "package com.tencent.mm.pluginsdk.ui.chat;public class ChattingUILayout extends android.view.ViewGroup {public ChattingUILayout(android.content.Context c){super(c);}}",
"com/tencent/mm/ui/widget/EdgeToEdgeWrapperLayout.java": '''package com.tencent.mm.ui.widget;public class EdgeToEdgeWrapperLayout extends android.view.ViewGroup {public int color=0xFFFFFFFF,writes;public boolean throwGetter,throwSetter;public EdgeToEdgeWrapperLayout(android.content.Context c){super(c);}public int getNavigationBarBackgroundColor(){if(throwGetter)throw new IllegalStateException("getter");return color;}public void setNavigationBarBackgroundColor(int c){if(throwSetter)throw new IllegalStateException("setter");color=c;writes++;}}''',
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--source-dir", type=Path, default=FEATURE)
    parser.add_argument("--baseline", action="store_true")
    parser.add_argument("--status-baseline", action="store_true")
    parser.add_argument("--return-baseline", action="store_true")
    parser.add_argument("--review-baseline", action="store_true")
    parser.add_argument("--missing-getter", action="store_true")
    parser.add_argument("--host-baseline", action="store_true")
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=False)
    fixture = args.out / "fixtures"
    classes = args.out / "classes"
    empty = args.out / "empty-sourcepath"
    classes.mkdir()
    empty.mkdir()
    sources = dict(SOURCES)
    if args.missing_getter:
        sources["com/tencent/mm/ui/widget/EdgeToEdgeWrapperLayout.java"] = sources["com/tencent/mm/ui/widget/EdgeToEdgeWrapperLayout.java"].replace('public int getNavigationBarBackgroundColor(){if(throwGetter)throw new IllegalStateException("getter");return color;}', '')
    for name, content in sources.items():
        path = fixture / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
    actual = [args.source_dir / (name + ".java") for name in ("ChatWindowInsetsCoordinator", "SystemBarsCoordinator")]
    actual.append(FEATURE / "InsetGeometry.java")
    manifest = {str(path.resolve()): hashlib.sha256(path.read_bytes()).hexdigest() for path in actual + [SUITE]}
    (args.out / "source-hashes.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    logs = []
    def run(command):
        result = subprocess.run([str(v) for v in command], cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace")
        text = result.stdout + result.stderr
        logs.append(text)
        print(text, end="", flush=True)
        (args.out / "run.log").write_text("".join(logs), encoding="utf-8")
        return result.returncode
    javac = args.jdk / "bin/javac.exe"
    java = args.jdk / "bin/java.exe"
    rc = run([javac, "-J-Duser.language=en", "-encoding", "UTF-8", "-implicit:none", "-sourcepath", empty, "-classpath", classes, "-d", classes, *sorted(fixture.rglob("*.java")), *actual, SUITE])
    if rc:
        return rc
    print("Compiled BOTH actual production coordinators in the same source set", flush=True)
    scenarios = (["host-four-pages", "host-headerless-chat", "host-top-surface", "host-legacy-flags", "host-event", "host-top-release"] if args.host_baseline else
                 ["review-hidden-view", "review-hidden-ancestor", "review-alpha"] if args.review_baseline else
                 ["return-hide", "return-remove"] if args.return_baseline else
                 ["home-light", "home-dark", "home-focus"] if args.status_baseline else
                 ["acquire-wrapper", "resume-ownership"] if args.baseline else
                 ["missing-getter" if args.missing_getter else "green", "status-green", "return-hide", "return-remove", "review-green", "host-green"])
    for scenario in scenarios:
        rc = run([java, "-ea", "-cp", classes, "wex.ui.core.feature.SystemBarsChecks", scenario])
        if args.baseline or args.status_baseline or args.return_baseline or args.review_baseline or args.host_baseline:
            if rc == 0 or "AssertionError" not in logs[-1]:
                raise AssertionError("Baseline must fail the actual contract assertion: " + scenario)
            confirmation = "RED confirmed: original production pair fails " + scenario + "\n"
            logs.append(confirmation)
            print(confirmation, end="", flush=True)
            (args.out / "run.log").write_text("".join(logs), encoding="utf-8")
        elif rc:
            return rc
    return 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    raise SystemExit(main())
