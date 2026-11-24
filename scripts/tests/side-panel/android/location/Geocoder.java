package android.location;

import android.content.Context;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;

public final class Geocoder {
    public static final ArrayDeque<GeocodeListener> pending = new ArrayDeque<>();
    public interface GeocodeListener {
        void onGeocode(List<Address> values);
        void onError(String error);
    }
    public Geocoder(Context context, Locale locale) { }
    public static boolean isPresent() { return true; }
    public void getFromLocation(double lat, double lon, int limit, GeocodeListener listener) {
        pending.add(listener);
    }
}
