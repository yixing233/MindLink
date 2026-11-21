package wex.ui.core.feature;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class HomeSidebarChecks {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        HomeSidebarState state = new HomeSidebarState();
        check(!state.isOpen() && state.open() < 0, "no enabled entry starts closed");
        state.available(true);
        check(!state.isOpen(), "entry readiness does not auto-open");
        long first = state.open();
        check(first >= 0 && state.accepts(first), "user click opens once");
        check(state.open() < 0, "repeated click cannot create second dialog");
        state.close();
        check(!state.accepts(first) && !state.isOpen(), "close invalidates queued card loading");
        long second = state.open();
        check(second != first && state.accepts(second), "explicit reopen has fresh generation");
        state.available(false);
        check(!state.accepts(second) && !state.isOpen(), "tab switch or entry loss closes sidebar");
        state.available(true);
        check(!state.isOpen() && !state.accepts(second), "return home does not reopen stale dialog");
        long third = state.open();
        state.close();
        state.close();
        check(!state.accepts(third), "repeat close remains safe");

        check(HomeSidebarState.order("calendar,image,music", true, false, true)
                .equals(Arrays.asList("calendar", "music")), "sidebar excludes image and keeps relative order");
        check(HomeSidebarState.order("music,image,calendar", true, false, true)
                .equals(Arrays.asList("music", "calendar")), "configured relative order retained");
        check(HomeSidebarState.order("music,music,unknown,calendar", false, true, false)
                .equals(Arrays.asList("image")), "disabled cards, duplicate and unknown entries ignored");
        check(HomeSidebarState.order(null, false, false, false).isEmpty(), "all disabled needs no entry or empty header");
        check(HomeSidebarState.order("image", true, false, false).equals(Arrays.asList("calendar")),
                "omitted enabled sidebar card is still accessible");

        List<int[]> occupied = new ArrayList<int[]>();
        occupied.add(new int[]{0, 0, 150, 56});
        occupied.add(new int[]{272, 0, 328, 56});
        occupied.add(new int[]{328, 0, 384, 56});
        int left = HomeSidebarSlot.find(384, 56, 48, 4, occupied);
        check(left >= 192 && left + 48 <= 268, "entry fits before existing action area");
        check(HomeSidebarSlot.fits(384, 56, 48, left, 4, 4, occupied), "measured entry matches the free slot");
        check(!HomeSidebarSlot.fits(384, 56, 48, 268, 4, 4, occupied), "host-shifted entry cannot cover search");
        check(!HomeSidebarSlot.fits(384, 56, 48, left, 12, 4, occupied), "vertical clipping makes entry unavailable");
        occupied.add(new int[]{140, 0, 268, 56});
        check(HomeSidebarSlot.find(384, 56, 48, 4, occupied) == -1, "long profile refuses overlapping entry");
        check(HomeSidebarSlot.find(384, 40, 48, 4, occupied) == -1, "toolbar too short keeps fallback");
        check(HomeSidebarSlot.find(0, 56, 48, 4, occupied) == -1, "unmeasured toolbar is not an available entry");
        occupied.clear();
        occupied.add(new int[]{250, 80, 320, 130});
        check(HomeSidebarSlot.find(384, 56, 48, 4, occupied) == 332, "content below toolbar does not claim its space");
        occupied.clear();
        occupied.add(new int[]{0, 0, 999, 56});
        check(HomeSidebarSlot.find(384, 56, 48, 4, occupied) == -1, "full-width clickable region is never overlaid");
        System.out.println("PASS: sidebar default closed, repeat open, stale callbacks, page leave, enabled order and collision-free entry slot");
    }
}
