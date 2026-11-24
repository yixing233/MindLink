package android.location;

public final class LocationRequest {
    public static final int QUALITY_HIGH_ACCURACY = 100;
    public static final class Builder {
        public Builder(long interval) { }
        public Builder setQuality(int quality) { return this; }
        public Builder setDurationMillis(long duration) { return this; }
        public Builder setMaxUpdates(int count) { return this; }
        public LocationRequest build() { return new LocationRequest(); }
    }
}
