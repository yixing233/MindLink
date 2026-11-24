package wex.ui.core.feature;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.widget.ActionBarContainer;
import androidx.appcompat.widget.Toolbar;
import com.tencent.mm.plugin.sns.ui.improve.ImproveSnsTimelineUI;
import com.tencent.mm.ui.widget.imageview.WeImageView;
import dev.mindlink.xposed.XposedBridge;

/** Same full-window content / status-padding / native action geometry as the device dump. */
public final class MomentsTopBarChecks {
    static int assertions;
    static void check(boolean value, String reason) { assertions++; if (!value) throw new AssertionError(reason); }

    public static void main(String[] args) throws Throwable {
        ImproveSnsTimelineUI activity = new ImproveSnsTimelineUI();
        activity.getResources().getDisplayMetrics().density = 3f;
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        ViewGroup content = new ViewGroup(activity); content.setId(android.R.id.content);
        ActionBarContainer bar = new ActionBarContainer(activity);
        Toolbar toolbar = new Toolbar(activity);
        ViewGroup back = new ViewGroup(activity); back.setId(700); back.setClickable(true);
        WeImageView arrow = new WeImageView(activity); arrow.setImageDrawable(new ColorDrawable(-1));
        WeImageView camera = new WeImageView(activity); camera.setImageDrawable(new ColorDrawable(-1));
        TextView title = new TextView(activity); title.setId(android.R.id.text1); title.setText("Moments");
        title.setTextColor(0xFF2468AC); title.setAlpha(0f);
        int[] clicks = new int[3];
        back.setOnClickListener(v -> clicks[0]++);
        camera.setOnClickListener(v -> clicks[1]++);
        camera.setOnLongClickListener(v -> { clicks[2]++; return true; });
        decor.addView(content); decor.addView(bar); bar.addView(toolbar);
        toolbar.addView(back); toolbar.addView(camera); toolbar.addView(title); back.addView(arrow);
        decor.layout(0,0,1200,2670); content.layout(0,0,1200,2670); bar.layout(0,0,1200,278);
        toolbar.layout(0,134,1200,278); back.layout(0,0,120,144); arrow.layout(24,0,96,144);
        camera.layout(1032,0,1200,144); title.layout(523,0,676,144); decor.attach();
        camera.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        Drawable nativeToolbar = new ColorDrawable(0x11223344), nativeBar = new ColorDrawable(0x55667788);
        toolbar.setBackground(nativeToolbar); bar.setBackground(nativeBar);
        ColorFilter hostFilter = new ColorFilter(); arrow.setColorFilter(hostFilter);
        activity.getWindow().setStatusBarColor(0xFFAABBCC);

        check(MomentsTopBarCards.findToolbar(decor) == toolbar, "find unique real toolbar outside the content tree");
        check(MomentsTopBarCards.actions(toolbar)[1] == camera, "preserve native long-click camera action");
        MomentsTopBarCards.Session session = new MomentsTopBarCards.Session(activity, toolbar);
        session.cards.configure(ChatGlassSurface.PLAIN, 8, true);
        session.sync();
        check(title.getAlpha() == 0f, "expanded cover keeps native title hidden");
        check(title.getTextColors().getDefaultColor() == 0xFF191919, "light gradient gets readable title color");
        check(arrow.getIconColor() == 0xFF191919, "native return icon matches the material");
        check(camera.getAlpha() == 1f && camera.getLayerType() == View.LAYER_TYPE_SOFTWARE
                        && ((ColorDrawable)camera.getDrawable()).getColor() == 0,
                "native camera keeps accessibility visibility, gestures and its original layer type");
        check(session.cards.left.left == 36 && session.cards.right.right == 1164, "both cards use 12dp outer margins");
        check(session.cards.left.height() == 120 && session.cards.right.height() == 120, "cards fit the 48dp toolbar with breathing room");
        float leftTranslation = back.getTranslationX(), rightTranslation = camera.getTranslationX();
        int colorWrites = camera.colorWrites;
        for (int i=0;i<10;i++) session.sync();
        check(camera.colorWrites == colorWrites, "stable icon state never invalidates every frame");
        check(back.getTranslationX() == leftTranslation && camera.getTranslationX() == rightTranslation, "repeated frames do not accumulate translation");
        check(!bar.getClipChildren() && !bar.getClipToPadding(), "gradient reaches the status bar beyond toolbar bounds");
        session.cards.draw(new Canvas());
        check(session.cards.cameraGlyph != null && session.cards.glyphColor == 0xFF191919,
                "camera glyph is drawn by the independent card layer");
        check(session.cards.band.top == -134 && session.cards.band.bottom == 194, "progressive band covers window top through 1.18 bar heights");
        check(session.cards.bandBackdrop.source == content && session.cards.bandBackdrop.running, "plain cards still sample the sibling content for blur");
        session.cards.configure(ChatGlassSurface.PLAIN, 8, false);
        check(!session.cards.bandBackdrop.running, "disabling blur releases the private sampler");
        session.cards.configure(ChatGlassSurface.PLAIN, 8, true); session.cards.draw(new Canvas());
        check(session.cards.bandBackdrop.source == content && session.cards.bandBackdrop.running, "re-enabling blur restores its explicit content source");
        session.cards.configure(ChatGlassSurface.LIQUID, 8, true);
        check(!session.cards.bandBackdrop.running, "glass material does not keep the redundant sampler");

        Drawable scrolledBar = new ColorDrawable(-1);
        Drawable outlinedCamera = new ColorDrawable(0xFFFFFFFF);
        bar.setBackground(scrolledBar); camera.setImageDrawable(outlinedCamera); camera.setColorFilter(hostFilter);
        arrow.setIconColor(-1); camera.setIconColor(-1);
        camera.setEnableColorFilter(false); camera.setClearColorFilter(true);
        title.setAlpha(0.65f); session.sync();
        check(!camera.p && camera.o, "native drawable filter flags keep the host's semantics");
        Drawable moduleCamera = session.cards.cameraGlyph;
        check(camera.getDrawable() != outlinedCamera && camera.getAlpha() == 1f,
                "host can replace its SVG without changing the visible card glyph or click target");
        for (int i=0;i<10;i++) session.sync();
        check(session.cards.cameraGlyph == moduleCamera, "stable frames reuse the camera glyph");
        check(arrow.getIconColor() == 0xFF191919 && camera.getIconColor() == -1, "scroll keeps back tint and native camera state separate");
        check(bar.getBackground() == null && toolbar.getBackground() == session.cards, "scroll/unread native background writes get redressed");
        check(title.getAlpha() == 0.65f, "transition keeps native title alpha");
        check(session.cards.glyphColor == 0xFF191919, "replacement camera stays readable on light material");
        back.performClick(); camera.performClick(); camera.performLongClick();
        check(clicks[0] == 1 && clicks[1] == 1 && clicks[2] == 1, "back, camera, long-press still invoke original actions");
        SettingsTheme.dark = true; session.sync(); session.cards.draw(new Canvas());
        check(session.cards.glyphColor == 0xFFF2F2F2, "night material gets light camera glyphs");
        SettingsTheme.dark = false; session.sync();
        session.dispose(); session.dispose();
        check(toolbar.getBackground() == nativeToolbar && bar.getBackground() == scrolledBar, "release restores latest host background");
        check(back.getTranslationX() == 0f && camera.getTranslationX() == 0f, "release restores hit-target positions");
        check(camera.getColorFilter() == hostFilter && arrow.getColorFilter() == hostFilter, "release restores native image filters");
        check(camera.getIconColor() == -1 && arrow.getIconColor() == -1, "release restores the host's latest native icon color");
        check(!camera.p && camera.o && camera.getLayerType() == View.LAYER_TYPE_SOFTWARE, "release keeps native flags and restores original layer type");
        check(camera.getDrawable() == outlinedCamera, "release restores the host's latest camera resource");
        check(camera.getAlpha() == 1f, "release reveals native camera again");
        check(title.getTextColors().getDefaultColor() == 0xFF2468AC && title.getAlpha() == 0.65f, "release restores title colors without touching native fade");
        check(bar.getClipChildren() && bar.getClipToPadding(), "release restores ancestor clipping");
        check(toolbar.getViewTreeObserver().listeners.isEmpty(), "release removes pre-draw observer");
        check(activity.getWindow().getStatusBarColor() == 0xFFAABBCC, "release restores status bar color");

        MomentsTopBarCards.init(MomentsTopBarChecks.class.getClassLoader());
        XposedBridge.invoke(activity, "setActionbarFrosted", new Class<?>[]{boolean.class,int.class,int.class,int.class}, true,0,0,45);
        XposedBridge.invoke(activity, "onResume", new Class<?>[0]); Handler.drain();
        check(!activity.frosted, "resume suppresses native flat frosted layer");
        XposedBridge.invoke(activity, "setActionbarFrosted", new Class<?>[]{boolean.class,int.class,int.class,int.class}, true,0,0,100);
        check(!activity.frosted, "scrolled native frosted request stays off while dressed");
        XposedBridge.invoke(activity, "onPause", new Class<?>[0]);
        check(activity.frosted && activity.frostAlpha == 100, "pause restores latest requested native frosted state");
        check(toolbar.getViewTreeObserver().listeners.isEmpty(), "pause releases session observer");
        Activity unrelated = new Activity(); XposedBridge.invoke(unrelated, "onResume", new Class<?>[0]); Handler.drain();
        check(unrelated.getWindow().getDecorView().getViewTreeObserver().listeners.isEmpty(), "other activities are untouched");
        nativeBars();
        System.out.println("PASS: Moments and standard top bars, geometry, native actions and restore ("+assertions+" assertions; host doubles, no GPU claim)");
    }

