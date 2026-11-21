package android.content.res;

public class ColorStateList {
    private final int[][] states;
    private final int[] colors;

    public ColorStateList(int[][] states, int[] colors) {
        this.states = states;
        this.colors = colors;
    }

    public static ColorStateList valueOf(int color) {
        return new ColorStateList(new int[][]{new int[0]}, new int[]{color});
    }

    public int getDefaultColor() { return colors[colors.length - 1]; }

    public int getColorForState(int[] current, int fallback) {
        for (int i = 0; i < states.length; i++) {
            boolean matches = true;
            for (int required : states[i]) {
                boolean present = false;
                for (int value : current) if (value == Math.abs(required)) present = true;
                if (present != (required > 0)) { matches = false; break; }
            }
            if (matches) return colors[i];
        }
        return fallback;
    }
}
