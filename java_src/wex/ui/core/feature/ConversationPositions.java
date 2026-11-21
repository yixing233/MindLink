package wex.ui.core.feature;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/** One immutable mapping is shared by count, item, bind and click paths. */
final class ConversationPositions {
    private final int[] visibleToRaw;
    private final int[] rawToVisible;

    private ConversationPositions(int[] visibleToRaw, int[] rawToVisible) {
        this.visibleToRaw = visibleToRaw; this.rawToVisible = rawToVisible;
    }

    static <T> ConversationPositions build(List<T> rows, Predicate<T> matches) {
        int[] visible = new int[rows.size()], reverse = new int[rows.size()];
        Arrays.fill(reverse, -1);
        int size = 0;
        for (int raw = 0; raw < rows.size(); raw++) {
            if (matches.test(rows.get(raw))) {
                visible[size] = raw; reverse[raw] = size++;
            }
        }
        return new ConversationPositions(Arrays.copyOf(visible, size), reverse);
    }

    int size() { return visibleToRaw.length; }
    int raw(int visible) { return visible >= 0 && visible < size() ? visibleToRaw[visible] : -1; }
    int visible(int raw) { return raw >= 0 && raw < rawToVisible.length ? rawToVisible[raw] : -1; }
}
