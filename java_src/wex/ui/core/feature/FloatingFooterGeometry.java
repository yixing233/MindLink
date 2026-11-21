package wex.ui.core.feature;

final class FloatingFooterGeometry {
    private FloatingFooterGeometry() { }

    // -1 means unmeasured: do not replace a native/natural height yet.
    static int panelHeightLimit(int hostHeight, int imeHeight, int inputHeight, int reserve, int minimum) {
        if (hostHeight <= 0 || inputHeight <= 0) return -1;
        // minimum is a usability recommendation, never permission to exceed bounds.
        long available = (long) hostHeight - Math.max(0, imeHeight) - inputHeight - Math.max(0, reserve);
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, available));
    }

    static int targetPanelHeight(int natural, int extension, int limit) {
        if (natural <= 0) return 0;
        long wanted = (long) natural + Math.max(0, extension);
        return (int) Math.min(wanted, Math.max(0, limit));
    }

    static int scrollForVisiblePanel(boolean panelVisible, int requested, int imeHeight, int keyboardScroll) {
        if (!panelVisible) return requested;
        if (imeHeight <= 0) return 0;
        return keyboardScroll > 0 ? keyboardScroll : imeHeight;
    }

    static int yFromBottomWithoutPanel(int original, boolean panelVisible, int panelHeight) {
        return panelVisible ? Math.max(0, original - Math.max(0, panelHeight)) : original;
    }
}
