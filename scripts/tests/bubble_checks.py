"""Bubble-only host checks of real BubbleApply with private Android boundary doubles.

Synthetic PNGs only. This does not validate Android/Skia rendering or Xposed hooks.
The four older bubble checks remain unchanged and run here as well.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
FEATURE = ROOT / "java_src/wex/ui/core/feature"

# Boundary doubles, not a copy of the production marker parser or padding logic.
FAKES = {
"android/graphics/Bitmap.java": r'''package android.graphics;
import java.awt.image.BufferedImage;
public class Bitmap {
 public enum Config { ARGB_8888 }
 public final BufferedImage image; public byte[] chunk; public boolean recycled;private int density=160;
 public Bitmap(BufferedImage image){this.image=image;}
 public static Bitmap createBitmap(int w,int h,Config c){return new Bitmap(new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB));}
 public static Bitmap createBitmap(Bitmap b,int x,int y,int w,int h){Bitmap out=createBitmap(w,h,Config.ARGB_8888);for(int j=0;j<h;j++)for(int i=0;i<w;i++)out.image.setRGB(i,j,b.getPixel(x+i,y+j));return out;}
 public int getWidth(){return image.getWidth();} public int getHeight(){return image.getHeight();}
 public int getPixel(int x,int y){return image.getRGB(x,y);} public void setPixel(int x,int y,int color){image.setRGB(x,y,color);}
 public byte[] getNinePatchChunk(){return chunk;} public void recycle(){recycled=true;}
 public int getDensity(){return density;}public void setDensity(int value){density=value;}
}
''',
"android/graphics/BitmapFactory.java": r'''package android.graphics;
import java.nio.*;import java.nio.file.*;import java.io.*;import javax.imageio.ImageIO;import java.awt.image.BufferedImage;
public final class BitmapFactory {
 public static final class Options {public boolean inJustDecodeBounds,inScaled;public int outWidth,outHeight,inSampleSize=1;}
 public static int lastSample;
 public static Bitmap decodeFile(String path,Options o){try{
  byte[] bytes=Files.readAllBytes(Paths.get(path)); BufferedImage image=ImageIO.read(new ByteArrayInputStream(bytes));if(image==null)return null;
  o.outWidth=image.getWidth();o.outHeight=image.getHeight();if(o.inJustDecodeBounds)return null;
  lastSample=o.inSampleSize;int s=Math.max(1,o.inSampleSize);Bitmap b=Bitmap.createBitmap(Math.max(1,image.getWidth()/s),Math.max(1,image.getHeight()/s),Bitmap.Config.ARGB_8888);
  for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++)b.setPixel(x,y,image.getRGB(x*s,y*s));
  // Android's PNG decoder deserializes on-disk network-order npTc to native order.
  for(int p=8;p+12<=bytes.length;){int n=ByteBuffer.wrap(bytes,p,4).getInt();if(n<0||p+12L+n>bytes.length)break;
   if(bytes[p+4]=='n'&&bytes[p+5]=='p'&&bytes[p+6]=='T'&&bytes[p+7]=='c'){
    byte[] chunk=java.util.Arrays.copyOfRange(bytes,p+8,p+8+n);if(chunk.length>=32){ByteBuffer src=ByteBuffer.wrap(chunk).order(ByteOrder.BIG_ENDIAN);ByteBuffer dest=ByteBuffer.wrap(chunk).order(ByteOrder.nativeOrder());
     for(int i=4;i+4<=chunk.length;i+=4)dest.putInt(i,src.getInt(i));chunk[0]=1;}b.chunk=chunk;
   }p+=12+n;
  }return b;
 }catch(IOException e){return null;}}
}
''',
"android/graphics/BitmapRegionDecoder.java": r'''package android.graphics;
import android.graphics.Rect;import javax.imageio.ImageIO;import java.io.*;import java.awt.image.BufferedImage;
public final class BitmapRegionDecoder {
 private final Bitmap source;private BitmapRegionDecoder(Bitmap source){this.source=source;}
 public static BitmapRegionDecoder newInstance(String path,boolean shareable)throws IOException {BufferedImage image=ImageIO.read(new File(path));return image==null?null:new BitmapRegionDecoder(new Bitmap(image));}
 public Bitmap decodeRegion(Rect r,BitmapFactory.Options o){return Bitmap.createBitmap(source,r.left,r.top,r.right-r.left,r.bottom-r.top);}
 public void recycle(){source.recycle();}
}
''',
"android/graphics/NinePatch.java": r'''package android.graphics;
public final class NinePatch { public static boolean isNinePatchChunk(byte[] b){return b!=null&&b.length>=32&&b[0]==1;} }
''',
"android/graphics/Rect.java": r'''package android.graphics;
public class Rect {public int left,top,right,bottom;public Rect(){}public Rect(int l,int t,int r,int b){set(l,t,r,b);}public void set(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}}
''',
"android/graphics/RectF.java": r'''package android.graphics;
public class RectF {public final float left,top,right,bottom;public RectF(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}}
''',
"android/graphics/Color.java": r'''package android.graphics;
public final class Color {public static final int TRANSPARENT=0,BLACK=0xff000000;public static int alpha(int c){return c>>>24;}}
''',
"android/graphics/Paint.java": r'''package android.graphics;
public class Paint {public static final int ANTI_ALIAS_FLAG=1,FILTER_BITMAP_FLAG=2;public Paint(int flags){}}
''',
"android/graphics/Path.java": r'''package android.graphics;
public class Path {public enum Direction{CW}public float radius;public void addRoundRect(RectF r,float x,float y,Direction d){radius=x;}}
''',
"android/graphics/Canvas.java": r'''package android.graphics;
public class Canvas {public static float lastRadius;public static RectF lastDestination;public Canvas(Bitmap b){}public void clipPath(Path p){lastRadius=p.radius;}public void drawBitmap(Bitmap b,Rect r,RectF d,Paint p){lastDestination=d;}}
''',
"android/graphics/drawable/Drawable.java": r'''package android.graphics.drawable;
import android.graphics.Rect;
public class Drawable {public int alpha=255;public void setAlpha(int a){alpha=a;}public boolean getPadding(Rect out){out.set(0,0,0,0);return false;}}
''',
"android/graphics/drawable/BitmapDrawable.java": r'''package android.graphics.drawable;
import android.graphics.Bitmap;import android.content.res.Resources;
public class BitmapDrawable extends Drawable {public final Bitmap bitmap;public boolean filter;public BitmapDrawable(Resources r,Bitmap b){bitmap=b;}public void setFilterBitmap(boolean b){filter=b;}}
''',
"android/graphics/drawable/NinePatchDrawable.java": r'''package android.graphics.drawable;
import android.graphics.*;import android.content.res.Resources;
public class NinePatchDrawable extends Drawable {
 public final Bitmap bitmap;public final byte[] chunk;private final Rect padding;
 public NinePatchDrawable(Resources r,Bitmap b,byte[] c,Rect p,String name){bitmap=b;chunk=c;
  float scale=b.getDensity()==0?1f:(float)r.getDisplayMetrics().densityDpi/b.getDensity();
  padding=new Rect(Math.round(p.left*scale),Math.round(p.top*scale),Math.round(p.right*scale),Math.round(p.bottom*scale));}
 public boolean getPadding(Rect out){out.set(padding.left,padding.top,padding.right,padding.bottom);return (out.left|out.top|out.right|out.bottom)!=0;}
}
''',
"android/graphics/drawable/GradientDrawable.java": r'''package android.graphics.drawable;
public class GradientDrawable extends Drawable {public float corner;public void setColor(int c){}public void setCornerRadius(float r){corner=r;}public void setStroke(int w,int c){}}
''',
"android/graphics/drawable/StateListDrawable.java": r'''package android.graphics.drawable;
import android.graphics.Rect;
public class StateListDrawable extends Drawable {public boolean getPadding(Rect out){out.set(4,5,6,7);return true;}}
''',
"android/content/res/Resources.java": r'''package android.content.res;
import android.util.DisplayMetrics;
public class Resources {public final DisplayMetrics metrics=new DisplayMetrics();public DisplayMetrics getDisplayMetrics(){return metrics;}public Configuration getConfiguration(){return new Configuration();}}
''',
"android/content/res/Configuration.java": r'''package android.content.res;
public class Configuration {public int uiMode;}
''',
"android/util/DisplayMetrics.java": r'''package android.util;
public class DisplayMetrics {public float density=1;public int densityDpi=160;}
''',
"android/os/Looper.java": r'''package android.os;
public class Looper {private static final Looper MAIN=new Looper();public static Looper getMainLooper(){return MAIN;}public static Looper myLooper(){return MAIN;}}
''',
"android/os/Handler.java": r'''package android.os;
public class Handler {public Handler(Looper l){}public boolean post(Runnable r){r.run();return true;}public boolean postDelayed(Runnable r,long t){return true;}public void removeCallbacks(Runnable r){}}
''',
"android/os/SystemClock.java": r'''package android.os;
public class SystemClock {public static long uptimeMillis(){return 1000;}}
''',
"android/view/ViewTreeObserver.java": r'''package android.view;
public class ViewTreeObserver {public interface OnPreDrawListener{boolean onPreDraw();}public boolean isAlive(){return true;}public void addOnPreDrawListener(OnPreDrawListener l){}public void removeOnPreDrawListener(OnPreDrawListener l){}}
''',
"android/view/View.java": r'''package android.view;
import android.graphics.Rect;import android.graphics.drawable.Drawable;import android.content.res.Resources;
public class View {
 public interface OnAttachStateChangeListener{void onViewAttachedToWindow(View v);void onViewDetachedFromWindow(View v);}
 public Drawable background;public int l=4,t=5,r=6,b=7,width=100,height=60,x,y;public View root=this;public boolean attached=true;public final Resources resources=new Resources();
 public Drawable getBackground(){return background;}public void setBackground(Drawable d){background=d;Rect p=new Rect();if(d!=null&&d.getPadding(p))setPadding(p.left,p.top,p.right,p.bottom);}
 public void setPadding(int l,int t,int r,int b){this.l=l;this.t=t;this.r=r;this.b=b;}
 public int getPaddingLeft(){return l;}public int getPaddingTop(){return t;}public int getPaddingRight(){return r;}public int getPaddingBottom(){return b;}
 public int getWidth(){return width;}public int getHeight(){return height;}public void getLocationInWindow(int[] out){out[0]=x;out[1]=y;}
 public View getRootView(){return root;}public Resources getResources(){return resources;}public boolean isAttachedToWindow(){return attached;}
 public ViewTreeObserver getViewTreeObserver(){return new ViewTreeObserver();}public void addOnAttachStateChangeListener(OnAttachStateChangeListener l){}
}
''',
"com/tencent/mm/ui/widget/MMNeat7extView.java": r'''package com.tencent.mm.ui.widget;
public class MMNeat7extView extends android.view.View {}
''',
"dev/mindlink/xposed/XposedBridge.java": r'''package dev.mindlink.xposed;
public final class XposedBridge {public static void log(String s){}}
''',
"wex/ui/core/feature/BubbleGlass.java": r'''package wex.ui.core.feature;
import android.graphics.Bitmap;import android.graphics.drawable.Drawable;import android.graphics.drawable.GradientDrawable;import android.view.View;
public final class BubbleGlass {
 public static final class Sample {public BubbleSourceStamp source;}
 public static void clearCache(){}public static boolean isCurrent(View v,BubbleSourceStamp s){return true;}
 public static BubbleSourceStamp sourceStamp(View v){return null;}public static Sample capture(View v,float r){return null;}
 public static Bitmap blur(Sample s){return null;}public static Drawable drawable(Bitmap b,Sample s,float c,int a,boolean dark,float density){GradientDrawable d=new GradientDrawable();d.setCornerRadius(c);return d;}
}
''',
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", required=True, type=Path)
    parser.add_argument("--out", type=Path, help="New isolated output directory; retained for RED/GREEN evidence")
    parser.add_argument("--case", action="append", choices=["raw", "compiled", "sampling", "padding", "ordinary", "guards", "all"])
    args = parser.parse_args()
    jdk = {name: args.jdk / "bin" / (name + ".exe") for name in ("java", "javac")}
    for name in jdk:
        if not jdk[name].is_file():
            jdk[name] = args.jdk / "bin" / name
        if not jdk[name].is_file():
            parser.error("Missing JDK executable: " + str(jdk[name]))
    temporary = None
    if args.out:
        args.out.mkdir(parents=True, exist_ok=False)
        work = args.out.resolve()
    else:
        temporary = tempfile.TemporaryDirectory(prefix="mindlink-bubble-")
        work = Path(temporary.name)
    try:
        fake = work / "fake-src"
        classes = work / "classes"
        empty = work / "empty-sourcepath"
        classes.mkdir()
        empty.mkdir()
        for relative, content in FAKES.items():
            path = fake / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
        names = ["BubbleApply", "BubbleSettings", "BubbleGeneration", "BubbleSourceStamp", "BubbleRegion", "BubbleBlur"]
        if (FEATURE / "BubbleNinePatch.java").is_file():
            names.append("BubbleNinePatch")
        production = [FEATURE / (name + ".java") for name in names]
        tests = [ROOT / "scripts/tests/wex/ui/core/feature" / (name + "Checks.java") for name in
                 ("BubbleSettings", "BubbleGeneration", "BubbleBackground", "BubbleBlur", "BubbleImage")]
        sources = sorted(fake.rglob("*.java")) + production + tests + [ROOT / "scripts/tests/wex/ui/core/WexStore.java"]
        hashes = {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in production + tests + [Path(__file__).resolve()]}
        (work / "source-hashes.json").write_text(json.dumps(hashes, indent=2), encoding="utf-8")
        commands = []
        def run(label, command):
            result = subprocess.run(list(map(str, command)), cwd=ROOT, capture_output=True, text=True,
                                    encoding="utf-8", errors="replace", timeout=90)
            output = result.stdout + result.stderr
            (work / (label + ".log")).write_text(output, encoding="utf-8")
            print(output, end="", flush=True)
            commands.append({"label": label, "argv": list(map(str, command)), "exit": result.returncode})
            return result.returncode
        status = run("compile", [jdk["javac"], "-encoding", "UTF-8", "-proc:none", "-implicit:none", "-sourcepath", empty,
                                 "-classpath", classes, "-d", classes, *sources])
        if not status:
            print("Compiled real BubbleApply and bubble helpers; private JVM doubles only, not Android rendering.", flush=True)
            status = 0
            for test in ("BubbleSettings", "BubbleGeneration", "BubbleBackground", "BubbleBlur"):
                status |= run(test, [jdk["java"], "-ea", "-cp", classes, "wex.ui.core.feature." + test + "Checks"])
            for case in args.case or ["all"]:
                status |= run("BubbleImage-" + case, [jdk["java"], "-ea", "-cp", classes,
                             "wex.ui.core.feature.BubbleImageChecks", case, work / ("synthetic-" + case)])
        after = {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in production + tests + [Path(__file__).resolve()]}
        result = {"passed": status == 0 and hashes == after, "commands": commands,
                  "source_changes": [p for p in hashes if hashes[p] != after[p]],
                  "scope": "Real BubbleApply + production helpers, synthetic PNGs and private Android doubles; no device/network/package operations"}
        (work / "result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        print("Evidence: " + str(work), flush=True)
        return 0 if result["passed"] else 1
    finally:
        if temporary:
            temporary.cleanup()


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    raise SystemExit(main())
