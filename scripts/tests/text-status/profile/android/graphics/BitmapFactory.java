package android.graphics;
public final class BitmapFactory {
    public static class Options {
        public boolean inJustDecodeBounds;
        public int outWidth, outHeight, inSampleSize;
    }
    public static Bitmap decodeByteArray(byte[] data, int offset, int length, Options options) {
        throw new AssertionError("No avatar decoding is permitted in account readiness checks");
    }
}
