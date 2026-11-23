package wex.ui.core.feature;

import android.view.View;
import android.view.ViewGroup;
import com.tencent.mm.dynamicbackground.view.DynamicBackgroundGLSurfaceView;
import com.tencent.mm.plugin.multitask.ui.bg.DynamicBgContainer;
import com.tencent.mm.ui.conversation.MainUIView;

public final class ConversationPullWallpaperGuardChecks {
    private static int assertions;

    public static void main(String[] args) {
        MainUIView page = new MainUIView();
        DynamicBgContainer container = new DynamicBgContainer();
        DynamicBackgroundGLSurfaceView surface = new DynamicBackgroundGLSurfaceView();
        container.add(surface);
        page.add(container);
        View list = new View();
        page.add(list);

        ConversationPullWallpaperGuard guard = new ConversationPullWallpaperGuard();
        check(guard.hideForConversationList(list), "exact conversation pull surface is recognized");
        check(surface.getVisibility() == View.INVISIBLE, "exact conversation GL surface is suppressed before the list can expose it");
        check(!guard.hideForConversationList(list), "steady hidden frames do not retrigger a backdrop refresh");
        check(surface.getVisibility() == View.INVISIBLE, "repeated frames keep the surface suppressed");
        check(guard.restore(), "releasing the guard reports its visibility transition");
        check(surface.getVisibility() == View.VISIBLE, "the original surface visibility is restored on release");

        ViewGroup unrelatedPage = new ViewGroup();
        DynamicBgContainer unrelatedContainer = new DynamicBgContainer();
        DynamicBackgroundGLSurfaceView unrelatedSurface = new DynamicBackgroundGLSurfaceView();
        unrelatedContainer.add(unrelatedSurface);
        unrelatedPage.add(unrelatedContainer);
        View unrelatedList = new View();
        unrelatedPage.add(unrelatedList);
        check(!guard.hideForConversationList(unrelatedList), "other pages do not activate the pull guard");
        check(unrelatedSurface.getVisibility() == View.VISIBLE, "same GL class on another page is untouched");

        MainUIView nestedPage = new MainUIView();
        ViewGroup wrapper = new ViewGroup();
        DynamicBgContainer nestedContainer = new DynamicBgContainer();
        DynamicBackgroundGLSurfaceView nestedSurface = new DynamicBackgroundGLSurfaceView();
        nestedContainer.add(nestedSurface);
        wrapper.add(nestedContainer);
        nestedPage.add(wrapper);
        View nestedList = new View();
        nestedPage.add(nestedList);
        check(!guard.hideForConversationList(nestedList), "non-matching nested paths do not activate the pull guard");
        check(nestedSurface.getVisibility() == View.VISIBLE, "a non-matching nested container path is fail-closed");

        check(guard.hideForConversationList(list), "a later pull acquires the surface again");
        container.children.remove(surface);
        surface.parent = null;
        check(guard.hideForConversationList(list) && surface.getVisibility() == View.VISIBLE,
                "leaving the pull page restores the wall and releases guard state");
        check(!guard.hideForConversationList(list), "later frames do not repeat the removal refresh");

        System.out.println("PASS: conversation pull wallpaper guard (" + assertions + " assertions)");
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
