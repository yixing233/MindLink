package wex.ui.core.feature;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import dev.mindlink.xposed.XposedBridge;

/** Live home/profile header. Avatar, status and settings have separate click targets, like WeKit. */
public final class ProfileView {
    private ProfileView() { }

    public static View buildBar(Activity activity) {
        return activity == null ? null : new ToolbarOwner(activity);
    }

    /** The smali-facing view/tag stays stable; only the isolated original ToolbarComponent is updated. */
    private static final class ToolbarOwner extends FrameLayout {
        final WeakReference<Activity> owner;
        final Runnable changed = this::update;
        final Runnable awaitIdentity = new Runnable() {
            int attempts;
            @Override public void run() {
                if (!subscribed || !isAttachedToWindow() || ++attempts > 12) return;
                SelfProfile.refresh(false);
                TextStatusBridge.refresh(false);
                if (!SelfProfile.hasAny()) postDelayed(this, 500);
            }
        };
        View toolbar;
        boolean subscribed;
        boolean updating;
        boolean failed;

        ToolbarOwner(Activity activity) {
            super(activity);
            owner = new WeakReference<>(activity);
            setTag("w\u0435\u0445_top_profile");
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            Activity activity = owner.get();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
            if (toolbar == null && !failed) {
                Map<String, Runnable> actions = new LinkedHashMap<>();
                actions.put("open_panel", () -> { Activity target=owner.get(); if(target!=null) HomeSidePanelFeature.open(target); });
                actions.put("open_profile", () -> { Activity target=owner.get(); if(target!=null) openPersonalProfile(target); });
                actions.put("open_status", () -> { Activity target=owner.get(); if(target!=null) TextStatusBridge.open(target); });
                actions.put("refresh_status", () -> TextStatusBridge.refresh(true));
                actions.put("open_settings", () -> { Activity target=owner.get(); if(target!=null) openSettings(target); });
                try {
                    toolbar = SidePanelComposeBridge.buildToolbar(activity, HomeSidePanelSnapshot.toolbar(getContext(), changed),
                            new SidePanelComposeBridge.Callbacks(actions));
                    addView(toolbar, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                } catch (RuntimeException | LinkageError error) { failed = true; report("创建", error); }
            }
            if (toolbar != null) {
                try { SidePanelComposeBridge.lifecycle(toolbar, true); }
                catch (RuntimeException | LinkageError error) { report("恢复", error); }
            }
            if (toolbar == null) ensureNativeProfile();
            if (!subscribed) {
                subscribed = true;
                SelfProfile.subscribe(changed);
                TextStatusBridge.subscribe(changed);
                post(awaitIdentity);
            }
            update();
        }

        @Override protected void onDetachedFromWindow() {
            removeCallbacks(awaitIdentity);
            removeCallbacks(changed);
            HomeSidePanelSnapshot.releaseToolbar(changed);
            SelfProfile.unsubscribe(changed);
            TextStatusBridge.unsubscribe(changed);
            subscribed = false;
            // The status service's subscribe is queued; a detach can beat its registration.
            post(() -> { if (!subscribed) TextStatusBridge.unsubscribe(changed); });
            if (toolbar != null) {
                try { SidePanelComposeBridge.lifecycle(toolbar, false); }
                catch (RuntimeException | LinkageError error) { report("暂停", error); }
                try { SidePanelComposeBridge.dispose(toolbar); }
                catch (RuntimeException | LinkageError error) { report("释放", error); }
                removeView(toolbar);
                toolbar = null;
            }
            super.onDetachedFromWindow();
        }

        private void update() {
            if (!subscribed || toolbar == null || updating) return;
            updating = true;
            try { SidePanelComposeBridge.update(toolbar, HomeSidePanelSnapshot.toolbar(getContext(), changed)); }
            catch (RuntimeException | LinkageError error) { report("更新", error); }
            finally { updating = false; }
        }

        /**
         * The Compose toolbar needs the module package to be visible to WeChat; when that or the
         * payload fails, the top bar would be empty. Keep a clickable native profile instead.
         */
        private void ensureNativeProfile() {
            if (getChildCount() != 0) return;
            Activity activity = owner.get();
            if (activity == null) return;
            try {
                addView(new Header(activity, false),
                        new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
            } catch (RuntimeException | LinkageError error) { report("native fallback", error); }
        }

        private void report(String operation, Throwable error) {
            try { XposedBridge.log("WEX: 顶栏资料: 原组件" + operation + "失败 " + error.getClass().getSimpleName()); }
            catch (Throwable ignored) { }
        }
    }

    public static View buildPanel(Activity activity) {
        return activity == null ? null : new Header(activity, true);
    }

    private static final class Header extends LinearLayout {
        final WeakReference<Activity> owner;
        final ImageView avatar, statusIcon;
        final TextView initial, name, status;
        final boolean panel;
        Bitmap shownAvatar;
        String shownIcon = "";
        final Runnable identityChanged = this::updateIdentity;
        final Runnable statusChanged = this::updateStatus;
        boolean subscribed;
        int retries;
        final Runnable awaitIdentity = new Runnable() {
            @Override public void run() {
                if (!isAttachedToWindow() || !subscribed || ++retries > 12) return;
                SelfProfile.refresh(false);
                TextStatusBridge.refresh(false);
                if (!SelfProfile.hasAny()) postDelayed(this, 500);
            }
        };

        Header(Activity activity, boolean panel) {
            super(activity);
            owner = new WeakReference<>(activity);
            this.panel = panel;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            if (!panel) setTag("w\u0435\u0445_top_profile");
            setPadding(panel ? 0 : dp(this, 12), 0, 0, 0);
            int size = dp(this, panel ? 58 : 44);
            FrameLayout imageSlot = new FrameLayout(activity);
            imageSlot.setBackground(SettingsTheme.round(SettingsTheme.accentSurface(activity), size / 2));
            imageSlot.setClipToOutline(true);
            imageSlot.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            imageSlot.setContentDescription(panel ? "打开个人资料" : "打开首页侧栏");
            imageSlot.setFocusable(true);
            imageSlot.setOnClickListener(view -> {
                Activity target = owner.get();
                if (target == null) return;
                if (panel) openPersonalProfile(target);
                else HomeSidePanelFeature.open(target);
            });
            avatar = new ImageView(activity);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            initial = new TextView(activity);
            initial.setGravity(Gravity.CENTER);
            initial.setTextSize(panel ? 22 : 18);
            initial.setTextColor(SettingsTheme.ink(activity));
            imageSlot.addView(avatar, new FrameLayout.LayoutParams(size, size));
            imageSlot.addView(initial, new FrameLayout.LayoutParams(size, size));
            addView(imageSlot, new LayoutParams(size, size));

            LinearLayout texts = new LinearLayout(activity);
            texts.setOrientation(VERTICAL);
            texts.setGravity(Gravity.CENTER_VERTICAL);
            texts.setMinimumHeight(dp(this, 48));
            texts.setFocusable(true);
            texts.setContentDescription("查看或修改微信状态");
            texts.setOnClickListener(view -> {
                Activity target = owner.get();
                if (target != null) TextStatusBridge.open(target);
            });
            name = text(activity, panel ? 19 : 14);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            texts.addView(name, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            LinearLayout statusRow = new LinearLayout(activity);
            statusRow.setGravity(Gravity.CENTER_VERTICAL);
            statusIcon = new ImageView(activity);
            int iconSize = dp(this, panel ? 22 : 18);
            statusRow.addView(statusIcon, new LayoutParams(iconSize, iconSize));
            status = text(activity, panel ? 12 : 11);
            status.setTextColor(SettingsTheme.sub(activity));
            statusRow.addView(status, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            texts.addView(statusRow, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            LayoutParams textParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            textParams.leftMargin = dp(this, 8);
            addView(texts, textParams);
            if (panel) {
                ImageView gear = new ImageView(activity);
                gear.setImageDrawable(LucideIcons.drawableDp("wex_setting_icon.png", 22, SettingsTheme.sub(activity)));
                gear.setScaleType(ImageView.ScaleType.CENTER);
                gear.setContentDescription("Mdl 设置");
                gear.setFocusable(true);
                gear.setOnClickListener(view -> {
                    Activity target = owner.get();
                    if (target != null) openSettings(target);
                });
                addView(gear, new LayoutParams(dp(this, 48), dp(this, 48)));
            }
            updateIdentity();
            updateStatus();
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!subscribed) {
                subscribed = true;
                SelfProfile.subscribe(identityChanged);
                TextStatusBridge.subscribe(statusChanged);
                retries = 0;
                post(awaitIdentity);
            }
        }

        @Override protected void onDetachedFromWindow() {
            removeCallbacks(awaitIdentity);
            SelfProfile.unsubscribe(identityChanged);
            TextStatusBridge.unsubscribe(statusChanged);
            subscribed = false;
            super.onDetachedFromWindow();
        }

        private void updateIdentity() {
            String nickname = SelfProfile.nickname();
            String next = nickname.isEmpty() ? "微信资料" : nickname;
            if (!TextUtils.equals(name.getText(), next)) name.setText(next);
            Bitmap bitmap = SelfProfile.avatar();
            if (bitmap != shownAvatar) { shownAvatar = bitmap; avatar.setImageBitmap(bitmap); }
            initial.setVisibility(bitmap == null ? VISIBLE : GONE);
            String letter = nickname.isEmpty() ? "我" : nickname.substring(0, nickname.offsetByCodePoints(0, 1));
            if (!TextUtils.equals(initial.getText(), letter)) initial.setText(letter);
        }

        private void updateStatus() {
            TextStatusBridge.Snapshot snapshot = TextStatusBridge.snapshot();
            if (!TextUtils.equals(status.getText(), snapshot.label())) status.setText(snapshot.label());
            boolean hasIcon = snapshot.kind == TextStatusBridge.Kind.READY && !snapshot.iconId.isEmpty();
            statusIcon.setVisibility(hasIcon ? VISIBLE : GONE);
            if (!Objects.equals(shownIcon, snapshot.iconId)) {
                shownIcon = snapshot.iconId;
                TextStatusBridge.renderIcon(statusIcon, snapshot);
            }
        }
    }

    static void openSettings(Activity activity) {
        try {
            Class<?> helper = SettingsShell.loadModuleClassOrNull(activity, "wex.ui.core.xposed.SettingsPanelHelper");
            if (helper == null) throw new IllegalStateException("Settings helper unavailable");
            Object instance = helper.getField("INSTANCE").get(null);
            Method show = null;
            for (Method candidate : helper.getDeclaredMethods()) {
                if (candidate.getName().equals("show") && candidate.getParameterCount() == 2
                        && candidate.getParameterTypes()[0] == Activity.class) { show = candidate; break; }
            }
            if (show == null) throw new NoSuchMethodException("SettingsPanelHelper.show");
            Class<?> callbackType = show.getParameterTypes()[1];
            Object callback = Proxy.newProxyInstance(callbackType.getClassLoader(), new Class<?>[]{callbackType},
                    (proxy, method, args) -> {
                        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        if (method.getName().equals("equals")) return args != null && args.length == 1 && proxy == args[0];
                        if (method.getName().equals("toString")) return "MindLinkSettingsCallback";
                        return null;
                    });
            show.setAccessible(true);
            show.invoke(instance, activity, callback);
        } catch (Exception | LinkageError error) {
            XposedBridge.log("WEX: 顶栏资料: 设置入口失败 " + error.getClass().getSimpleName());
            ImagePicker.INSTANCE.toast(activity, "设置面板不可用");
        }
    }

    static void openPersonalProfile(Activity activity) {
        for (String target : new String[]{"com.tencent.mm.plugin.setting.ui.setting_new.CommonSettingsUI",
                "com.tencent.mm.plugin.setting.ui.setting.SettingsPersonalInfoUI"}) {
            try {
                Intent intent = new Intent().setClassName(activity.getPackageName(), target)
                        .putExtra("key_config_item", "SettingGroup_Main_PersonalInfo");
                if (intent.resolveActivity(activity.getPackageManager()) == null) continue;
                activity.startActivity(intent); return;
            } catch (android.content.ActivityNotFoundException ignored) { }
        }
        ImagePicker.INSTANCE.toast(activity, "个人资料页不可用");
    }

    private static TextView text(Activity activity, float sp) {
        TextView text = new TextView(activity);
        text.setTextSize(sp); text.setTextColor(SettingsTheme.ink(activity));
        text.setSingleLine(true); text.setEllipsize(TextUtils.TruncateAt.END);
        return text;
    }

    private static int dp(View view, int value) { return Math.round(view.getResources().getDisplayMetrics().density * value); }
}
