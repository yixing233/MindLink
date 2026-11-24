package wex.ui.core.feature;

import android.Manifest;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationRequest;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import wex.ui.core.WexStore;

/** Foreground, bounded location fixes and explicit place searches. No background tracking. */
final class WeatherLocation {
    static final String CURRENT = "@current";
    private static final String CACHE = "weather_current_place_v1";
    private static final long MAX_AGE_MS = 120_000L, REFRESH_MS = 30 * 60_000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(8), task -> { Thread t = new Thread(task, "MindLink-weather-place"); t.setDaemon(true); return t; });
    private static long lastRequest;
    private static final java.util.Map<String, List<JSONObject>> SEARCH_CACHE =
            new java.util.LinkedHashMap<String, List<JSONObject>>(16, .75f, true) {
                @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, List<JSONObject>> entry) {
                    return size() > 64;
                }
            };

    interface Result { void complete(List<JSONObject> places, String error); }
    static final class Job {
        volatile boolean cancelled;
        volatile HttpURLConnection connection;
        final List<CancellationSignal> signals = new ArrayList<>();
        Runnable timeout;
        boolean resolving;
        Location best;
        int remaining;
        void cancel() {
            cancelled = true;
            stopFix();
            HttpURLConnection active = connection;
            if (active != null) active.disconnect();
        }
        void stopFix() {
            if (timeout != null) MAIN.removeCallbacks(timeout);
            for (CancellationSignal signal : signals) signal.cancel();
            signals.clear();
        }
    }

    private WeatherLocation() { }

    static String saved() {
        try {
            String raw = WexStore.INSTANCE.getString(CACHE, "");
            if (!raw.isEmpty()) HomeSidePanelData.city(raw);
            return raw;
        } catch (Exception ignored) { return ""; }
    }

    static boolean needsFix() {
        try {
            long age = System.currentTimeMillis() - new JSONObject(saved()).getLong("locatedAt");
            return age < 0 || age >= REFRESH_MS;
        } catch (Exception ignored) { return true; }
    }

    /** Wall-clock changes cannot turn an old device fix into a fresh one. */
    static boolean usable(Location location, long elapsedNanos, float maximumAccuracy) {
        if (location == null || !location.hasAccuracy() || !Float.isFinite(location.getAccuracy())
                || location.getAccuracy() <= 0 || location.getAccuracy() > maximumAccuracy) return false;
        long age = elapsedNanos - location.getElapsedRealtimeNanos();
        return location.getElapsedRealtimeNanos() > 0 && age >= 0 && age <= MAX_AGE_MS * 1_000_000L
                && Double.isFinite(location.getLatitude()) && Math.abs(location.getLatitude()) <= 90
                && Double.isFinite(location.getLongitude()) && Math.abs(location.getLongitude()) <= 180;
    }

    static Job locate(Context context, Result callback) {
        Job job = new Job();
        Context app = context.getApplicationContext();
        MAIN.post(() -> {
            if (job.cancelled) return;
            if (!LocationPrivacy.isEnabled()) { finish(job, callback, null, "请先开启联网数据"); return; }
            if (app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != 0) {
                finish(job, callback, null, "请在系统设置中允许微信使用精确位置，再重试定位"); return;
            }
            LocationManager manager = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
            if (manager == null || !manager.isLocationEnabled()) {
                finish(job, callback, null, "请先开启手机定位服务"); return;
            }
            List<String> providers = new ArrayList<>();
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER}) {
                try {
                    if (!manager.isProviderEnabled(provider)) continue;
                    providers.add(provider);
                    Location cached = manager.getLastKnownLocation(provider);
                    if (usable(cached, SystemClock.elapsedRealtimeNanos(), 1000f)
                            && (job.best == null || cached.getAccuracy() < job.best.getAccuracy())) job.best = cached;
                } catch (IllegalArgumentException | SecurityException ignored) { }
            }
            if (usable(job.best, SystemClock.elapsedRealtimeNanos(), 100f)) { resolve(app, job, callback); return; }
            job.timeout = () -> resolve(app, job, callback);
            MAIN.postDelayed(job.timeout, 20_000L);
            job.remaining = providers.size();
            if (providers.isEmpty()) { finish(job, callback, null, "暂无可用定位来源，请稍后重试或手动选择地点"); return; }
            LocationRequest request = new LocationRequest.Builder(0L).setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
                    .setDurationMillis(20_000L).setMaxUpdates(1).build();
            for (String provider : providers) {
                CancellationSignal signal = new CancellationSignal();
                job.signals.add(signal);
                try {
                    manager.getCurrentLocation(provider, request, signal, MAIN::post, location -> {
                        if (job.cancelled || job.resolving) return;
                        if (usable(location, SystemClock.elapsedRealtimeNanos(), 1000f)
                                && (job.best == null || location.getAccuracy() < job.best.getAccuracy())) job.best = location;
                        if (--job.remaining == 0 || usable(job.best, SystemClock.elapsedRealtimeNanos(), 100f)) resolve(app, job, callback);
                    });
                } catch (IllegalArgumentException | SecurityException error) {
                    if (--job.remaining == 0) resolve(app, job, callback);
                }
            }
        });
        return job;
    }

    private static void resolve(Context app, Job job, Result callback) {
        if (job.cancelled || job.resolving) return;
        job.resolving = true; job.stopFix();
        Location fix = job.best;
        if (!usable(fix, SystemClock.elapsedRealtimeNanos(), 1000f)) {
            finish(job, callback, null, "暂未取得足够准确的新位置，请到开阔处重试或手动选择区县"); return;
        }
        if (!LocationPrivacy.isEnabled()) { finish(job, callback, null, "联网数据已关闭"); return; }
        job.timeout = () -> finishFix(job, callback, fix, null);
        try {
            if (Geocoder.isPresent()) {
                MAIN.postDelayed(job.timeout, 8000L);
                new Geocoder(app, Locale.SIMPLIFIED_CHINESE).getFromLocation(fix.getLatitude(), fix.getLongitude(), 1,
                        new Geocoder.GeocodeListener() {
                            @Override public void onGeocode(List<Address> addresses) {
                                MAIN.post(() -> finishFix(job, callback, fix, addresses.isEmpty() ? null : addresses.get(0)));
                            }
                            @Override public void onError(String error) { MAIN.post(() -> finishFix(job, callback, fix, null)); }
                        });
                return;
            }
        } catch (RuntimeException ignored) { }
        finishFix(job, callback, fix, null);
    }

    private static void finishFix(Job job, Result callback, Location fix, Address address) {
        if (job.cancelled || job.timeout == null) return;
        job.stopFix();
        job.timeout = null;
        try {
            JSONObject place = devicePlace(fix, address);
            keepNearbyCoordinates(fix, place);
            finish(job, callback, java.util.Collections.singletonList(place), "");
        } catch (Exception error) { finish(job, callback, null, "暂未取得可用位置"); }
    }

    private static void keepNearbyCoordinates(Location fix, JSONObject place) {
        try {
            JSONObject previous = HomeSidePanelData.city(saved());
            if (!sameRegion(place, previous)) return;
            double lat = previous.getDouble("latitude"), lon = previous.getDouble("longitude");
            float[] distance = new float[1];
            Location.distanceBetween(fix.getLatitude(), fix.getLongitude(), lat, lon, distance);
            if (distance[0] < 150f) place.put("latitude", lat).put("longitude", lon);
        } catch (Exception ignored) {
            // No previous coordinate to reuse.
        }
    }

    private static boolean sameRegion(JSONObject a, JSONObject b) {
        for (String key : new String[]{"countryCode", "province", "city", "district"})
            if (!a.optString(key, "").equals(b.optString(key, ""))) return false;
        return true;
    }

    static JSONObject devicePlace(Location fix, Address address) throws Exception {
        String country = address == null ? "" : text(address.getCountryCode());
        String province = address == null ? "" : text(address.getAdminArea());
        String city = address == null ? "" : text(address.getLocality());
        String subLocality = address == null ? "" : text(address.getSubLocality());
        String subAdmin = address == null ? "" : text(address.getSubAdminArea());
        boolean china = "CN".equalsIgnoreCase(country);
        if (china && localDistrict(city)) {
            if (!localDistrict(subLocality)) subLocality = city;
            city = "";
        }
        if (city.isEmpty() && (!china || !localDistrict(subAdmin))) city = subAdmin;
        String district = china
                ? localDistrict(subLocality) ? subLocality : localDistrict(subAdmin) && !subAdmin.equals(city) ? subAdmin : ""
                : subAdmin.equals(city) ? "" : subAdmin;
        return HomeSidePanelData.city(new JSONObject().put("cityNum", "").put("countryCode", country)
                .put("province", province).put("city", city).put("district", district)
                .put("latitude", fix.getLatitude()).put("longitude", fix.getLongitude()).put("source", "current")
                .put("accuracy", fix.getAccuracy()).put("locatedAt", System.currentTimeMillis()).toString());
    }

    private static boolean localDistrict(String name) {
        return name.endsWith("区") || name.endsWith("县") || name.endsWith("旗");
    }

    private static String text(String value) { return value == null ? "" : value.trim(); }

    static Job search(String query, Result callback) {
        Job job = new Job();
        String text = query == null ? "" : query.trim();
        if (text.length() < 2 || text.length() > 80) { finish(job, callback, null, "请输入 2 至 80 个字的城市或区县名称"); return job; }
        submit(job, callback, () -> {
            List<JSONObject> cached = SEARCH_CACHE.get(text);
            if (cached != null) return cached;
            JSONArray array = new JSONArray(get(job, "https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1"
                    + "&accept-language=zh-CN&limit=8&q=" + URLEncoder.encode(text, "UTF-8")));
            List<JSONObject> places = new ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                try {
                    String category = item.optString("category", "");
                    if (!"boundary".equals(category) && !"place".equals(category)) continue;
                    JSONObject place = place(item, Double.parseDouble(item.getString("lat")), Double.parseDouble(item.getString("lon")));
                    place.put("source", "manual");
                    String identity = label(place) + ":" + place.getDouble("latitude") + ":" + place.getDouble("longitude");
                    if (seen.add(identity)) places.add(place);
                } catch (Exception ignored) { }
            }
            SEARCH_CACHE.put(text, places);
            return places;
        });
        return job;
    }

    static JSONObject place(JSONObject item, double latitude, double longitude) throws Exception {
        JSONObject address = item.getJSONObject("address");
        String city = first(address, "city", "municipality", "town", "village");
        String district = first(address, "city_district", "county", "district", "borough");
        String province = first(address, "state", "province");
        String country = first(address, "country_code");
        if ("CN".equalsIgnoreCase(country)) {
            String name = item.optString("name", "").trim();
            if (localDistrict(name)) district = name;
            if (localDistrict(city)) {
                if (district.isEmpty()) district = city;
                city = "";
            }
            if (city.isEmpty()) {
                // Some Chinese district boundaries omit their parent from addressdetails.
                for (String part : item.optString("display_name", "").split("[,，]")) {
                    String parent = part.trim();
                    if (!parent.equals(province) && (parent.endsWith("市") || parent.endsWith("自治州") || parent.endsWith("地区"))) {
                        city = parent;
                        break;
                    }
                }
            }
        }
        if (city.isEmpty() && district.isEmpty()) throw new IllegalArgumentException("No local place name");
        return HomeSidePanelData.city(new JSONObject().put("cityNum", "")
                .put("countryCode", country).put("province", province)
                .put("city", city).put("district", district).put("latitude", latitude).put("longitude", longitude)
                .put("source", "manual").toString());
    }

    private static String first(JSONObject value, String... keys) {
        for (String key : keys) { String text = value.optString(key, "").trim(); if (!text.isEmpty()) return text; }
        return "";
    }

    static String label(JSONObject place) {
        java.util.LinkedHashSet<String> parts = new java.util.LinkedHashSet<>();
        for (String key : new String[]{"province", "city", "district"}) {
            String value = place.optString(key, "").trim(); if (!value.isEmpty()) parts.add(value);
        }
        if (parts.isEmpty()) parts.add("当前位置");
        if ("current".equals(place.optString("source", "")) && place.optString("district", "").isEmpty())
            parts.add("区县未识别");
        return String.join(" · ", parts);
    }

    static void saveCurrent(JSONObject place) throws Exception {
        requireConsent();
        String raw = place.toString();
        WexStore.INSTANCE.putString(CACHE, raw);
        if (!raw.equals(WexStore.INSTANCE.getString(CACHE, ""))) throw new IllegalStateException("Location save failed");
    }

    private interface Task { List<JSONObject> run() throws Exception; }
    private static void submit(Job job, Result callback, Task task) {
        try {
            IO.execute(() -> {
                if (job.cancelled) return;
                try { requireConsent(); finish(job, callback, task.run(), ""); }
                catch (Exception error) { finish(job, callback, null, "地点查询未完成，请检查网络后重试"); }
            });
        } catch (java.util.concurrent.RejectedExecutionException error) {
            finish(job, callback, null, "地点查询繁忙，请稍后重试");
        }
    }

    private static void finish(Job job, Result callback, List<JSONObject> values, String error) {
        MAIN.post(() -> {
            if (job.cancelled) return;
            job.stopFix();
            if (!LocationPrivacy.isEnabled()) callback.complete(java.util.Collections.emptyList(), "联网数据已关闭");
            else callback.complete(values == null ? java.util.Collections.emptyList() : values, error);
        });
    }

    private static String get(Job job, String url) throws Exception {
        requireConsent();
        // The one worker serializes explicit searches, at most one per second.
        long wait = 1100L - (SystemClock.elapsedRealtime() - lastRequest);
        if (wait > 0) Thread.sleep(wait);
        if (job.cancelled) throw new InterruptedException();
        requireConsent(); lastRequest = SystemClock.elapsedRealtime();
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        job.connection = connection;
        try {
            connection.setConnectTimeout(8000); connection.setReadTimeout(8000); connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "MindLink/1.0 (+https://github.com/yixing233/MindLink)");
            connection.setRequestProperty("Accept", "application/json");
            if (job.cancelled) throw new InterruptedException();
            if (connection.getResponseCode() != 200) throw new IllegalStateException("Geocoder response");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int n;
                while ((n = input.read(buffer)) != -1) {
                    requireConsent(); if (job.cancelled) throw new InterruptedException();
                    if (out.size() + n > 1024 * 1024) throw new IllegalStateException("Response size");
                    out.write(buffer, 0, n);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally { job.connection = null; connection.disconnect(); }
    }

    private static void requireConsent() { if (!LocationPrivacy.isEnabled()) throw new SecurityException("Network disabled"); }
}
