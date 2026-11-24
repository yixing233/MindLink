package android.location;

import android.os.CancellationSignal;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class LocationManager {
    public static final LocationManager INSTANCE = new LocationManager();
    public static final String GPS_PROVIDER = "gps", NETWORK_PROVIDER = "network", FUSED_PROVIDER = "fused";
    public static boolean permission = true;
    public static Location cached, fresh;
    public boolean isLocationEnabled() { return true; }
    public boolean isProviderEnabled(String provider) { return GPS_PROVIDER.equals(provider); }
    public Location getLastKnownLocation(String provider) { return cached; }
    public void getCurrentLocation(String provider, LocationRequest request, CancellationSignal signal,
                                   Executor executor, Consumer<Location> consumer) {
        executor.execute(() -> { if (!signal.cancelled) consumer.accept(fresh); });
    }
}
