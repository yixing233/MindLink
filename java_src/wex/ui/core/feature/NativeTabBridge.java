package wex.ui.core.feature;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Reads host-owned tab nodes without removing their listeners or changing the pager page set. */
final class NativeTabBridge {
    static final class Icons {
        final Bitmap outlined, filled;
        final int activeColor;
        Icons(Bitmap outlined, Bitmap filled, int activeColor) {
            this.outlined = outlined; this.filled = filled; this.activeColor = activeColor;
        }
    }

    static final class Badge {
        final int count;
        final boolean dot;
        Badge(int count, boolean dot) { this.count = count; this.dot = dot; }
    }

    private final WeakReference<ViewGroup> hostRef;
    private final List<WeakReference<View>> tabs = new ArrayList<WeakReference<View>>();
    private final List<WeakReference<View>> iconViews = new ArrayList<WeakReference<View>>();
    private final String[] labels = new String[4];
    private final Icons[] icons = new Icons[4];
    private boolean iconFailureLogged;

    NativeTabBridge(ViewGroup host) {
        hostRef = new WeakReference<ViewGroup>(host);
        View[] found = new View[4], foundIcons = new View[4];
        ArrayDeque<View> queue = new ArrayDeque<View>(); queue.add(host);
        for (int visited = 0; !queue.isEmpty() && visited < 192; visited++) {
            View view = queue.removeFirst();
            Object tag = view.getTag();
            if (tag instanceof Integer && view.hasOnClickListeners()) {
                int index = (Integer) tag;
                if (index >= 0 && index < 4) {
                    if (found[index] != null && found[index] != view) throw new IllegalStateException("Ambiguous native tab node");
                    found[index] = view;
                }
            }
            if ("com.tencent.mm.ui.TabIconView".equals(view.getClass().getName())) {
                View parent = view;
                while (parent != host && !(parent.getTag() instanceof Integer)) {
                    parent = parent.getParent() instanceof View ? (View) parent.getParent() : host;
                }
                if (parent.getTag() instanceof Integer) {
                    int index = (Integer) parent.getTag();
                    if (index >= 0 && index < 4) {
                        if (foundIcons[index] != null) throw new IllegalStateException("Ambiguous native tab icon");
                        foundIcons[index] = view;
                    }
                }
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
            }
        }
        int labelId = host.getResources().getIdentifier("icon_tv", "id", "com.tencent.mm");
        for (int i = 0; i < 4; i++) {
            if (found[i] == null || foundIcons[i] == null) throw new IllegalStateException("Native four-tab structure is unavailable");
            tabs.add(new WeakReference<View>(found[i])); iconViews.add(new WeakReference<View>(foundIcons[i]));
            View label = labelId == 0 ? null : found[i].findViewById(labelId);
            labels[i] = label instanceof TextView ? ((TextView) label).getText().toString() : NavigationTabModel.labelFor(i);
        }
    }

    String label(int index) { return labels[index]; }
    Icons icons(int index) { return icons[index]; }

    void reloadIcons() {
        for (int i = 0; i < 4; i++) {
            View view = iconViews.get(i).get();
            try { icons[i] = view == null ? null : readIcons(view); }
            catch (Throwable error) {
                icons[i] = null;
                if (!iconFailureLogged) {
                    iconFailureLogged = true;
                    NavigationBarFeature.log("原生图标读取失败，使用线条图标 " + error.getClass().getSimpleName());
                }
            }
        }
    }

    boolean click(int index) {
        View tab = tabs.get(index).get();
        return tab != null && tab.performClick();
    }

    boolean longClick(int index) {
        View tab = tabs.get(index).get();
        return tab != null && tab.isLongClickable() && tab.performLongClick();
    }

    Badge badge(int index) {
        ViewGroup host = hostRef.get();
        if (host == null) return new Badge(-1, false);
        String getter = new String[]{"getMainTabUnread", "getContactTabUnread", "getFriendTabUnread", "getSettingsTabUnread"}[index];
        int count = -1;
        boolean dot = false;
        try {
            Object value = host.getClass().getMethod(getter).invoke(host);
            if (value instanceof Number) count = Math.max(0, ((Number) value).intValue());
        } catch (ReflectiveOperationException ignored) { }
        if (index == 2 || index == 3) {
            try {
                Object point = host.getClass().getMethod(index == 2 ? "getShowFriendPoint" : "getSettingsPoint").invoke(host);
                dot = Boolean.TRUE.equals(point);
            } catch (ReflectiveOperationException ignored) { }
        }
        return new Badge(count, dot);
    }

    private static Icons readIcons(View view) throws ReflectiveOperationException {
        List<Field> bitmapFields = new ArrayList<Field>(), alphaFields = new ArrayList<Field>();
        List<Paint> paints = new ArrayList<Paint>();
        for (Field field : view.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            if (field.getType() == Bitmap.class) bitmapFields.add(field);
            else if (field.getType() == int.class) alphaFields.add(field);
            else if (field.getType() == Paint.class) {
                Object value = field.get(view); if (value instanceof Paint) paints.add((Paint) value);
            }
        }
        if (bitmapFields.size() != 3 || alphaFields.size() != 1 || paints.size() != 2) {
            throw new IllegalStateException("Unsupported TabIconView fields");
        }
        int width = 0, height = 0;
        for (Field field : bitmapFields) {
            Bitmap bitmap = (Bitmap) field.get(view);
            if (bitmap == null || bitmap.isRecycled()) throw new IllegalStateException("Native tab bitmap is unavailable");
            width = Math.max(width, bitmap.getWidth()); height = Math.max(height, bitmap.getHeight());
        }
        if (width <= 0 || height <= 0 || width > 1024 || height > 1024) throw new IllegalStateException("Invalid native icon dimensions");
        Field alpha = alphaFields.get(0);
        int originalAlpha = alpha.getInt(view);
        int[] paintAlphas = new int[paints.size()];
        for (int i = 0; i < paints.size(); i++) paintAlphas[i] = paints.get(i).getAlpha();
        Method draw = view.getClass().getDeclaredMethod("onDraw", Canvas.class); draw.setAccessible(true);
        try {
            alpha.setInt(view, 0);
            Bitmap outlined = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            draw.invoke(view, new Canvas(outlined));
            Paint active = null;
            for (Paint paint : paints) if (paint.getAlpha() == 0) {
                if (active != null) throw new IllegalStateException("Ambiguous native active paint");
                active = paint;
            }
            if (active == null) throw new IllegalStateException("Native active paint is unavailable");
            Bitmap swatch = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            Paint color = new Paint(); color.setColor(Color.WHITE); color.setColorFilter(active.getColorFilter());
            new Canvas(swatch).drawPaint(color);
            int activeColor = swatch.getPixel(0, 0); swatch.recycle();
            alpha.setInt(view, 255);
            Bitmap filled = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            draw.invoke(view, new Canvas(filled));
            return new Icons(outlined, filled, activeColor);
        } finally {
            alpha.setInt(view, originalAlpha);
            for (int i = 0; i < paints.size(); i++) paints.get(i).setAlpha(paintAlphas[i]);
        }
    }
}
