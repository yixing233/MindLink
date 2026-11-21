package wex.ui.core.feature;

/** Home-list direction tracking adapted from WeKit ReplaceNavigationBar (GPL-3.0-only). */
final class NavigationScrollState {
    private int position = -1, top;
    private boolean observed, manual, draggedDown, hidden;

    void touchScroll(boolean value) {
        manual = value;
        if (value) draggedDown = false;
    }

    boolean update(int firstPosition, int firstTop) {
        boolean same = position == firstPosition;
        boolean down = same ? firstTop < top : firstPosition > position;
        boolean changed = !same || Math.abs(top - firstTop) > 1;
        if (manual && down) draggedDown = true;
        boolean bounce = down && !manual && !draggedDown && same;
        if (observed && changed && (down || manual) && !bounce) hidden = down;
        position = firstPosition; top = firstTop; observed = true;
        return hidden;
    }

    void reset() { position = -1; observed = false; manual = false; draggedDown = false; hidden = false; }
}
