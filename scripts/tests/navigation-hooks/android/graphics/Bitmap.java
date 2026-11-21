package android.graphics;
public class Bitmap {
    public enum Config { ARGB_8888 }
    public static Bitmap createBitmap(int w,int h,Config config) { return new Bitmap(); }
    public int getWidth() { return 24; }
    public int getHeight() { return 24; }
    public boolean isRecycled() { return false; }
    public int getPixel(int x,int y) { return 0; }
    public void recycle() { }
}