    private static void nativeBars() throws Throwable {
        Activity activity = new Activity();
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        ViewGroup content = new ViewGroup(activity); content.setId(android.R.id.content);
        ActionBarContainer container = new ActionBarContainer(activity);
        Toolbar toolbar = new Toolbar(activity);
        ViewGroup back = new ViewGroup(activity); back.setId(700); back.setClickable(true);
        ImageView left = new ImageView(activity), share = new ImageView(activity), more = new ImageView(activity);
        Drawable icon = new ColorDrawable(-1);
        left.setImageDrawable(icon); share.setImageDrawable(icon); more.setImageDrawable(icon);
        int[] actions = new int[2];
        share.setOnClickListener(view -> actions[0]++);
        more.setOnLongClickListener(view -> { actions[1]++; return true; });
        decor.addView(content); decor.addView(container); container.addView(toolbar);
        toolbar.addView(more); toolbar.addView(share); toolbar.addView(back); back.addView(left);
        decor.layout(0,0,400,800); content.layout(0,0,400,800); container.layout(0,0,400,88);
        toolbar.layout(0,24,400,88); back.layout(0,0,56,64); left.layout(12,20,36,44);
        share.layout(275,10,325,54); more.layout(325,10,375,54); decor.attach();
        Drawable nativeBackground = new ColorDrawable(0xFFAAAAAA);
        toolbar.setBackground(nativeBackground); container.setBackground(nativeBackground);
        check(NativeTopBarCards.iconTargets(toolbar).equals(java.util.List.of(left, share, more)),
                "sort native actions by position and pick left glyph inside its click target");
        NativeTopBarCards.init();
        XposedBridge.invoke(activity, "onPostResume", new Class<?>[0]);
        NativeTopBarCards.Dressing dressing = (NativeTopBarCards.Dressing) toolbar.getBackground();
        check(dressing.buttons.size() == 3 && dressing.buttons.get(1).right < dressing.buttons.get(2).left,
                "right-hand actions have separate circles with a gap");
        check(dressing.bandBackdrop.source == content && dressing.top == -24 && dressing.bottom > toolbar.getHeight(),
                "band samples the separate scene and extends beyond the toolbar");
        dressing.draw(new Canvas());
        check(dressing.bandBackdrop.running, "the hardware band requests the backdrop");
        share.performClick(); more.performLongClick();
        check(actions[0] == 1 && actions[1] == 1 && share.getDrawable() == icon && more.getDrawable() == icon,
                "original icon, click and long-press survive styling");
        android.widget.EditText search = new android.widget.EditText(activity);
        toolbar.addView(search); search.layout(65,0,265,64);
        toolbar.getViewTreeObserver().listeners.get(0).onPreDraw();
        check(toolbar.getBackground() == nativeBackground && container.getClipChildren() && !dressing.bandBackdrop.running,
                "expanded search restores original background and releases capture");
        toolbar.removeView(search);
        toolbar.getViewTreeObserver().listeners.get(0).onPreDraw();
        check(toolbar.getBackground() instanceof NativeTopBarCards.Dressing, "leaving search restores circles");
        XposedBridge.invoke(activity, "onPause", new Class<?>[0]);
        check(toolbar.getBackground() == nativeBackground && container.getBackground() == nativeBackground
                        && toolbar.getViewTreeObserver().listeners.isEmpty(), "pause restores the native tab and detaches its observer");
    }
}
