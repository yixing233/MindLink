package wex.ui.core.feature;

import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import com.tencent.mm.ui.widget.MMNeat7extView;
import wex.ui.core.WexStore;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/** Real private BubbleApply entrypoints; fixtures are entirely synthetic, never user images. */
public final class BubbleImageChecks {
    private static int assertions;
    private static Path directory;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private static Object invoke(Method method, Object receiver, Object... args) throws Exception {
        try { return method.invoke(receiver, args); }
        catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw e;
        }
    }
    private static Drawable image(Path path, int width, int height, float corner, int alpha) throws Exception {
        return image(new Resources(), path, width, height, corner, alpha);
    }
    private static Drawable image(Resources resources, Path path, int width, int height, float corner, int alpha) throws Exception {
        byte[] before = Files.readAllBytes(path);
        Method method = BubbleApply.class.getDeclaredMethod("imageDrawable", Resources.class, String.class,
                int.class, int.class, float.class, int.class);
        method.setAccessible(true);
        try {
            return (Drawable) invoke(method, null, resources, path.toString(), width, height, corner, alpha);
        } finally {
            check(Files.isRegularFile(path) && Arrays.equals(before, Files.readAllBytes(path)), "image source is never deleted or rewritten, even on rejection");
        }
    }
    private static BufferedImage content(int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
            image.setRGB(x, y, 0xff000000 | (((x * 17 + 23) & 255) << 16) | (((y * 13 + 31) & 255) << 8) | 0x55);
        return image;
    }
    private static BufferedImage raw(boolean padding) {
        BufferedImage image = new BufferedImage(10, 9, BufferedImage.TYPE_INT_ARGB);
        BufferedImage center = content(8, 7);
        for (int y = 0; y < 7; y++) for (int x = 0; x < 8; x++) image.setRGB(x + 1, y + 1, center.getRGB(x, y));
        // Two separated horizontal stretch segments, one vertical segment.
        for (int x : new int[]{2,3,6,7}) image.setRGB(x, 0, 0xff000000);
        for (int y = 3; y <= 5; y++) image.setRGB(0, y, 0xff000000);
        if (padding) {
            for (int x = 3; x <= 6; x++) image.setRGB(x, 8, 0xff000000);
            for (int y = 2; y <= 6; y++) image.setRGB(9, y, 0xff000000);
        }
        return image;
    }
    private static Path png(String name, BufferedImage bitmap) throws Exception {
        Path path = directory.resolve(name);
        check(ImageIO.write(bitmap, "png", path.toFile()), "synthetic PNG encoder is available");
        return path;
    }
    // Independent fixture wire bytes, never production parser/serializer output.
    private static byte[] compiledChunk(boolean malformed) {
        return compiledChunk(malformed, false);
    }
    private static byte[] compiledChunk(boolean malformed, boolean fullStretch) {
        int colors = fullStretch ? 1 : 9;
        ByteBuffer b = ByteBuffer.allocate(32 + 16 + colors * 4).order(ByteOrder.BIG_ENDIAN);
        b.put((byte)0).put((byte)2).put((byte)2).put((byte)colors);
        b.putInt(0).putInt(0).putInt(fullStretch ? 0 : 2).putInt(fullStretch ? 0 : 1)
                .putInt(fullStretch ? 0 : 1).putInt(fullStretch ? 0 : 2).putInt(0);
        b.putInt(fullStretch ? 0 : 1).putInt(malformed ? 99 : fullStretch ? 8 : 7)
                .putInt(fullStretch ? 0 : 2).putInt(fullStretch ? 7 : 6);
        for (int i = 0; i < colors; i++) b.putInt(1);
        return b.array();
    }
    private static Path compiled(String name, boolean malformed) throws Exception {
        return compiled(name, malformed, false);
    }
    private static Path compiled(String name, boolean malformed, boolean fullStretch) throws Exception {
        Path path = png(name, content(8, 7));
        byte[] png = Files.readAllBytes(path);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        stream.write(png, 0, 33); // append a legal npTc ancillary chunk after IHDR
        byte[] type = {'n','p','T','c'};
        byte[] chunk = compiledChunk(malformed, fullStretch);
        DataOutputStream out = new DataOutputStream(stream);
        out.writeInt(chunk.length);out.write(type);out.write(chunk);
        CRC32 crc = new CRC32();crc.update(type);crc.update(chunk);out.writeInt((int)crc.getValue());
        stream.write(png, 33, png.length - 33);
        Files.write(path, stream.toByteArray());
        return path;
    }
    private static void padding(Drawable d, int l, int t, int r, int b, String message) {
        Rect p = new Rect();d.getPadding(p);
        check(p.left == l && p.top == t && p.right == r && p.bottom == b, message);
    }
    private static void rawChecks() throws Exception {
        // The real picker stores this under .png, so .9 suffix must not be required.
        Path raw = png("bubble_bg_path_self.png", raw(true));
        Drawable d = image(raw, 100, 60, 99, 123);
        check(d instanceof NinePatchDrawable, "strict raw 1px markers must become a NinePatchDrawable, even when picker renamed .png");
        NinePatchDrawable nine = (NinePatchDrawable) d;
        check(nine.bitmap.getWidth() == 8 && nine.bitmap.getHeight() == 7, "raw frame is removed, not rendered or cover-cropped");
        check(nine.bitmap.getPixel(0, 0) == content(8, 7).getRGB(0, 0), "first interior pixel retained after crop");
        check(nine.alpha == 123, "raw nine-patch keeps image opacity");
        padding(nine, 2, 1, 2, 1, "raw content markers map to inset padding");
        ByteBuffer b = ByteBuffer.wrap(nine.chunk).order(ByteOrder.nativeOrder());
        check((nine.chunk[1] & 255) == 4 && (nine.chunk[2] & 255) == 2 && (nine.chunk[3] & 255) == 15,
                "all disjoint stretch runs and grid cell count retained");
        check(b.getInt(32) == 1 && b.getInt(36) == 3 && b.getInt(40) == 5 && b.getInt(44) == 7
                && b.getInt(48) == 2 && b.getInt(52) == 5, "raw stretch coordinates use stripped-image exclusive endpoints");
        for (int i = 56; i < nine.chunk.length; i += 4) check(b.getInt(i) == 1, "generated cells are NO_COLOR, not transparent");
        NinePatchDrawable longMessage = (NinePatchDrawable) image(raw, 720, 240, 0, 255);
        check(Arrays.equals(nine.chunk, longMessage.chunk) && longMessage.bitmap.getWidth() == 8, "message length never changes nine-patch geometry");
        Drawable defaultPadding = image(png("without-content.9.png", raw(false)), 40, 20, 8, 255);
        check(defaultPadding instanceof NinePatchDrawable, "absent content marks use stretch-derived padding");
        padding(defaultPadding, 1, 2, 1, 2, "default content padding follows outer stretch edges");
        BufferedImage fullRaw = raw(false);
        for (int x = 1; x < 9; x++) fullRaw.setRGB(x,0,0xff000000);
        for (int y = 1; y < 8; y++) fullRaw.setRGB(0,y,0xff000000);
        NinePatchDrawable full = (NinePatchDrawable) image(png("full-raw.png",fullRaw),120,70,44,255);
        check((full.chunk[3]&255)==1 && full.chunk.length==52,"raw full-axis stretch follows real aapt2 nonempty cell count");
        padding(full,0,0,0,0,"full raw stretch defaults to valid zero padding");
    }
    private static void compiledChecks() throws Exception {
        Drawable d = image(compiled("compiled-renamed.png", false), 180, 90, 99, 177);
        check(d instanceof NinePatchDrawable, "valid compiled npTc detected by data, without .9 extension");
        NinePatchDrawable nine = (NinePatchDrawable) d;
        check(nine.bitmap.getWidth() == 8 && nine.bitmap.getHeight() == 7 && nine.alpha == 177,
                "compiled image retains bitmap and opacity, ignoring ordinary corner clipping");
        padding(nine, 2, 1, 1, 2, "compiled chunk padding must be passed to drawable instead of empty Rect");
        Drawable full = image(compiled("full-stretch.png", false, true), 140, 70, 22, 255);
        check(full instanceof NinePatchDrawable, "real aapt2 edge-to-edge divs have only one nonempty color cell and remain supported");
        padding(full, 0, 0, 0, 0, "compiled zero content padding is valid");
        Resources dense = new Resources();
        dense.getDisplayMetrics().density = 3f; dense.getDisplayMetrics().densityDpi = 480;
        padding(image(dense, compiled("dense-compiled.png", false), 180, 90, 0, 255),
                2, 1, 1, 2, "imported compiled pixel padding is not multiplied by device density");
        padding(image(dense, png("dense-raw.png", raw(true)), 180, 90, 0, 255),
                2, 1, 2, 1, "imported raw fixed regions and content pixels share target density");
    }
    private static void samplingChecks() throws Exception {
        // Marker rows must be decoded before ordinary-image sampling. Large but synthetic, 64x48.
        BufferedImage raw = new BufferedImage(66, 50, BufferedImage.TYPE_INT_ARGB);
        BufferedImage center = content(64, 48);
        for (int y = 0; y < 48; y++) for (int x = 0; x < 64; x++) raw.setRGB(x+1,y+1,center.getRGB(x,y));
        for (int x = 2; x <= 60; x++) raw.setRGB(x,0,0xff000000);
        for (int y = 2; y <= 44; y++) raw.setRGB(0,y,0xff000000);
        Drawable d = image(png("sample-frame.png", raw), 3, 2, 0, 255);
        check(d instanceof NinePatchDrawable, "tiny target cannot sample away raw stretch marker frame");
        check(((NinePatchDrawable)d).bitmap.getWidth() == 64 && BitmapFactory.lastSample == 1,
                "nine-patch source uses no subsampling");
        d = image(compiled("sample-compiled.png", false), 1, 1, 0, 255);
        check(d instanceof NinePatchDrawable && ((NinePatchDrawable)d).bitmap.getWidth() == 8,
                "compiled geometry is not changed by target-driven inSampleSize");
    }
    private static Object state(View view) throws Exception {
        WexStore.INSTANCE.reset();WexStore.INSTANCE.putBool("bubble_enabled", true);
        WexStore.INSTANCE.putString("bubble_mode", "1");
        BubbleApply.onBackground(view);
        Field states = BubbleApply.class.getDeclaredField("STATES");states.setAccessible(true);
        Object s = ((Map<?,?>)states.get(null)).get(view);
        check(s != null, "real State registered for original host bubble");
        return s;
    }
    private static void deliver(Object s, int mode, Drawable background) throws Exception {
        Class<?> c = s.getClass();
        Field gen = c.getDeclaredField("generation");gen.setAccessible(true);
        BubbleGeneration generation = (BubbleGeneration)gen.get(s);
        long token = generation.next(true, mode);
        WexStore.INSTANCE.putString("bubble_mode", String.valueOf(mode));
        for (String name : new String[]{"width","height"}) {
            Field f = c.getDeclaredField(name);f.setAccessible(true);f.setInt(s,name.equals("width")?100:60);
        }
        Method m = c.getDeclaredMethod("deliver",long.class,int.class,Drawable.class);m.setAccessible(true);
        invoke(m,s,token,mode,background);
    }
    private static void viewPadding(View v, int l, int t, int r, int b, String message) {
        check(v.getPaddingLeft()==l && v.getPaddingTop()==t && v.getPaddingRight()==r && v.getPaddingBottom()==b,message);
    }
    private static void paddingChecks() throws Exception {
        MMNeat7extView view = new MMNeat7extView();
        Drawable original = new StateListDrawable();view.background=original;
        Object s = state(view);
        byte[] chunk = compiledChunk(false);
        Drawable nine = new NinePatchDrawable(new Resources(), android.graphics.Bitmap.createBitmap(8,7,android.graphics.Bitmap.Config.ARGB_8888),
                chunk,new Rect(2,1,1,2),null);
        deliver(s,1,nine);
        viewPadding(view,2,1,1,2,"real State must apply nine-patch content padding, not unconditionally keep native padding");
        deliver(s,1,nine);
        viewPadding(view,2,1,1,2,"repeat delivery never accumulates nine-patch padding");
        deliver(s,1,image(png("ordinary-switch.png",content(8,7)),100,60,10,222));
        viewPadding(view,4,5,6,7,"nine-patch to ordinary image restores host padding");
        deliver(s,1,nine);deliver(s,0,new android.graphics.drawable.GradientDrawable());
        viewPadding(view,4,5,6,7,"nine-patch to glass retains pre-module padding");
        deliver(s,1,nine);deliver(s,2,new android.graphics.drawable.GradientDrawable());
        viewPadding(view,4,5,6,7,"nine-patch to transparent retains pre-module padding");
        deliver(s,1,nine);
        WexStore.INSTANCE.putBool("bubble_enabled",false);
        Method stop=s.getClass().getDeclaredMethod("stop",boolean.class);stop.setAccessible(true);invoke(stop,s,true);
        check(view.background==original,"disable restores exact original drawable");
        viewPadding(view,4,5,6,7,"disable restores exact original host padding");
        WexStore.INSTANCE.putBool("bubble_enabled",true);
        deliver(s,1,nine);
        view.setPadding(11,12,13,14); // host changed padding while custom drawable stayed installed
        WexStore.INSTANCE.putBool("bubble_enabled",false);invoke(stop,s,true);
        viewPadding(view,11,12,13,14,"disable never overwrites newer host padding");
        WexStore.INSTANCE.putBool("bubble_enabled",true);deliver(s,1,nine);
        deliver(s,1,image(png("host-padding-switch.png",content(8,7)),100,60,0,255));
        viewPadding(view,11,12,13,14,"subsequent switch restores newer captured host padding");
        deliver(s,1,nine);
        Drawable replacement=new StateListDrawable();view.setBackground(replacement);view.setPadding(21,22,23,24);
        WexStore.INSTANCE.putBool("bubble_enabled",false);invoke(stop,s,true);
        check(view.background==replacement,"disable never overwrites newer host background");
        viewPadding(view,21,22,23,24,"host replacement owns padding");
        WexStore.INSTANCE.putBool("bubble_enabled",true);view.background=original;view.setPadding(4,5,6,7);
        deliver(s,1,new NinePatchDrawable(new Resources(),android.graphics.Bitmap.createBitmap(8,7,android.graphics.Bitmap.Config.ARGB_8888),
                compiledChunk(false,true),new Rect(0,0,0,0),null));
        viewPadding(view,0,0,0,0,"zero padding is applied even when Drawable.getPadding returns false");
        WexStore.INSTANCE.putBool("bubble_enabled",false);invoke(stop,s,true);
        viewPadding(view,4,5,6,7,"zero padding has the same nonaccumulating restore lifecycle");
    }
    private static void ordinaryChecks() throws Exception {
        BufferedImage transparent = new BufferedImage(10,9,BufferedImage.TYPE_INT_ARGB);
        BufferedImage center=content(8,7);
        for(int y=0;y<7;y++)for(int x=0;x<8;x++)transparent.setRGB(x+1,y+1,center.getRGB(x,y));
        Drawable d=image(png("ordinary-transparent.9.png",transparent),100,60,12,101);
        check(d instanceof BitmapDrawable,"ordinary transparent border and misleading .9 filename must not become nine-patch");
        check(((BitmapDrawable)d).filter && d.alpha==101 && Canvas.lastRadius==12,"ordinary image opacity/filter/corner support preserved");
        check(Canvas.lastDestination.left<0 || Canvas.lastDestination.top<0,"ordinary image still uses centered cover, not stretch");
        BufferedImage[] bad={raw(true),raw(true),raw(true),raw(true),raw(true),raw(true)};
        bad[0].setRGB(0,0,0xff000000); // corner not clear
        bad[1].setRGB(4,0,0x80000000); // translucent marker
        bad[2].setRGB(4,0,0xffff0000); // invalid frame color
        for(int y=1;y<8;y++)bad[3].setRGB(0,y,0); // missing stretch axis
        bad[4].setRGB(4,8,0); // disjoint content run
        bad[5].setRGB(9,7,0xff0000ff); // invalid right frame
        for(int i=0;i<bad.length;i++)check(image(png("invalid-"+i+".9.png",bad[i]),100,60,9,255) instanceof BitmapDrawable,
                "invalid/ambiguous frame remains ordinary image, case "+i);
        d=image(png("ordinary-large.png",content(128,96)),3,2,1,255);
        check(d instanceof BitmapDrawable && BitmapFactory.lastSample>1,"ordinary source still uses bounded decode sampling");
        View root=new View();root.width=400;root.x=120;View bubble=new View();bubble.root=root;bubble.width=100;bubble.x=350;
        Method self=BubbleApply.class.getDeclaredMethod("isSelfBubble",View.class);self.setAccessible(true);
        check((Boolean)invoke(self,null,bubble),"self direction retains root-relative right half");
        bubble.x=140;check(!(Boolean)invoke(self,null,bubble),"peer direction retains root-relative left half");
    }
    private static void guardChecks() throws Exception {
        boolean rejected=false;
        try {image(compiled("bad-chunk.png",true),100,60,0,255);} catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"malformed compiled chunk coordinates rejected, never passed to native nine-patch");
        BufferedImage huge=new BufferedImage(1025,1025,BufferedImage.TYPE_INT_ARGB);
        for(int x=1;x<1024;x++)huge.setRGB(x,0,0xff000000);
        for(int y=1;y<1024;y++)huge.setRGB(0,y,0xff000000);
        BitmapFactory.lastSample=-99;rejected=false;
        try {image(png("budget-raw.png",huge),40,20,0,255);}catch(IllegalArgumentException e){rejected=true;}
        check(rejected && BitmapFactory.lastSample==-99,"raw over-budget nine-patch rejected before whole-image decode, not sampled into ordinary image");
        byte[] good=compiledChunk(false);ByteBuffer nativeChunk=ByteBuffer.wrap(good).order(ByteOrder.BIG_ENDIAN);
        ByteBuffer converted=ByteBuffer.allocate(good.length).order(ByteOrder.nativeOrder());converted.put(good,0,4);
        for(int i=4;i<good.length;i+=4)converted.putInt(i,nativeChunk.getInt(i));good=converted.array();good[0]=1;
        Class<?> helper=Class.forName("wex.ui.core.feature.BubbleNinePatch");
        Method padding=helper.getDeclaredMethod("padding",byte[].class,int.class,int.class);padding.setAccessible(true);
        byte[][] invalid={Arrays.copyOf(good,good.length-1),good.clone(),good.clone(),good.clone(),good.clone()};
        invalid[1][1]=3;ByteBuffer.wrap(invalid[2]).order(ByteOrder.nativeOrder()).putInt(36,0);
        ByteBuffer.wrap(invalid[3]).order(ByteOrder.nativeOrder()).putInt(12,-1);invalid[4][0]=0;
        for(int i=0;i<invalid.length;i++) {
            rejected=false;try{invoke(padding,null,invalid[i],8,7);}catch(IllegalArgumentException e){rejected=true;}
            check(rejected,"strict decoded chunk malformed data rejected, case "+i);
        }
    }
    public static void main(String[] args) throws Exception {
        directory=Paths.get(args[1]);Files.createDirectories(directory);
        String which=args[0];
        if(which.equals("raw")||which.equals("all"))rawChecks();
        if(which.equals("compiled")||which.equals("all"))compiledChecks();
        if(which.equals("sampling")||which.equals("all"))samplingChecks();
        if(which.equals("padding")||which.equals("all"))paddingChecks();
        if(which.equals("ordinary")||which.equals("all"))ordinaryChecks();
        if(which.equals("guards")||which.equals("all"))guardChecks();
        System.out.println("PASS BubbleImage "+which+" ("+assertions+" assertions)");
    }
}
