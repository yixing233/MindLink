package wex.ui.core.feature;

import android.os.Bundle;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import de.robv.android.xposed.XposedBridge;
import wex.ui.core.WexStore;

/** Full WeKit data snapshots; existing endpoints, consent, per-card caches and TTLs are retained. */
final class HomeSidePanelData {
    private static final long WEATHER_TTL = 30 * 60 * 1000L;
    private static final long HITOKOTO_TTL = 5 * 60 * 1000L;
    private static final long ERROR_RETRY_MS = 60 * 1000L;
    private static final long MIN_REFRESH_MS = 1000L;
    private static final ExecutorService IO = new ThreadPoolExecutor(2, 2, 0L,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(16), runnable -> {
                Thread thread = new Thread(runnable, "MindLink-sidebar-data");
                thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private static final Set<String> ACTIVE = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, Attempt> ATTEMPTS = new ConcurrentHashMap<>();

    private HomeSidePanelData() { }

    // Keep the old renderer entry points for its Java editor/legacy callers, not as the Compose model.
    static String weather(HomeSidePanelLayout.Card card) {
        String fingerprint = safe(card.weatherCity).trim();
        Bundle state = weatherState(card, true);
        Cache cache = read("weather", card.id, fingerprint);
        return cache == null ? state.getString("message", "加载中...") : cache.display;
    }

    static String hitokoto(HomeSidePanelLayout.Card card) {
        Bundle state = hitokotoState(card, true);
        Cache cache = read("hitokoto", card.id, hitokotoFingerprint(card));
        return cache == null ? state.getString("message", "加载中...") : cache.display;
    }

    static Bundle weatherState(HomeSidePanelLayout.Card card, boolean requestIfExpired) {
        String configuredCity = safe(card.weatherCity).trim();
        String fingerprint = configuredCity;
        String requestKey = "weather:" + card.id + ":" + fingerprint;
        Cache cached = read("weather", card.id, fingerprint);
        boolean valid = validCity(configuredCity);
        boolean consent = LocationPrivacy.isEnabled();
        if (valid && consent && requestIfExpired && stale(cached, WEATHER_TTL)) {
            request(requestKey, "天气", false,
                    () -> fetchWeather(card.id, configuredCity, fingerprint));
        }
        return state("天气", cached, requestKey, consent, valid ? null : "城市代码无效，请在编辑页设置城市");
    }

    static Bundle hitokotoState(HomeSidePanelLayout.Card card, boolean requestIfExpired) {
        // Capture scalars: an editor must not mutate a queued request's settings or cache identity.
        HomeSidePanelLayout.Card requestCard = hitokotoRequest(card);
        String fingerprint = hitokotoFingerprint(requestCard);
        String requestKey = "hitokoto:" + card.id + ":" + fingerprint;
        Cache cached = read("hitokoto", card.id, fingerprint);
        boolean valid = validHitokoto(requestCard);
        boolean consent = LocationPrivacy.isEnabled();
        if (valid && consent && requestIfExpired && stale(cached, HITOKOTO_TTL)) {
            request(requestKey, "一言", false, () -> fetchHitokoto(requestCard, fingerprint));
        }
        return state("一言", cached, requestKey, consent, valid ? null : "一言分类或长度设置无效");
    }

    static void refreshWeather(HomeSidePanelLayout.Card card) {
        if (card == null || !LocationPrivacy.isEnabled()) {
            HomeSidePanelFeature.refreshOpenDrawers(); return;
        }
        String city = safe(card.weatherCity).trim();
        if (validCity(city)) request("weather:" + card.id + ":" + city, "天气", true,
                () -> fetchWeather(card.id, city, city));
        HomeSidePanelFeature.refreshOpenDrawers();
    }

    static void refreshHitokoto(HomeSidePanelLayout.Card card) {
        if (card == null || !LocationPrivacy.isEnabled()) {
            HomeSidePanelFeature.refreshOpenDrawers(); return;
        }
        HomeSidePanelLayout.Card captured = hitokotoRequest(card);
        String fingerprint = hitokotoFingerprint(captured);
        if (validHitokoto(captured)) request("hitokoto:" + captured.id + ":" + fingerprint,
                "一言", true, () -> fetchHitokoto(captured, fingerprint));
        HomeSidePanelFeature.refreshOpenDrawers();
    }

    private static Bundle state(String label, Cache cache, String requestKey, boolean consent, String invalid) {
        Bundle state = new Bundle();
        boolean refreshing = ACTIVE.contains(requestKey);
        state.putBoolean("refreshing", refreshing);
        Attempt attempt = ATTEMPTS.get(requestKey);
        String message = invalid != null ? invalid : !consent
                ? (cache == null ? "未授权联网；无缓存" : "未授权联网；保留缓存")
                : attempt != null && !attempt.message.isEmpty() ? attempt.message : null;
        if (message == null && cache != null && cache.snapshot == null)
            message = "旧" + label + "缓存缺少完整字段；刷新后恢复";
        if (message != null) {
            state.putString("kind", "error");
            state.putString("message", message);
            if (cache != null && cache.snapshot != null) state.putBundle("cached", toBundle(cache.snapshot));
        } else if (cache != null && cache.snapshot != null) {
            state.putString("kind", "ready");
            state.putBundle("snapshot", toBundle(cache.snapshot));
        } else {
            state.putString("kind", "loading");
        }
        // Display-only v0 caches are not WeatherSnapshot/HitokotoSnapshot. Keep, never fabricate fields.
        if (cache != null && cache.snapshot == null && !cache.display.isEmpty())
            state.putString("legacyDisplay", cache.display);
        return state;
    }

    private static void fetchWeather(String id, String configuredCity, String fingerprint) throws Exception {
        JSONObject city = city(configuredCity);
        String endpoint = weatherEndpoint("0", "0") + "&locationKey=" + encode("weathercn:" + city.getString("cityNum"));
        JSONObject snapshot = parseWeather(get(endpoint, "weatherapi.market.xiaomi.com"),
                city, System.currentTimeMillis());
        String display = weatherName(snapshot.getString("weatherCode")) + "  "
                + snapshot.getString("temperature") + "°  " + snapshot.getString("high") + "°/"
                + snapshot.getString("low") + "°  体感 " + snapshot.getString("feelsLike") + "°";
        write("weather", id, fingerprint, display, snapshot);
    }

    /** Xiaomi v3 parameters verified against CrabTab's src/utils/weather.ts (9e1f0f7).
     * Coordinate queries intentionally omit locationKey: a guessed city ID overrides coordinates. */
    static String weatherEndpoint(String latitude, String longitude) {
        double lat = Double.parseDouble(latitude), lon = Double.parseDouble(longitude);
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180)
            throw new IllegalArgumentException("Invalid weather coordinates");
        return String.format(java.util.Locale.ROOT,
                "https://weatherapi.market.xiaomi.com/wtr-v3/weather/all?latitude=%.4f&longitude=%.4f", lat, lon)
                + "&sign=zUFJoAR2ZVrDy1vF3D07&isGlobal=false&locale=zh_cn&days=5&appKey=weather20151024";
    }

    /** CalendarData's worker-thread bridge. Array contains temperature, condition and emoji. */
    public static String[] fetchLocationWeather(String latitude, String longitude) throws Exception {
        requireConsent();
        String city = WexStore.INSTANCE.getString("weather_city", "当前位置");
        String[] result = parseLocationWeather(get(weatherEndpoint(latitude, longitude), "weatherapi.market.xiaomi.com"));
        requireConsent();
        if (!latitude.equals(WexStore.INSTANCE.getString("wp_lat", ""))
                || !longitude.equals(WexStore.INSTANCE.getString("wp_lon", ""))
                || !city.equals(WexStore.INSTANCE.getString("weather_city", "当前位置")))
            throw new IllegalStateException("Weather location changed during request");
        return new String[]{result[0], result[1], result[2], city, locationWeatherIdentity(latitude, longitude)};
    }

    public static String locationWeatherIdentity(String latitude, String longitude) {
        return "xiaomi_v3:" + latitude + ":" + longitude;
    }

    static String[] parseLocationWeather(String payload) throws Exception {
        JSONObject current = new JSONObject(payload).getJSONObject("current");
        String temperature = numeric(current.getJSONObject("temperature"), "value");
        String code = required(current, "weather");
        if (!code.matches("[0-9]+")) throw new IllegalArgumentException("Invalid weather code");
        required(current, "pubTime");
        return new String[]{String.valueOf(Math.round(Double.parseDouble(temperature))), weatherName(code), weatherEmoji(code)};
    }

    private static String numeric(JSONObject owner, String key) throws Exception {
        String value = required(owner, key);
        if (!Double.isFinite(Double.parseDouble(value))) throw new IllegalArgumentException("Invalid weather value");
        return value;
    }

    /** Mirrors WeKit parseWeatherPayload, including its explicit from=high / to=low mapping. */
    static JSONObject parseWeather(String payload, JSONObject selectedCity, long fetchedAt) throws Exception {
        JSONObject root = new JSONObject(payload);
        JSONObject current = root.getJSONObject("current");
        JSONObject first = root.getJSONObject("forecastDaily").getJSONObject("temperature")
                .getJSONArray("value").getJSONObject(0);
        JSONObject snapshot = new JSONObject()
                .put("city", normalizeCity(selectedCity))
                .put("weatherCode", required(current, "weather"))
                .put("temperature", numeric(current.getJSONObject("temperature"), "value"))
                .put("feelsLike", numeric(current.getJSONObject("feelsLike"), "value"))
                .put("high", numeric(first, "from"))
                .put("low", numeric(first, "to"))
                .put("humidity", numeric(current.getJSONObject("humidity"), "value"))
                .put("windSpeed", numeric(current.getJSONObject("wind").getJSONObject("speed"), "value"))
                .put("publishedAt", required(current, "pubTime"))
                .put("fetchedAt", fetchedAt);
        return normalizeWeather(snapshot);
    }

    private static void fetchHitokoto(HomeSidePanelLayout.Card card, String fingerprint) throws Exception {
        StringBuilder endpoint = new StringBuilder("https://v1.hitokoto.cn/?encode=json&charset=utf-8");
        String categories = safe(card.hitokotoCategories).trim();
        if (categories.isEmpty()) categories = "a,b,c,d,e,f,g,h,i,j,k,l";
        for (String category : categories.split("[,，\\s]+")) endpoint.append("&c=").append(category);
        if (card.hitokotoMinLength != null) endpoint.append("&min_length=").append(card.hitokotoMinLength);
        if (card.hitokotoMaxLength != null) endpoint.append("&max_length=").append(card.hitokotoMaxLength);
        JSONObject snapshot = parseHitokoto(get(endpoint.toString(), "v1.hitokoto.cn"), System.currentTimeMillis());
        String source = snapshot.optString("source", ""), author = snapshot.optString("author", "");
        StringBuilder display = new StringBuilder(snapshot.getString("text"));
        if (card.showHitokotoSource && !source.isEmpty()) display.append("\n—— ").append(source);
        if (card.showHitokotoAuthor && !author.isEmpty())
            display.append(card.showHitokotoSource && !source.isEmpty() ? " · " : "\n—— ").append(author);
        write("hitokoto", card.id, fingerprint, display.toString(), snapshot);
    }

    static JSONObject parseHitokoto(String payload, long fetchedAt) throws Exception {
        JSONObject root = new JSONObject(payload);
        JSONObject snapshot = new JSONObject().put("uuid", required(root, "uuid"))
                .put("text", required(root, "hitokoto")).put("fetchedAt", fetchedAt);
        optional(root, "type", snapshot, "type");
        optional(root, "from", snapshot, "source");
        optional(root, "from_who", snapshot, "author");
        optional(root, "creator", snapshot, "creator");
        optional(root, "created_at", snapshot, "createdAt");
        return normalizeHitokoto(snapshot);
    }

    private static void request(String key, String label, boolean force, Request request) {
        if (!LocationPrivacy.isEnabled() || !ACTIVE.add(key)) return;
        long now = System.currentTimeMillis();
        Attempt previous = ATTEMPTS.get(key);
        long delay = force || previous == null || previous.message.isEmpty() ? MIN_REFRESH_MS : ERROR_RETRY_MS;
        if (previous != null && now >= previous.startedAt && now - previous.startedAt < delay) {
            ACTIVE.remove(key); return;
        }
        ATTEMPTS.put(key, new Attempt(now, ""));
        try {
            IO.execute(() -> {
                try {
                    if (LocationPrivacy.isEnabled()) request.run();
                } catch (Throwable error) {
                    ATTEMPTS.put(key, new Attempt(now, label + "刷新失败；点击重试"));
                    log(label + "刷新失败 " + error.getClass().getSimpleName());
                } finally {
                    ACTIVE.remove(key);
                    HomeSidePanelFeature.refreshOpenDrawers();
                }
            });
        } catch (RejectedExecutionException error) {
            ACTIVE.remove(key);
            ATTEMPTS.put(key, new Attempt(now, "刷新任务繁忙；请稍后重试"));
            HomeSidePanelFeature.refreshOpenDrawers();
        }
    }

    private static String get(String address, String expectedHost) throws Exception {
        requireConsent();
        URL url = new URL(address);
        if (!"https".equals(url.getProtocol()) || !expectedHost.equalsIgnoreCase(url.getHost()))
            throw new IllegalArgumentException("Unexpected endpoint");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(6000); connection.setReadTimeout(6000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            requireConsent();
            int status = connection.getResponseCode();
            if (status != 200) throw new IllegalStateException("HTTP " + status);
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    requireConsent();
                    if (output.size() + count > 1024 * 1024) throw new IllegalStateException("Response too large");
                    output.write(buffer, 0, count);
                }
                return output.toString(StandardCharsets.UTF_8.name());
            }
        } finally { connection.disconnect(); }
    }

    private static Cache read(String type, String id, String fingerprint) {
        try {
            String raw = WexStore.INSTANCE.getString(key(type, id), "");
            if (raw == null || raw.isEmpty()) return null;
            JSONObject value = new JSONObject(raw);
            if (!fingerprint.equals(value.optString("fingerprint", ""))) return null;
            String display = value.optString("display", "");
            JSONObject snapshot = null;
            try {
                JSONObject stored = value.optJSONObject("snapshot");
                if (stored != null) snapshot = "weather".equals(type) ? normalizeWeather(stored) : normalizeHitokoto(stored);
            } catch (Exception ignored) { /* partial new caches remain legacy, never Ready */ }
            if (snapshot == null && display.isEmpty()) return null;
            return new Cache(display, snapshot == null ? value.optLong("fetchedAt", 0) : snapshot.getLong("fetchedAt"), snapshot);
        } catch (Throwable error) { return null; }
    }

    private static void write(String type, String id, String fingerprint, String display, JSONObject snapshot) throws Exception {
        JSONObject value = new JSONObject().put("schemaVersion", 1).put("fingerprint", fingerprint)
                .put("display", display).put("fetchedAt", snapshot.getLong("fetchedAt")).put("snapshot", snapshot);
        // Revocation never deletes the old cache and does not commit a request completed after revocation.
        requireConsent();
        WexStore.INSTANCE.putString(key(type, id), value.toString());
    }

    /** A city number alone is not evidence of a city name, country or a location coordinate. */
    static JSONObject city(String configured) throws Exception {
        String value = safe(configured).trim();
        return normalizeCity(value.startsWith("{") ? new JSONObject(value)
                : new JSONObject().put("cityNum", value));
    }

    private static JSONObject normalizeCity(JSONObject source) throws Exception {
        String cityNum = required(source, "cityNum");
        if (!cityNum.matches("[0-9]{9}")) throw new IllegalArgumentException("Invalid city number");
        JSONObject result = new JSONObject().put("cityNum", cityNum);
        for (String name : new String[]{"countryCode", "province", "city", "district"}) {
            Object value = source.opt(name);
            if (value != null && value != JSONObject.NULL && !(value instanceof String))
                throw new IllegalArgumentException("Invalid city field");
            result.put(name, value instanceof String ? (String) value : "");
        }
        for (String name : new String[]{"latitude", "longitude"}) {
            Object value = source.opt(name);
            if (value != null && value != JSONObject.NULL) {
                if (!(value instanceof Number)) throw new IllegalArgumentException("Invalid coordinate");
                double coordinate = ((Number) value).doubleValue();
                double bound = "latitude".equals(name) ? 90 : 180;
                if (!Double.isFinite(coordinate) || Math.abs(coordinate) > bound)
                    throw new IllegalArgumentException("Invalid coordinate");
                result.put(name, coordinate);
            }
        }
        return result;
    }

    private static JSONObject normalizeWeather(JSONObject source) throws Exception {
        JSONObject result = new JSONObject().put("city", normalizeCity(source.getJSONObject("city")));
        for (String name : new String[]{"weatherCode", "publishedAt"}) result.put(name, required(source, name));
        for (String name : new String[]{"temperature", "feelsLike", "high", "low", "humidity", "windSpeed"})
            result.put(name, numeric(source, name));
        result.put("fetchedAt", fetchedAt(source));
        return result;
    }

    private static JSONObject normalizeHitokoto(JSONObject source) throws Exception {
        JSONObject result = new JSONObject().put("uuid", required(source, "uuid")).put("text", required(source, "text"))
                .put("fetchedAt", fetchedAt(source));
        for (String name : new String[]{"type", "source", "author", "creator", "createdAt"})
            optional(source, name, result, name);
        return result;
    }

    static Bundle toBundle(JSONObject source) {
        Bundle result = new Bundle();
        for (java.util.Iterator<String> names = source.keys(); names.hasNext();) {
            String name = names.next(); Object value = source.opt(name);
            if (value instanceof JSONObject) result.putBundle(name, toBundle((JSONObject) value));
            else if (value instanceof String) result.putString(name, (String) value);
            else if ("fetchedAt".equals(name) && value instanceof Number) result.putLong(name, ((Number) value).longValue());
            else if (value instanceof Number) result.putDouble(name, ((Number) value).doubleValue());
        }
        return result;
    }

    private static String required(JSONObject owner, String key) throws Exception {
        Object value = owner.get(key);
        if (!(value instanceof String) || ((String) value).trim().isEmpty())
            throw new IllegalArgumentException("Incomplete data field");
        return ((String) value).trim();
    }

    private static long fetchedAt(JSONObject owner) throws Exception {
        Object value = owner.get("fetchedAt");
        if (!(value instanceof Number) || ((Number) value).longValue() <= 0)
            throw new IllegalArgumentException("Invalid fetch time");
        return ((Number) value).longValue();
    }

    private static void optional(JSONObject owner, String sourceName, JSONObject target, String targetName) throws Exception {
        Object value = owner.opt(sourceName);
        if (value != null && value != JSONObject.NULL && !(value instanceof String))
            throw new IllegalArgumentException("Invalid optional data field");
        if (value instanceof String && !((String) value).trim().isEmpty()) target.put(targetName, ((String) value).trim());
    }

    private static HomeSidePanelLayout.Card hitokotoRequest(HomeSidePanelLayout.Card card) {
        HomeSidePanelLayout.Card result = new HomeSidePanelLayout.Card();
        result.id = card.id; result.hitokotoCategories = card.hitokotoCategories;
        result.hitokotoMinLength = card.hitokotoMinLength; result.hitokotoMaxLength = card.hitokotoMaxLength;
        result.showHitokotoSource = card.showHitokotoSource; result.showHitokotoAuthor = card.showHitokotoAuthor;
        return result;
    }

    private static boolean validCity(String value) { try { city(value); return true; } catch (Exception ignored) { return false; } }
    private static boolean validHitokoto(HomeSidePanelLayout.Card card) {
        Integer min = card.hitokotoMinLength, max = card.hitokotoMaxLength;
        if (min != null && min < 0 || max != null && max < 0 || min != null && max != null && max < min) return false;
        String categories = safe(card.hitokotoCategories).trim();
        if (categories.isEmpty()) return true;
        for (String value : categories.split("[,，\\s]+")) if (!value.matches("[a-l]")) return false;
        return true;
    }
    private static String hitokotoFingerprint(HomeSidePanelLayout.Card card) {
        return safe(card.hitokotoCategories) + "|" + card.hitokotoMinLength + "|" + card.hitokotoMaxLength
                + "|" + card.showHitokotoSource + "|" + card.showHitokotoAuthor;
    }
    private static boolean stale(Cache cache, long ttl) { return cache == null || cache.snapshot == null || expired(cache.fetchedAt, ttl); }
    private static boolean expired(long at, long ttl) { long now = System.currentTimeMillis(); return at <= 0 || at > now || now - at >= ttl; }
    private static String key(String type, String id) { return "home_side_panel_" + type + "_cache_" + safe(id).replaceAll("[^A-Za-z0-9_-]", ""); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String safe(String value) { return value == null ? "" : value; }
    private static void requireConsent() { if (!LocationPrivacy.isEnabled()) throw new SecurityException("Network consent revoked"); }
    private static String weatherName(String code) {
        switch (code) {
            case "0": return "晴"; case "1": return "多云"; case "2": return "阴";
            case "3": return "阵雨"; case "4": return "雷阵雨"; case "5": return "雷阵雨伴有冰雹";
            case "6": return "雨夹雪"; case "7": return "小雨"; case "8": return "中雨"; case "9": return "大雨";
            case "10": return "暴雨"; case "11": return "大暴雨"; case "12": return "特大暴雨";
            case "13": return "阵雪"; case "14": return "小雪"; case "15": return "中雪"; case "16": return "大雪";
            case "17": return "暴雪"; case "18": return "雾"; case "19": return "冻雨"; case "20": return "沙尘暴";
            case "21": return "小到中雨"; case "22": return "中到大雨"; case "23": return "大到暴雨";
            case "24": return "暴雨到大暴雨"; case "25": return "大暴雨到特大暴雨";
            case "26": return "小到中雪"; case "27": return "中到大雪"; case "28": return "大到暴雪";
            case "29": return "浮尘"; case "30": return "扬沙"; case "31": return "强沙尘暴";
            case "32": return "浓雾"; case "53": return "霾"; default: return "天气 " + code;
        }
    }

    private static String weatherEmoji(String code) {
        switch (code) {
            case "0": return "☀️"; case "1": return "⛅"; case "2": return "☁️";
            case "4": case "5": return "⛈️";
            case "6": case "13": case "14": case "15": case "16": case "17":
            case "26": case "27": case "28": return "🌨️";
            case "3": case "7": case "8": case "9": case "10": case "11": case "12":
            case "19": case "21": case "22": case "23": case "24": case "25": return "🌧️";
            case "18": case "20": case "29": case "30": case "31": case "32": case "53": return "🌫️";
            default: return "🌡️";
        }
    }
    private static void log(String value) { try { XposedBridge.log("WEX: 侧栏数据: " + value); } catch (Throwable ignored) { } }
    private interface Request { void run() throws Exception; }
    private static final class Attempt {
        final long startedAt; final String message;
        Attempt(long startedAt, String message) { this.startedAt = startedAt; this.message = message; }
    }
    private static final class Cache {
        final String display; final long fetchedAt; final JSONObject snapshot;
        Cache(String display, long fetchedAt, JSONObject snapshot) { this.display = display; this.fetchedAt = fetchedAt; this.snapshot = snapshot; }
    }
}
