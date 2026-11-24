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
import androidx.appcompat.widget.ActionBarOverlayLayout;
import androidx.appcompat.widget.Toolbar;
import com.tencent.mm.plugin.sns.ui.improve.ImproveSnsTimelineUI;
import com.tencent.mm.ui.FrostedContentView;
import com.tencent.mm.ui.LauncherUI;
import com.tencent.mm.ui.base.CustomViewPager;
import com.tencent.mm.ui.conversation.ConversationListView;
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
        nativeBarsOpaque();
        nativeBarsOverlay();
        homeTopBarCards();
        homeContactsDiscoverResumeNoJump();
        System.out.println("PASS: Moments, standard, and home top bars, geometry, icon centering and restore ("+assertions+" assertions; host doubles, no GPU claim)");
    }

    private static void nativeBarsOpaque() throws Throwable {
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
        decor.layout(0,0,400,800); content.layout(0,88,400,800); container.layout(0,0,400,88);
        toolbar.layout(0,24,400,88); back.layout(0,0,56,64); left.layout(12,20,36,44);
        share.layout(275,10,325,54); more.layout(325,10,375,54); decor.attach();
        Drawable nativeBackground = new ColorDrawable(0xFFEDEDED);
        toolbar.setBackground(nativeBackground); container.setBackground(nativeBackground);
        check(NativeTopBarCards.iconTargets(toolbar).equals(java.util.List.of(left, share, more)),
                "sort native actions by position and pick left glyph inside its click target");
        NativeTopBarCards.init();
        XposedBridge.invoke(activity, "onPostResume", new Class<?>[0]);
        NativeTopBarCards.Dressing dressing = (NativeTopBarCards.Dressing) toolbar.getBackground();
        check(!dressing.overlay && container.getBackground() == nativeBackground,
                "standard opaque page preserves container background and skips overlay mode");
        check(dressing.buttons.size() == 3 && dressing.buttons.get(1).right < dressing.buttons.get(2).left,
                "right-hand actions have separate circles with a gap");
        check(dressing.buttons.get(0).left >= 10f, "left circle keeps outer margin off screen edge");
        check(Math.abs(dressing.buttons.get(0).width() - dressing.buttons.get(1).width()) < 0.1f,
                "left and right circles share equal radius");
        int[] tAt = new int[2], lAt = new int[2];
        toolbar.getLocationInWindow(tAt); left.getLocationInWindow(lAt);
        float leftCenter = lAt[0] - tAt[0] + left.getWidth() / 2f;
        check(left.getTranslationX() > 0f && Math.abs(leftCenter - dressing.buttons.get(0).centerX()) < 0.5f,
                "left icon is translated to center inside the inset circle");
        float shiftedX = left.getTranslationX();
        toolbar.getViewTreeObserver().listeners.get(0).onPreDraw();
        check(Math.abs(left.getTranslationX() - shiftedX) < 0.1f, "repeated frames do not drift icon translation");
        dressing.draw(new Canvas());
        check(!dressing.bandBackdrop.running && dressing.band.draws == 0,
                "opaque standard bar never draws progressive blur band");
        share.performClick(); more.performLongClick();
        check(actions[0] == 1 && actions[1] == 1 && share.getDrawable() == icon && more.getDrawable() == icon,
                "original icon, click and long-press survive styling");
        android.widget.EditText search = new android.widget.EditText(activity);
        toolbar.addView(search); search.layout(65,0,265,64);
        toolbar.getViewTreeObserver().listeners.get(0).onPreDraw();
        check(toolbar.getBackground() == nativeBackground && container.getClipChildren()
                        && back.getClipChildren() && left.getTranslationX() == 0f && !dressing.bandBackdrop.running,
                "expanded search restores original background, clipping and icon translation");
        toolbar.removeView(search);
        toolbar.getViewTreeObserver().listeners.get(0).onPreDraw();
        check(toolbar.getBackground() instanceof NativeTopBarCards.Dressing, "leaving search restores circles");
        wex.ui.core.WexStore.INSTANCE.flags.put(HomeTopBarCards.KEY_ENABLED, false);
        NativeTopBarCards.refresh(); Handler.drain();
        check(toolbar.getBackground() == nativeBackground && left.getTranslationX() == 0f,
                "disabling top_floating_cards restores native top bar");
        wex.ui.core.WexStore.INSTANCE.flags.put(HomeTopBarCards.KEY_ENABLED, true);
        NativeTopBarCards.refresh(); Handler.drain();
        check(toolbar.getBackground() instanceof NativeTopBarCards.Dressing,
                "re-enabling top_floating_cards restores circles");
        XposedBridge.invoke(activity, "onPause", new Class<?>[0]);
        check(toolbar.getBackground() == nativeBackground && container.getBackground() == nativeBackground
                        && left.getTranslationX() == 0f && toolbar.getViewTreeObserver().listeners.isEmpty(),
                "pause restores the native tab, icon shift and detaches its observer");
    }

    private static void nativeBarsOverlay() throws Throwable {
        Activity activity = new Activity();
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        ViewGroup content = new ViewGroup(activity); content.setId(android.R.id.content);
        ActionBarContainer container = new ActionBarContainer(activity);
        Toolbar toolbar = new Toolbar(activity);
        ViewGroup back = new ViewGroup(activity); back.setId(700); back.setClickable(true);
        ImageView left = new ImageView(activity), more = new ImageView(activity);
        Drawable icon = new ColorDrawable(-1);
        left.setImageDrawable(icon); more.setImageDrawable(icon);
        more.setOnClickListener(view -> {});
        decor.addView(content); decor.addView(container); container.addView(toolbar);
        toolbar.addView(more); toolbar.addView(back); back.addView(left);
        decor.layout(0,0,400,800); content.layout(0,0,400,800); container.layout(0,0,400,88);
        toolbar.layout(0,24,400,88); back.layout(0,0,56,64); left.layout(12,20,36,44);
        more.layout(325,10,375,54); decor.attach();
        toolbar.setBackground(null); container.setBackground(null);
        NativeTopBarCards.init();
        XposedBridge.invoke(activity, "onPostResume", new Class<?>[0]);
        NativeTopBarCards.Dressing dressing = (NativeTopBarCards.Dressing) toolbar.getBackground();
        check(dressing.overlay && dressing.bandBackdrop.source == content
                        && dressing.top == -24 && dressing.bottom > toolbar.getHeight(),
                "true transparent overlay bar enables band and samples the full-window content");
        dressing.draw(new Canvas());
        check(dressing.bandBackdrop.running && dressing.band.draws > 0,
                "the hardware band requests backdrop and draws in overlay mode");
        XposedBridge.invoke(activity, "onPause", new Class<?>[0]);
        check(toolbar.getBackground() == null && container.getBackground() == null && !dressing.bandBackdrop.running,
                "pause restores transparent overlay bar and stops backdrop capture");
    }

    private static void homeTopBarCards() {
        Activity activity = new Activity();
        activity.getResources().getDisplayMetrics().density = 3f;
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        Toolbar toolbar = new Toolbar(activity);
        ViewGroup plusAction = new ViewGroup(activity); plusAction.setClickable(true);
        WeImageView plusIcon = new WeImageView(activity); plusIcon.setImageDrawable(new ColorDrawable(-1));
        ViewGroup searchAction = new ViewGroup(activity); searchAction.setClickable(true);
        WeImageView searchIcon = new WeImageView(activity); searchIcon.setImageDrawable(new ColorDrawable(-1));
        decor.addView(toolbar);
        // Add plus before search to verify sorting pairs icons with their own slots.
        toolbar.addView(plusAction); plusAction.addView(plusIcon);
        toolbar.addView(searchAction); searchAction.addView(searchIcon);
        decor.layout(0,0,1200,2670);
        toolbar.layout(0,134,1200,278);
        searchAction.layout(912,0,1056,144); searchIcon.layout(48,36,120,108);
        plusAction.layout(1056,0,1200,144); plusIcon.layout(24,36,96,108);
        decor.attach();

        HomeTopBarCards.Cards cards = new HomeTopBarCards.Cards(toolbar);
        check(cards.layout(toolbar), "initial home card layout produces button circles");
        check(cards.rightButtons.size() == 2, "home top bar draws two separate circles for search and plus");
        check(searchIcon.getAlpha() == 1f && plusIcon.getAlpha() == 1f,
                "native search and plus icons stay visible so host draws unclipped glyphs");
        int[] origin = new int[2], sAt = new int[2], pAt = new int[2];
        toolbar.getLocationInWindow(origin);
        searchIcon.getLocationInWindow(sAt); plusIcon.getLocationInWindow(pAt);
        float searchCenter = sAt[0] - origin[0] + searchIcon.getWidth() / 2f;
        float plusCenter = pAt[0] - origin[0] + plusIcon.getWidth() / 2f;
        check(Math.abs(searchCenter - cards.rightButtons.get(0).centerX()) < 0.5f,
                "asymmetric search icon is shifted to the center of the search circle");
        check(Math.abs(plusCenter - cards.rightButtons.get(1).centerX()) < 0.5f,
                "asymmetric plus icon is shifted to the center of the plus circle");
        check(!searchAction.getClipChildren() && !plusAction.getClipChildren(),
                "action containers release clipping for shifted icons");
        float sTx = searchIcon.getTranslationX(), pTx = plusIcon.getTranslationX();
        check(!cards.layout(toolbar) && searchIcon.getTranslationX() == sTx && plusIcon.getTranslationX() == pTx,
                "repeated layout passes remain stable with zero drift");
        cards.draw(new Canvas());
        searchAction.setVisibility(View.GONE);
        check(cards.layout(toolbar) && cards.rightButtons.size() == 1,
                "hiding search button leaves only plus circle");
        check(searchIcon.getTranslationX() == 0f && searchAction.getClipChildren(),
                "hiding search button restores its icon shift and clipping");
        cards.stop();
        check(plusIcon.getTranslationX() == 0f && plusAction.getClipChildren(),
                "stop restores remaining icon shift and clipping");
    }

    private static void homeContactsDiscoverResumeNoJump() throws Throwable {
        wex.ui.core.WexStore.INSTANCE.flags.put(HomeTopBarCards.KEY_ENABLED, true);
        wex.ui.core.WexStore.INSTANCE.flags.put(HomeTopBarCards.KEY_OVERLAY, true);
        LauncherUI activity = new LauncherUI();
        activity.getResources().getDisplayMetrics().density = 3f;
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        ActionBarOverlayLayout overlay = new ActionBarOverlayLayout(activity);
        ViewGroup content = new ViewGroup(activity); content.setId(android.R.id.content);
        CustomViewPager pager = new CustomViewPager(activity);
        ActionBarContainer bar = new ActionBarContainer(activity);
        Toolbar toolbar = new Toolbar(activity);
        ViewGroup searchAction = new ViewGroup(activity); searchAction.setClickable(true);
        WeImageView searchIcon = new WeImageView(activity); searchIcon.setImageDrawable(new ColorDrawable(-1));

        FrostedContentView convFrosted = new FrostedContentView(activity);
        ConversationListView convList = new ConversationListView(activity);
        convFrosted.addView(convList);

        FrostedContentView contactsFrosted = new FrostedContentView(activity);
        android.widget.ListView contactsList = new android.widget.ListView(activity);
        contactsFrosted.addView(contactsList);

        ViewGroup discoverPage = new ViewGroup(activity);
        FrostedContentView discoverFrosted = new FrostedContentView(activity);
        android.widget.ListView discoverList = new android.widget.ListView(activity);
        discoverFrosted.addView(discoverList);
        discoverPage.addView(discoverFrosted);

        pager.addView(convFrosted);
        pager.addView(contactsFrosted);
        pager.addView(discoverPage);
        content.addView(pager);
        toolbar.addView(searchAction); searchAction.addView(searchIcon);
        bar.addView(toolbar);
        overlay.addView(content);
        overlay.addView(bar);
        decor.addView(overlay);

        decor.layout(0, 0, 1200, 2670);
        overlay.layout(0, 0, 1200, 2670);
        content.layout(0, 0, 1200, 2670);
        content.setPadding(0, 278, 0, 0);
        pager.layout(0, 0, 1200, 2670);
        bar.layout(0, 0, 1200, 278);
        toolbar.layout(0, 134, 1200, 278);
        searchAction.layout(1056, 0, 1200, 144);
        searchIcon.layout(24, 36, 96, 108);

        convFrosted.layout(0, 0, 1200, 2670);
        convFrosted.setPadding(0, 278, 0, 0);
        convList.layout(0, 0, 1200, 2670);

        contactsFrosted.layout(1200, 0, 2400, 2670);
        contactsFrosted.setPadding(0, 278, 0, 168);
        contactsList.layout(0, 278, 1200, 2670);

        discoverPage.layout(2400, 0, 3600, 2670);
        discoverFrosted.layout(0, 0, 1200, 2670);
        discoverFrosted.setPadding(0, 278, 0, 168);
        discoverList.layout(0, 278, 1200, 2670);

        overlay.setOverlayMode(false);
        pager.setCurrentItem(0);
        decor.attach();

        HomeTopBarCards.init();
        XposedBridge.invoke(activity, "onResume", new Class<?>[0]);
        Handler.drain();

        check(!toolbar.getViewTreeObserver().listeners.isEmpty(), "home top bar installs pre-draw listener");
        android.view.ViewTreeObserver.OnPreDrawListener listener =
                toolbar.getViewTreeObserver().listeners.get(0);
        check(convFrosted.getPaddingTop() == 278 && content.getPaddingTop() == 0 && overlay.isInOverlayMode(),
                "chats page never lifts its own FrostedContentView while overlay mode is active");

        // Switch to Contacts (slot 1): lift FrostedContentView first, then pad inner list on next layout.
        pager.setCurrentItem(1);
        check(!listener.onPreDraw(), "lifting Contacts FrostedContentView cancels stale double-padded frame");
        check(contactsFrosted.getPaddingTop() == 0 && contactsFrosted.getPaddingBottom() == 168
                && contactsList.getPaddingTop() == 0,
                "first Contacts frame zeroes FrostedContentView top padding cleanly");
        contactsList.layout(0, 0, 1200, 2670);
        check(!listener.onPreDraw(), "padding Contacts list cancels unpadded frame before draw");
        int expectedListPad = 278 + Math.round(12f * 3f);
        check(contactsList.getPaddingTop() == expectedListPad,
                "Contacts list receives bar bottom plus 12dp top gap");
        check(listener.onPreDraw(), "settled Contacts frame draws cleanly");

        // Return from a secondary page: host tries to restore frosted top padding, content padding, and overlayMode=false.
        XposedBridge.invoke(activity, "onResume", new Class<?>[0]);
        XposedBridge.invoke(contactsFrosted, "setPadding",
                new Class<?>[]{int.class, int.class, int.class, int.class}, 0, 290, 0, 168);
        XposedBridge.invoke(content, "setPaddingRelative",
                new Class<?>[]{int.class, int.class, int.class, int.class}, 0, 278, 0, 0);
        XposedBridge.invoke(overlay, "setOverlayMode", new Class<?>[]{boolean.class}, false);
        check(contactsFrosted.getPaddingTop() == 0 && contactsFrosted.getPaddingBottom() == 168
                && content.getPaddingTop() == 0 && overlay.isInOverlayMode(),
                "padding and overlay guards block double-whitespace writeback on return");
        check(listener.onPreDraw(), "guarded Contacts return draws immediately with no skipped jump frame");
        Handler.drain();
        check(contactsFrosted.getPaddingTop() == 0 && contactsList.getPaddingTop() == expectedListPad,
                "post-resume sync keeps Contacts padding stable without jump");

        // Switch to Discover (slot 2), where FrostedContentView is a descendant inside page.
        pager.setCurrentItem(2);
        check(!listener.onPreDraw(), "lifting Discover descendant FrostedContentView suppresses stale frame");
        check(discoverFrosted.getPaddingTop() == 0, "Discover FrostedContentView is lifted");
        discoverList.layout(0, 0, 1200, 2670);
        check(!listener.onPreDraw(), "padding Discover list suppresses unpadded frame");
        check(discoverList.getPaddingTop() == expectedListPad, "Discover list receives bar bottom plus 12dp gap");
        check(listener.onPreDraw(), "settled Discover frame draws cleanly");

        // Even a direct padding mutation bypassing hooks within SCAN_MS triggers immediate drift recovery and cancels draw.
        discoverFrosted.setPadding(0, 278, 0, 168);
        check(!listener.onPreDraw(), "direct descendant FrostedContentView drift is caught immediately and frame draw skipped");
        check(discoverFrosted.getPaddingTop() == 0, "direct drift immediately re-zeroes Discover FrostedContentView");
        check(listener.onPreDraw(), "recovered Discover frame draws normally");

        // ConversationListView container stays unguarded.
        XposedBridge.invoke(convFrosted, "setPadding",
                new Class<?>[]{int.class, int.class, int.class, int.class}, 0, 278, 0, 0);
        check(convFrosted.getPaddingTop() == 278, "chats FrostedContentView stays unguarded");

        // Disabling overlay restores latest intercepted padding (290) and overlayMode=false.
        wex.ui.core.WexStore.INSTANCE.flags.put(HomeTopBarCards.KEY_OVERLAY, false);
        HomeTopBarCards.refresh();
        Handler.drain();
        check(contactsFrosted.getPaddingTop() == 290 && discoverFrosted.getPaddingTop() == 278
                && !overlay.isInOverlayMode() && content.getPaddingTop() == 278
                && contactsList.getPaddingTop() == 0 && discoverList.getPaddingTop() == 0,
                "disabling overlay restores latest host paddings and non-overlay state");
        XposedBridge.invoke(contactsFrosted, "setPadding",
                new Class<?>[]{int.class, int.class, int.class, int.class}, 0, 300, 0, 168);
        check(contactsFrosted.getPaddingTop() == 300, "padding guard detaches cleanly after release");

        wex.ui.core.WexStore.INSTANCE.flags.put(HomeTopBarCards.KEY_OVERLAY, true);
        XposedBridge.invoke(activity, "onDestroy", new Class<?>[0]);
    }
}
