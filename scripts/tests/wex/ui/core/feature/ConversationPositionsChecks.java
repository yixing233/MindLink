package wex.ui.core.feature;

import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

public final class ConversationPositionsChecks {
    public static void main(String[] args) {
        ConversationPositions mapping = ConversationPositions.build(Arrays.asList("alice", "group", "bob", "official"),
                name -> name.equals("alice") || name.equals("bob"));
        check(mapping.size() == 2, "filtered count");
        check(mapping.raw(0) == 0 && mapping.raw(1) == 2, "click resolves to exact source row");
        check(mapping.visible(2) == 1 && mapping.visible(1) == -1, "reverse map excludes hidden rows");
        check(mapping.raw(-1) == -1 && mapping.raw(2) == -1, "headers/footer never map to real rows");
        check(ConversationPositions.build(Collections.emptyList(), value -> true).size() == 0, "empty snapshot");
        Random random = new Random(42);
        for (int round = 0; round < 300; round++) {
            Integer[] rows = new Integer[random.nextInt(150)];
            for (int i = 0; i < rows.length; i++) rows[i] = random.nextInt(4);
            ConversationPositions value = ConversationPositions.build(Arrays.asList(rows), row -> row % 2 == 0);
            int visible = 0;
            for (int raw = 0; raw < rows.length; raw++) {
                if (rows[raw] % 2 == 0) {
                    check(value.raw(visible) == raw, "stable order");
                    check(value.visible(raw) == visible, "inverse mapping");
                    visible++;
                } else check(value.visible(raw) == -1, "excluded source row");
            }
            check(value.size() == visible, "count and bind use the same snapshot");
        }
        System.out.println("PASS: conversation count/bind/click mapping, headers and 300 randomized snapshots");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
