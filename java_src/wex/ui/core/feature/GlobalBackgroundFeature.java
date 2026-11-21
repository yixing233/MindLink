package wex.ui.core.feature;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import wex.ui.core.WexStore;

/**
 * Activity 级全局背景（移植自 WeKit `ApplyGlobalBackground.kt`，GPL-3.0）。
 *
 * 在每个 Activity 的 DecorView 上维护单一全屏 ImageView 蒙层：
 *  - 黑名单页面（媒体/拍摄/扫描/相册等）不加蒙层；
 *  - 图片查看器附着时临时隐藏；
 *  - 不透明度 1–80%，支持透明状态栏与清除图片。
 */
public final class GlobalBackgroundFeature {

    static final String KEY_ENABLED = "global_bg_enabled";
    static final String KEY_PATH = "global_bg_path";
    static final String KEY_OPACITY = "global_bg_opacity";
    static final String KEY_STATUS_BAR = "global_bg_transparent_status_bar";

    static final int MIN_OPACITY = 1;
    static final int MAX_OPACITY = 80;
    static final float DEFAULT_OPACITY = 0.10f;

    private static final String TAG = "WEX: 全局背景: ";
    private static final String OVERLAY_TAG = "mdl_global_bg_overlay";
    private static final int TAG_URI_KEY = 0x7E000221;

    /** 直接搬自 WeKit 的黑名单（包名前缀按 `com.tencent.mm.` 展开）。 */
    static final Set<String> BLACKLISTED_ACTIVITIES = buildBlacklist();
    private static final Map<Activity, WeakReference<ImageView>> OVERLAYS =
            Collections.synchronizedMap(new HashMap<Activity, WeakReference<ImageView>>());
    private static final Set<View> ATTACHED_IMAGE_VIEWS =
            Collections.synchronizedSet(Collections.newSetFromMap(new java.util.WeakHashMap<View, Boolean>()));

    private GlobalBackgroundFeature() {
    }

