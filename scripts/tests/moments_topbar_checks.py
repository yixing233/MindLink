"""Real Moments feature against isolated host doubles; no GPU/device claim."""
import argparse
from pathlib import Path
import runpy
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", required=True, type=Path)
    args = parser.parse_args()
    sources = dict(runpy.run_path(str(ROOT / "scripts/tests/chat-header-toolbar-checks.py"))["SOURCES"])

    def replace(path, old, new):
        assert old in sources[path], (path, old)
        sources[path] = sources[path].replace(old, new)

    replace("android/R.java", "content=16908290", "text1=16908308,content=16908290")
    replace("android/content/res/Resources.java", "public class Resources {", 'public class Resources {public int getIdentifier(String n,String t,String p){return n.equals("actionbar_up_indicator")?700:0;}')
    replace("android/view/View.java", "public void setClickable(boolean v){}", "private boolean clickable;public void setClickable(boolean v){clickable=v;}public boolean isClickable(){return clickable;}")
    replace("android/view/View.java", "public void setOnClickListener(OnClickListener c){}public void setOnLongClickListener(OnLongClickListener c){}", "public OnClickListener click;public OnLongClickListener longClick;public void setOnClickListener(OnClickListener c){click=c;setClickable(true);}public void setOnLongClickListener(OnLongClickListener c){longClick=c;}public boolean isLongClickable(){return longClick!=null;}public boolean performClick(){if(click!=null)click.onClick(this);return click!=null;}public boolean performLongClick(){return longClick!=null&&longClick.onLongClick(this);}")
    replace("android/app/Activity.java", "protected void onResume()", "public String getPackageName(){return \"com.tencent.mm\";}protected void onPostResume(){}protected void onResume()")
    replace("android/view/Window.java", "public int statusBarColor;", "public int statusBarColor;public int getStatusBarColor(){return statusBarColor;}")
    replace("android/view/WindowInsetsController.java", "public int appearance,appearanceMask;", "public int appearance,appearanceMask;public int getSystemBarsAppearance(){return appearance;}")
    replace("android/widget/ImageView.java", "private android.graphics.drawable.Drawable drawable;", "private android.graphics.ColorFilter filter;public android.graphics.ColorFilter getColorFilter(){return filter;}public void setColorFilter(android.graphics.ColorFilter f){filter=f;}private android.graphics.drawable.Drawable drawable;")
    replace("android/widget/TextView.java", "public void setTextColor(int color){}", "private android.content.res.ColorStateList colors=android.content.res.ColorStateList.valueOf(0);public android.content.res.ColorStateList getTextColors(){return colors;}public void setTextColor(int c){setTextColor(android.content.res.ColorStateList.valueOf(c));}public void setTextColor(android.content.res.ColorStateList c){colors=c;}")
    replace("android/graphics/Rect.java", "public void set(Rect other)", "public void set(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}public void set(Rect other)")
    replace("android/graphics/Canvas.java", "public class Canvas {", "public class Canvas {public boolean isHardwareAccelerated(){return true;}")
    replace("android/graphics/PixelFormat.java", "OPAQUE=-1", "TRANSLUCENT=-3,OPAQUE=-1")
    replace("android/graphics/drawable/Drawable.java", "public Object getCallback()", "public void invalidateSelf(){}public android.graphics.Rect getDirtyBounds(){return bounds;}public void getOutline(android.graphics.Outline o){}public Object getCallback()")
    replace("dev/mindlink/xposed/XposedHelpers.java", "public class XposedHelpers {", "public class XposedHelpers {public static Object callMethod(Object target,String name,Object... args){try{Class<?>[] types=new Class<?>[args.length];for(int i=0;i<args.length;i++)types[i]=args[i] instanceof Boolean?boolean.class:args[i] instanceof Integer?int.class:args[i].getClass();return XposedBridge.invoke(target,name,types,args);}catch(Throwable e){throw new RuntimeException(e);}}")
    replace("wex/ui/core/feature/ChatGlassSurface.java", "public int color,mode,glassBlur;", "public boolean running;public android.view.View source;public void refresh(){}public int color,mode,glassBlur;")
    replace("wex/ui/core/feature/ChatGlassSurface.java", "public ViewBackdropSampler sharedSampler(){return new ViewBackdropSampler();}", "public ViewBackdropSampler sharedSampler(){running=true;return new ViewBackdropSampler();}")
    replace("wex/ui/core/feature/ChatGlassSurface.java", "public void stop(){stops++;}public void backdrop(android.view.View source,android.view.View content){}", "public void stop(){stops++;running=false;source=null;}public void backdrop(android.view.View source,android.view.View content){this.source=source;}")
    sources.update({
        "wex/ui/core/feature/LucideIcons.java": "package wex.ui.core.feature;public class LucideIcons {public static android.graphics.Bitmap bitmap(String name,int size,int color,float padding){return new android.graphics.Bitmap();}}",
        "android/graphics/drawable/BitmapDrawable.java": "package android.graphics.drawable;public class BitmapDrawable extends Drawable {public BitmapDrawable(android.content.res.Resources resources,android.graphics.Bitmap bitmap){}}",
        "com/tencent/mm/ui/widget/imageview/WeImageView.java": "package com.tencent.mm.ui.widget.imageview;public class WeImageView extends android.widget.ImageView {private int iconColor=-1;public int colorWrites;public boolean p=true,o;public void setEnableColorFilter(boolean value){p=value;}public void setClearColorFilter(boolean value){o=value;}public WeImageView(android.content.Context c){super(c);}public int getIconColor(){return iconColor;}public void setIconColor(int c){iconColor=c;colorWrites++;}}",
        "android/content/res/ColorStateList.java": "package android.content.res;public class ColorStateList {private int c;public static ColorStateList valueOf(int c){ColorStateList s=new ColorStateList();s.c=c;return s;}public int getDefaultColor(){return c;}}",
        "android/graphics/PorterDuff.java": "package android.graphics;public class PorterDuff {public enum Mode {SRC_IN}}",
        "android/graphics/PorterDuffColorFilter.java": "package android.graphics;public class PorterDuffColorFilter extends ColorFilter {public final int color;public PorterDuffColorFilter(int c,PorterDuff.Mode m){color=c;}}",
        "android/widget/AbsListView.java": "package android.widget;public class AbsListView extends android.view.ViewGroup {public AbsListView(android.content.Context c){super(c);}}",
        "wex/ui/core/feature/ViewBackdropSampler.java": "package wex.ui.core.feature;public class ViewBackdropSampler {}",
        "wex/ui/core/feature/ProgressiveTopBlur.java": "package wex.ui.core.feature;public class ProgressiveTopBlur {public static final float HEIGHT_FACTOR=1.18f;public int draws,top,bottom,releases;public void draw(android.graphics.Canvas c,ViewBackdropSampler s,int w,int t,int b,float d,boolean dark,float a){draws++;top=t;bottom=b;}public void release(){releases++;}}",
        "wex/ui/core/feature/LiquidGlassDecoration.java": "package wex.ui.core.feature;public class LiquidGlassDecoration {public void dropShadow(android.graphics.Canvas c,android.graphics.RectF r,float radius,float density,boolean dark){}public void release(){}}",
        "com/tencent/mm/plugin/sns/ui/improve/ImproveSnsTimelineUI.java": """package com.tencent.mm.plugin.sns.ui.improve;
public class ImproveSnsTimelineUI extends android.app.Activity {
 public boolean frosted;public int frostAlpha;public void setActionbarFrosted(boolean value){frosted=value;}
 public void setActionbarFrosted(boolean value,int a,int b,int alpha){frosted=value;frostAlpha=alpha;}
}
""",
    })
    with tempfile.TemporaryDirectory(prefix="mindlink-moments-") as temp:
        temp = Path(temp)
        files = []
        for name, content in sources.items():
            file = temp / "src" / name
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(content, encoding="utf-8")
            files.append(file)
        files += [ROOT / "java_src/wex/ui/core/feature/MomentsTopBarCards.java",
                  ROOT / "java_src/wex/ui/core/feature/NativeTopBarCards.java",
                  ROOT / "scripts/tests/moments-topbar/wex/ui/core/feature/MomentsTopBarChecks.java"]
        classes = temp / "classes"
        classes.mkdir()
        suffix = ".exe" if (args.jdk / "bin/javac.exe").exists() else ""
        result = subprocess.run([str(args.jdk / f"bin/javac{suffix}"), "-encoding", "UTF-8", "-d", str(classes), *map(str, files)], cwd=ROOT)
        if result.returncode:
            return result.returncode
        return subprocess.run([str(args.jdk / f"bin/java{suffix}"), "-ea", "-cp", str(classes), "wex.ui.core.feature.MomentsTopBarChecks"], cwd=ROOT).returncode


if __name__ == "__main__":
    raise SystemExit(main())
