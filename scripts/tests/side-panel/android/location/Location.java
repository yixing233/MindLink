package android.location;

public final class Location {
    public final double latitude, longitude;
    public final float accuracy;
    public final long elapsed;
    public Location(double latitude, double longitude, float accuracy, long elapsed) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.elapsed = elapsed;
    }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public float getAccuracy() { return accuracy; }
    public boolean hasAccuracy() { return true; }
    public long getElapsedRealtimeNanos() { return elapsed; }
    public static void distanceBetween(double lat1, double lon1, double lat2, double lon2, float[] result) {
        result[0] = (float) (111000 * Math.hypot(lat1 - lat2, lon1 - lon2));
    }
}
