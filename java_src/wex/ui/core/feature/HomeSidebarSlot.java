package wex.ui.core.feature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 在顶栏右半区域寻找不覆盖已有内容的完整点击槽。 */
final class HomeSidebarSlot {
    static boolean fits(int width, int height, int size, int left, int top, int padding, List<int[]> occupied) {
        if (size <= 0 || left < padding || top < 0 || left + size > width - padding || top + size > height) return false;
        for (int[] rect : occupied) {
            if (rect.length < 4) continue;
            if (left < rect[2] + padding && left + size > rect[0] - padding
                    && top < rect[3] && top + size > rect[1]) return false;
        }
        return true;
    }

    static int find(int width, int height, int size, int padding, List<int[]> occupied) {
        if (width <= 0 || height < size || size <= 0) return -1;
        int start = Math.max(padding, width / 2);
        int end = width - padding;
        List<int[]> spans = new ArrayList<int[]>();
        for (int[] rect : occupied) {
            if (rect.length < 4 || rect[2] <= rect[0] || rect[3] <= 0 || rect[1] >= height) continue;
            spans.add(new int[]{Math.max(0, rect[0] - padding), Math.min(width, rect[2] + padding)});
        }
        spans.sort(new Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return Integer.compare(a[0], b[0]);
            }
        });
        int candidate = -1;
        for (int[] span : spans) {
            int right = Math.min(end, span[0]);
            if (right - start >= size) candidate = right - size;
            start = Math.max(start, span[1]);
        }
        if (end - start >= size) candidate = end - size;
        return candidate;
    }
}
