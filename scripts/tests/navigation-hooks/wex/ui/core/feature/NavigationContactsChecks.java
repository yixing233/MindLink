package wex.ui.core.feature;

import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.tencent.mm.ui.LauncherUI;
import com.tencent.mm.ui.LauncherUIBottomTabView;
import com.tencent.mm.ui.MainTabUI;
import com.tencent.mm.ui.contact.address.MvvmAddressUIFragment;
import com.tencent.mm.ui.mogic.WxViewPager;
import com.tencent.mm.view.recyclerview.WxRecyclerView;
import de.robv.android.xposed.XposedBridge;

/** Real nav hooks + Session. All results follow LP/padding driven host layout, not hand-set repaired bounds. */
final class NavigationContactsChecks {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static final class Home {
        final LauncherUI activity = new LauncherUI();
        final FrameLayout branch = new FrameLayout(activity);
        final WxViewPager pager = new WxViewPager(activity);
        final LauncherUIBottomTabView nativeBar = new LauncherUIBottomTabView(activity);
        final MainTabUI.TabsAdapter adapter = new MainTabUI.TabsAdapter();
        final LinearLayout page = new LinearLayout(activity);
        final FrameLayout wrapper = new FrameLayout(activity);
        final WxRecyclerView list = new WxRecyclerView(activity);
        final com.tencent.mm.ui.conversation.ConversationListView conversation = new com.tencent.mm.ui.conversation.ConversationListView(activity);
        final FrameLayout.LayoutParams reserved = new FrameLayout.LayoutParams(-1, 2000);
        int innerBottom;
        final int reservedHeight;
        Home(boolean consumed, boolean lazy, int rows, int gap) {
            activity.getResources().getDisplayMetrics().density = 1f;
            innerBottom = consumed ? 2320 : 2400;
            branch.layout(0, 0, 1080, 2400); branch.setPadding(0, 0, 0, consumed ? 80 : 0);
            ((ViewGroup) activity.getWindow().getDecorView()).addView(branch);
            branch.addView(pager); pager.adapter = adapter; adapter.mViewPager = pager;
            branch.addView(nativeBar); nativeBar.setId(0x7f093c64); nativeBar.layout(0, innerBottom - 240, 1080, innerBottom);
            page.setOrientation(LinearLayout.VERTICAL); page.setPadding(7, 30, 9, 120);
            pager.addView(page); page.addView(wrapper, new LinearLayout.LayoutParams(-1, -1)); wrapper.addView(list);
            reservedHeight = reserved.height = innerBottom - 30 - gap;
            reserved.topMargin = 30;
            page.setLayoutParams(reserved); list.setPadding(2, 3, 4, 12); list.rowsForTest(rows);
            if (!lazy) adapter.this$0.currentFragment = new MvvmAddressUIFragment(page, list);
            pager.current = 1;
            pager.addView(conversation); conversation.setVisibility(View.INVISIBLE); conversation.setPadding(0, 0, 0, 8);
            hostLayout();
        }
        NavigationBarViews.NavigationBarView bar() { return (NavigationBarViews.NavigationBarView) branch.findViewWithTag("mdl_nav_bar_v2"); }
        void open() { activity.onResume(); Handler.drainForTest(); Handler.nextFrameForTest(); }
        void select(int current) throws Throwable {
            pager.current = current;
            XposedBridge.invoke(adapter, "onPageSelected", new Class<?>[]{int.class}, current);
        }
        void hostLayout() {
            pager.layout(0, 0, 1080, innerBottom);
            int height = page.getLayoutParams().height;
            page.layout(0, 30, 1080, 30 + height);
            wrapper.layout(page.getPaddingLeft(), page.getPaddingTop(), 1080 - page.getPaddingRight(), height - page.getPaddingBottom());
            list.layout(0, 0, wrapper.getWidth(), wrapper.getHeight());
            conversation.layout(0, 0, 1080, innerBottom);
            if (bar() != null) { bar().measure(1080, 2400); bar().layout(0, innerBottom - bar().measuredHeight, 1080, innerBottom); }
        }
        void frame() { hostLayout(); branch.getViewTreeObserver().drawForTest(); bar().getViewTreeObserver().drawForTest(); }
        void settle() { for (int i = 0; i < 6; i++) frame(); }
        int listBottom() { int[] xy = new int[2]; list.getLocationInWindow(xy); return xy[1] + list.getHeight(); }
        int surfaceTop() { return bar().getTop() + Math.round(bar().surface.top); }
        void close() { activity.onDestroy(); Handler.drainForTest(); }
    }
    /** Captured real path: recycler/frame/linear/frame/bounce/Frosted/current pager. */
    private static final class FrostedHome {
        final LauncherUI activity = new LauncherUI();
        final FrameLayout branch = new FrameLayout(activity);
        final com.tencent.mm.ui.base.CustomViewPager pager = new com.tencent.mm.ui.base.CustomViewPager(activity);
        final LauncherUIBottomTabView nativeBar = new LauncherUIBottomTabView(activity);
        final MainTabUI.TabsAdapter adapter = new MainTabUI.TabsAdapter();
        final com.tencent.mm.ui.FrostedContentView root = new com.tencent.mm.ui.FrostedContentView(activity);
        final View title = new View(activity);
        final com.tencent.mm.ui.widget.pulldown.MMWeUIBounceView bounce = new com.tencent.mm.ui.widget.pulldown.MMWeUIBounceView(activity);
        final FrameLayout inner = new FrameLayout(activity), wrapper = new FrameLayout(activity);
        final LinearLayout linear = new LinearLayout(activity);
        final WxRecyclerView list = new WxRecyclerView(activity);
        final FrameLayout.LayoutParams bounceParams = new FrameLayout.LayoutParams(-1, -1);
        final NavigationContactViewport probe = new NavigationContactViewport();
        int bottomGap;
        FrostedHome() {
            activity.getResources().getDisplayMetrics().density = 1f;
            ((ViewGroup) activity.getWindow().getDecorView()).addView(branch);
            branch.layout(0, 0, 1200, 2610);
            branch.addView(pager); pager.adapter = adapter; adapter.mViewPager = pager; pager.current = 1;
            branch.addView(nativeBar); nativeBar.setId(NavigationContactViewport.NATIVE_ID); nativeBar.layout(0, 2442, 1200, 2610);
            pager.addView(root, new FrameLayout.LayoutParams(-1, -1)); root.setId(2131314652); root.setPadding(0, 0, 0, 168);
            root.addView(title, new FrameLayout.LayoutParams(-1, 278));
            bounceParams.topMargin = title.getLayoutParams().height;
            root.addView(bounce, bounceParams); bounce.setId(2131314675);
            bounce.addView(inner, new FrameLayout.LayoutParams(-1, -1));
            inner.addView(linear, new FrameLayout.LayoutParams(-1, -1)); linear.setOrientation(LinearLayout.VERTICAL);
            linear.addView(wrapper, new LinearLayout.LayoutParams(-1, -1));
            wrapper.addView(list, new FrameLayout.LayoutParams(-1, -1)); list.setPadding(2, 3, 4, 12); list.rowsForTest(100);
            adapter.this$0.currentFragment = new MvvmAddressUIFragment(root, list);
            hostLayout();
        }
        NavigationBarViews.NavigationBarView bar() { return (NavigationBarViews.NavigationBarView) branch.findViewWithTag("mdl_nav_bar_v2"); }
        void open() { activity.onResume(); Handler.drainForTest(); Handler.nextFrameForTest(); }
        void hostLayout() {
            pager.layout(0, 0, branch.getWidth(), branch.getHeight());
            // All repaired bounds derive from the same unchanged LP and parent padding after layout.
            root.layout(0, 0, pager.getWidth(), pager.getHeight());
            title.layout(0, root.getPaddingTop(), root.getWidth(), root.getPaddingTop() + title.getLayoutParams().height);
            int top = root.getPaddingTop() + bounceParams.topMargin;
            int height = bounceParams.height == ViewGroup.LayoutParams.MATCH_PARENT
                    ? root.getHeight() - root.getPaddingBottom() - top - bottomGap : bounceParams.height;
            bounce.layout(0, top, root.getWidth(), top + height);
            fill(inner, bounce); fill(linear, inner); fill(wrapper, linear); fill(list, wrapper);
            if (bar() != null) { bar().measure(1200, 2610); bar().layout(0, 2610 - bar().measuredHeight, 1200, 2610); }
        }
        private void fill(View child, ViewGroup parent) {
            int height = child.getLayoutParams().height;
            child.layout(parent.getPaddingLeft(), parent.getPaddingTop(), parent.getWidth() - parent.getPaddingRight(),
                    height == ViewGroup.LayoutParams.MATCH_PARENT ? parent.getHeight() - parent.getPaddingBottom() : parent.getPaddingTop() + height);
        }
        void frame() { hostLayout(); branch.getViewTreeObserver().drawForTest(); bar().getViewTreeObserver().drawForTest(); }
        void settle() { for (int i = 0; i < 6; i++) frame(); }
        int listBottom() { int[] xy = new int[2]; list.getLocationInWindow(xy); return xy[1] + list.getHeight(); }
        int surfaceTop() { return bar().getTop() + Math.round(bar().surface.top); }
        void close() { activity.onDestroy(); Handler.drainForTest(); }
    }
    private static void frostedChecks() throws Throwable {
        FrostedHome h = new FrostedHome();
        int[] xy = new int[2]; h.list.getLocationInWindow(xy);
        check(xy[1] == 278 && h.list.getHeight() == 2164 && h.bounce.getBottom() == 2442 && h.root.getHeight() == 2610,
                "real captured layout starts at title278 and consumes exact native168 root reserve");
        check(NavigationContactViewport.currentList(h.pager, h.adapter, h.probe, 168) == h.list,
                "RED captured Frosted getView/current p accepts exact bounded single-bounce path");
        check(NavigationContactViewport.currentList(h.pager, h.adapter) == h.list, "diagnostic-free overload retains same exact probe");
        View[] path = {h.list, h.wrapper, h.linear, h.inner, h.bounce, h.root};
        ViewGroup.LayoutParams[] params = new ViewGroup.LayoutParams[path.length];
        for (int i = 0; i < path.length; i++) params[i] = path[i].getLayoutParams();
        h.open(); h.settle();
        check(h.root.getPaddingBottom() == 0 && h.root.getPaddingTop() == 0 && h.title.getTop() == 0 && h.title.getHeight() == 278 && h.bounce.getTop() == 278,
                "GREEN only real root bottom168 clears; title and original top stay fixed");
        check(h.bounce.getBottom() == 2610 && h.list.getHeight() == 2332 && h.listBottom() == 2610,
                "LP-driven viewport reaches the pager bottom");
        for (int i = 0; i < path.length; i++) check(path[i].getLayoutParams() == params[i] && params[i].height == -1,
                "captured MATCH_PARENT path never receives fixed-height LP rewrite " + i);
        check(h.list.getPaddingBottom() == h.listBottom() - h.surfaceTop() + 6, "expanded real-path list end space is recomputed by Session");
        h.list.bottomForTest(); h.list.getLocationInWindow(xy);
        check(xy[1] + h.list.lastBottomForTest() <= h.surfaceTop() - 6, "real-path final contact row remains reachable above capsule");
        int requests = h.root.layoutRequests + h.list.layoutRequests;
        for (int i = 0; i < 20; i++) h.frame();
        check(requests == h.root.layoutRequests + h.list.layoutRequests, "real-path settled padding does not loop layout requests");
        h.inner.setTranslationY(7f);
        wex.ui.core.WexStore.INSTANCE.floating = false; NavigationBarFeature.refresh(); h.hostLayout();
        check(h.root.getPaddingBottom() == 168 && h.list.getHeight() == 2164 && h.inner.getTranslationY() == 7f && h.list.getPaddingBottom() == 12,
                "cancel restores exact root/end padding without changing bounce motion");
        h.inner.setTranslationY(0f);
        wex.ui.core.WexStore.INSTANCE.floating = true; NavigationBarFeature.refresh(); h.settle();
        check(h.root.getPaddingBottom() == 0 && h.bounce.getBottom() == 2610, "real root reserve reacquires after cancel");
        h.root.setPadding(0, 19, 0, 168); h.settle();
        check(h.root.getPaddingBottom() == 168 && h.root.getPaddingTop() == 19 && h.bounce.getTop() == 297,
                "host padding overwrite pauses patch writes and preserves the new title top");
        h.close();
        check(h.root.getPaddingBottom() == 168 && h.root.getPaddingTop() == 19,
                "dispose preserves the host's newer padding");
        FrostedHome unknown = new FrostedHome(); unknown.bottomGap = 2; unknown.hostLayout();
        check(NavigationContactViewport.currentList(unknown.pager, unknown.adapter, unknown.probe, 168) == unknown.list
                && !unknown.probe.update(unknown.list, unknown.pager, 168) && unknown.root.getPaddingBottom() == 168,
                "known bounce without exact consumed native gap cannot acquire root padding");
        com.tencent.mm.ui.widget.pulldown.MMWeUIBounceView extra = new com.tencent.mm.ui.widget.pulldown.MMWeUIBounceView(unknown.activity);
        unknown.inner.removeView(unknown.linear); unknown.inner.addView(extra); extra.addView(unknown.linear);
        check(NavigationContactViewport.currentList(unknown.pager, unknown.adapter, unknown.probe, 168) == unknown.list,
                "extra wrapper is accepted when list, fragment and pager share the proven parent path");
    }
    public static void main(String[] args) throws Throwable {
        de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam load = new de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam();
        load.classLoader = NavigationContactsChecks.class.getClassLoader(); NavigationBarFeature.init(load); run();
    }
    static void run() throws Throwable {
        frostedChecks();
        for (boolean consumed : new boolean[]{false, true}) {
            Home h = new Home(consumed, false, 100, 120); h.open(); h.settle();
            check(h.page.getLayoutParams().height == h.innerBottom - 30, "RED contacts: exact native reserved fixed parent height expands through production hooks");
            check(h.page.getPaddingBottom() == 0 && h.page.getPaddingTop() == 30, "only native-height root reserve removed; top/side padding retained");
            check(h.listBottom() == h.innerBottom, "contact viewport reaches actual parent inner bottom, including consumed inset");
            check(h.listBottom() > h.surfaceTop(), "content viewport extends beneath real 64dp floating surface, not transparent/zero strip");
            check(h.list.getPaddingBottom() == h.listBottom() - h.surfaceTop() + 6, "end padding recomputed from expanded viewport");
            h.list.bottomForTest();
            int[] xy = new int[2]; h.list.getLocationInWindow(xy);
            check(xy[1] + h.list.lastBottomForTest() <= h.surfaceTop() - 6, "long-list last visible row reachable above capsule");
            int requests = h.page.layoutRequests + h.list.layoutRequests;
            for (int i = 0; i < 20; i++) h.frame();
            check(requests == h.page.layoutRequests + h.list.layoutRequests, "settled geometry causes no repeating layout writes");
            h.select(0); h.page.setVisibility(View.INVISIBLE); h.conversation.setVisibility(View.VISIBLE); h.settle();
            // WeKit semantics: the floating bar overlays the conversation list without
            // writing padding into it; the host list geometry stays exactly native (8 = its
            // constructor padding). No per-frame end-space writes, no jumping.
            check(h.conversation.getPaddingBottom() == 8, "conversation switch leaves the host list padding untouched");
            check(h.page.getLayoutParams().height == h.reservedHeight && h.page.getPaddingBottom() == 120
                    && h.list.getPaddingBottom() == 12, "leaving contacts restores both viewport and end padding");
            h.select(1); h.page.setVisibility(View.VISIBLE); h.conversation.setVisibility(View.INVISIBLE); h.settle();
            check(h.listBottom() == h.innerBottom, "return from conversation recovers contacts without accumulating reserve");
            wex.ui.core.WexStore.INSTANCE.floating = false; NavigationBarFeature.refresh();
            check(h.page.getLayoutParams().height == h.reservedHeight && h.page.getPaddingBottom() == 120, "floating off restores contact LP and padding snapshot");
            check(h.list.getPaddingBottom() == 12 && h.list.getClipToPadding(), "floating off restores contact end padding/clip");
            wex.ui.core.WexStore.INSTANCE.floating = true; NavigationBarFeature.refresh(); h.settle();
            check(h.listBottom() == h.innerBottom, "floating on reacquires proven reserve");
            h.close();
            check(h.page.getLayoutParams().height == h.reservedHeight && h.page.getPaddingBottom() == 120, "dispose restores owned native reserve");
            check(h.wrapper.getClipChildren() && h.wrapper.getClipToPadding() && h.list.getClipToPadding(), "dispose restores inner clip snapshot");
        }
        for (int rows : new int[]{0, 2}) {
            Home h = new Home(true, false, rows, 120); h.open(); h.settle();
            check(h.listBottom() == h.innerBottom && h.list.maxScrollForTest() == 0, "empty/short contacts retain full viewport without artificial scroll"); h.close();
        }
        Home lazy = new Home(true, true, 100, 120); lazy.open(); lazy.settle();
        check(lazy.page.getPaddingBottom() == 120, "lazy unconfirmed fragment fails closed after dirty cleared");
        lazy.adapter.this$0.currentFragment = new MvvmAddressUIFragment(lazy.page, lazy.list); lazy.settle();
        check(lazy.listBottom() == lazy.innerBottom, "late real page attaches after dirty cleared and repairs on bounded probe");
        lazy.close();
        Home changed = new Home(true, false, 100, 120); changed.open(); changed.settle();
        FrameLayout.LayoutParams host = new FrameLayout.LayoutParams(-1, 1800); changed.page.setLayoutParams(host);
        changed.page.setPadding(7, 30, 9, 23); changed.list.setPadding(2, 3, 4, 31); changed.close();
        check(changed.page.getLayoutParams() == host && host.height == 1800 && changed.page.getPaddingBottom() == 23 && changed.list.getPaddingBottom() == 31,
                "dispose preserves newer host LP/padding instead of restoring stale snapshot");
        for (int gap : new int[]{0, 119, 121}) {
            Home rejected = new Home(true, false, 100, gap); rejected.open(); rejected.settle();
            check(rejected.listBottom() == rejected.innerBottom, "bounded measured reserve is reclaimed: " + gap); rejected.close();
        }
        Home oversized = new Home(true, false, 100, 260); oversized.open(); oversized.settle();
        check(oversized.page.getLayoutParams().height == oversized.reservedHeight, "oversized gap is not a native reserve"); oversized.close();
        Home wrong = new Home(true, false, 100, 120); wrong.list.setId(0x7f090268); wrong.open(); wrong.settle();
        check(wrong.page.getLayoutParams().height == wrong.reserved.height && wrong.page.getPaddingBottom() == 120 && wrong.list.getPaddingBottom() == 12,
                "same recycler class wrong id cannot own viewport or padding"); wrong.close();
        Home swipe = new Home(true, false, 100, 120); swipe.open(); swipe.bar().pageScrollState(1); swipe.bar().pageScrolled(0, .5f); swipe.settle();
        check(swipe.page.getLayoutParams().height == swipe.reserved.height, "fractional page cannot acquire contact dimensions");
        swipe.bar().pageScrollState(0); swipe.settle(); check(swipe.listBottom() == swipe.innerBottom, "idle after swipe recomputes exact contact reserve"); swipe.close();
        Home resized = new Home(true, false, 100, 120); resized.open(); resized.settle();
        FrameLayout.LayoutParams replacement = new FrameLayout.LayoutParams(-1, resized.innerBottom - 150); replacement.topMargin = 30;
        resized.page.setLayoutParams(replacement); resized.settle();
        check(resized.page.getLayoutParams() == replacement && replacement.height == resized.innerBottom - 150,
                "active host LP replacement pauses geometry writes until viewport changes");
        resized.innerBottom = 2020; resized.pager.getLayoutParams().height = 2020;
        replacement.height = 1870; resized.page.setPadding(7, 30, 9, 120); resized.settle();
        check(replacement.height == 1990 && resized.listBottom() == 2020 && resized.list.getPaddingBottom() == resized.listBottom() - resized.surfaceTop() + 6,
                "LP/viewport resize recalculates final-row padding after new layout");
        resized.close(); check(replacement.height == 1870 && resized.page.getPaddingBottom() == 120,
                "dispose restores latest acquired host LP baseline, not pre-replacement snapshot");
        Home stale = new Home(true, false, 100, 120); stale.list.setPadding(2, 3, 4, 320); stale.open(); stale.settle();
        check(stale.list.getPaddingBottom() == stale.listBottom() - stale.surfaceTop() + 6,
                "contacts exact padding shrinks obsolete preexisting end reserve after viewport expansion");
        stale.close(); check(stale.list.getPaddingBottom() == 320, "obsolete host padding still has reversible snapshot");
        Home changedMargin = new Home(true, false, 100, 120); changedMargin.open(); changedMargin.settle();
        changedMargin.reserved.bottomMargin = 17; changedMargin.close();
        check(changedMargin.reserved.height == changedMargin.reservedHeight && changedMargin.reserved.bottomMargin == 17,
                "dispose restores only owned height while preserving the host's newer margin");
        Home preOwned = new Home(true, false, 100, 120); preOwned.open();
        preOwned.bar().setAlpha(0f); preOwned.bar().getViewTreeObserver().drawForTest();
        check(preOwned.page.getLayoutParams().height == preOwned.reservedHeight && preOwned.page.getPaddingBottom() == 120,
                "no contact layout mutation before visible native takeover"); preOwned.close();
        Home nonHome = new Home(true, false, 100, 120); nonHome.open();
        com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout chat = new com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout(nonHome.activity);
        chat.layout(0, 0, 1080, 1000); nonHome.branch.addView(chat); nonHome.settle();
        check(nonHome.page.getPaddingBottom() == 120 && nonHome.page.getLayoutParams().height == nonHome.reservedHeight,
                "chatting/non-home branch never acquires contact constraints"); nonHome.close();
        Home offscreen = new Home(true, false, 100, 120); offscreen.open(); offscreen.page.setTranslationX(1080); offscreen.settle();
        check(offscreen.page.getPaddingBottom() == 120 && offscreen.list.getPaddingBottom() == 12,
                "cached address fragment translated off current page fails closed"); offscreen.close();
        Home duplicate = new Home(true, false, 100, 120);
        WxRecyclerView unrelated = new WxRecyclerView(duplicate.activity); unrelated.setPadding(1, 2, 3, 444);
        duplicate.pager.addView(unrelated); unrelated.layout(0, 0, 1080, 1000); duplicate.open(); duplicate.settle();
        check(unrelated.getPaddingBottom() == 444 && unrelated.getClipToPadding(),
                "another shown WxRecyclerView with same numeric id outside exact fragment p is untouched"); duplicate.close();
        Home classPatch = new Home(true, false, 100, 120); classPatch.adapter.this$0.currentFragment = new Object(); classPatch.open(); classPatch.settle();
        check(classPatch.page.getLayoutParams().height == classPatch.reservedHeight && classPatch.page.getPaddingBottom() == 120 && classPatch.list.getPaddingBottom() == 12,
                "unknown replacement fragment class fails closed without guessing host version"); classPatch.close();
        Home rootOnly = new Home(true, false, 100, 0); rootOnly.open(); rootOnly.settle();
        check(rootOnly.listBottom() == rootOnly.innerBottom && rootOnly.page.getPaddingBottom() == 0 && rootOnly.page.getLayoutParams().height == rootOnly.reservedHeight,
                "static proven root reserve alone expands inner viewport without unnecessary LP rewrite"); rootOnly.close();
        Home gone = new Home(true, false, 100, 120); gone.open(); gone.frame();
        check(gone.nativeBar.getVisibility() == View.GONE && gone.bar().session.nativeReserveHeight == 240,
                "visible native height captured before GONE takeover");
        gone.nativeBar.layout(0, 2200, 1080, 2200); NavigationBarFeature.refresh(); gone.settle();
        check(gone.listBottom() == gone.innerBottom && gone.bar().session.nativeReserveHeight == 240,
                "GONE zero measured host height never discards captured original reserve"); gone.close();
        System.out.println("PASS: " + checks + " contacts production-hook viewport/last-row/lazy/fail-closed/restore assertions");
    }
}