    public static void init() {
        try {
            XposedBridge.hookAllMethods(Activity.class, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    apply((Activity) param.thisObject);
                }
            });
            XposedBridge.hookAllMethods(Activity.class, "onWindowFocusChanged", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args.length == 1 && Boolean.TRUE.equals(param.args[0])) {
                        apply((Activity) param.thisObject);
                    }
                }
            });
            log("Activity Hook 已注册");
        } catch (Throwable error) {
            log("注册失败 " + error.getClass().getSimpleName());
        }
    }

    static boolean isEnabled() {
        try {
            return WexStore.INSTANCE.getBool(KEY_ENABLED, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static float opacity() {
        try {
            String raw = WexStore.INSTANCE.getString(KEY_OPACITY, "");
            if (raw != null && raw.length() > 0) {
                float value = Float.parseFloat(raw.trim());
                return Math.min(MAX_OPACITY / 100f, Math.max(MIN_OPACITY / 100f, value));
            }
        } catch (Throwable ignored) {
        }
        return DEFAULT_OPACITY;
    }

    static boolean transparentStatusBar() {
        try {
            return WexStore.INSTANCE.getBool(KEY_STATUS_BAR, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static String imagePath() {
        try {
            return WexStore.INSTANCE.getString(KEY_PATH, "");
        } catch (Throwable ignored) {
            return "";
        }
    }

    static void apply(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()
                || !isEnabled()) {
            return;
        }
        String path = imagePath();
        if (path.length() == 0) {
            removeOverlay(activity);
            return;
        }
        if (BLACKLISTED_ACTIVITIES.contains(activity.getClass().getName())) {
            removeOverlay(activity);
            return;
        }
        ViewGroup decor = activity.getWindow() == null
                ? null : (ViewGroup) activity.getWindow().getDecorView();
        if (decor == null) {
            return;
        }
        try {
            ImageView overlay = findOverlay(decor);
            if (overlay == null) {
                overlay = new ImageView(activity);
                overlay.setTag(OVERLAY_TAG);
                overlay.setScaleType(ImageView.ScaleType.CENTER_CROP);
                overlay.setClickable(false);
                overlay.setFocusable(false);
                overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                overlay.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                overlay.setElevation(100f);
                decor.addView(overlay);
                OVERLAYS.put(activity, new WeakReference<ImageView>(overlay));
            }
            overlay.setVisibility(View.VISIBLE);
            overlay.setAlpha(opacity());
            overlay.setTag(TAG_URI_KEY, path);
            GlobalWallpaper.loadInto(overlay, path);
        } catch (Throwable error) {
            log("应用蒙层失败 " + error.getClass().getSimpleName());
        }
    }

    private static void removeOverlay(Activity activity) {
        WeakReference<ImageView> reference = OVERLAYS.remove(activity);
        ImageView overlay = reference == null ? null : reference.get();
        if (overlay != null && overlay.getParent() instanceof ViewGroup) {
            try {
                ((ViewGroup) overlay.getParent()).removeView(overlay);
            } catch (Throwable ignored) {
            }
        }
    }

    private static ImageView findOverlay(ViewGroup decor) {
        for (int i = 0; i < decor.getChildCount(); i++) {
            View child = decor.getChildAt(i);
            if (child instanceof ImageView && OVERLAY_TAG.equals(child.getTag())) {
                return (ImageView) child;
            }
        }
        return null;
    }

    /** 图片查看器等 MultiTouchImageView 附着时隐藏蒙层，全部离开后恢复。 */
    static void onImageViewAttached(View view, Activity activity) {
        synchronized (ATTACHED_IMAGE_VIEWS) {
            ATTACHED_IMAGE_VIEWS.add(view);
        }
        ImageView overlay = overlayOf(activity);
        if (overlay != null) {
            overlay.setVisibility(View.INVISIBLE);
        }
    }

    static void onImageViewDetached(View view, Activity activity) {
        synchronized (ATTACHED_IMAGE_VIEWS) {
            ATTACHED_IMAGE_VIEWS.remove(view);
        }
        if (ATTACHED_IMAGE_VIEWS.isEmpty()) {
            ImageView overlay = overlayOf(activity);
            if (overlay != null) {
                overlay.setVisibility(View.VISIBLE);
            }
        }
    }

    private static ImageView overlayOf(Activity activity) {
        WeakReference<ImageView> reference = OVERLAYS.get(activity);
        return reference == null ? null : reference.get();
    }

    private static Set<String> buildBlacklist() {
        String host = "com.tencent.mm.";
        Set<String> set = new HashSet<String>();
        set.add(host + "plugin.sns.ui.SnsOnlineVideoActivity");
        set.add(host + "plugin.recordvideo.activity.MMRecordUI");
        set.add(host + "plugin.fav.ui.detail.FavoriteImgDetailUI");
        set.add(host + "plugin.scanner.ui.BaseScanUI");
        set.add(host + "plugin.finder.ui.FinderHomeAffinityUI");
        set.add(host + "plugin.lite.ui.WxaLiteAppLiteUI");
        set.add(host + "ui.chatting.gallery.ImageGalleryUI");
        set.add(host + "ui.chatting.gallery.ImageGalleryGridUI");
        set.add(host + "ui.chatting.gallery.MediaHistoryGalleryUI");
        set.add(host + "plugin.subapp.ui.gallery.GestureGalleryUI");
        set.add(host + "plugin.gallery.picker.view.ImageCropUI");
        set.add(host + "plugin.sns.ui.SnsBrowseUI");
        set.add(host + "plugin.finder.ui.FinderShareFeedRelUI");
        set.add(host + "plugin.gallery.ui.ImagePreviewUI");
        set.add(host + "plugin.gallery.ui.AlbumPreviewUI");
        set.add(host + "plugin.luckymoney.ui.LuckyMoneyBeforeDetailUI");
        set.add(host + "plugin.location_soso.SoSoProxyUI");
        set.add(host + "plugin.finder.feed.ui.FinderProfileTimeLineUI");
        set.add(host + "plugin.sns.ui.SnsGalleryUI");
        set.add(host + "pluginsdk.ui.ProfileHdHeadImg");
        set.add(host + "plugin.brandservice.ui.timeline.preload.ui.TmplWebViewMMUI");
        set.add(host + "plugin.voip.ui.VideoActivity");
        return Collections.unmodifiableSet(set);
    }

    private static void log(String message) {
        try {
            XposedBridge.log(TAG + message);
        } catch (Throwable ignored) {
        }
    }
}
