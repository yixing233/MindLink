package dev.mindlink.ui;

/** Platform-independent Material3 tokens compiled into both isolated runtimes. */
public final class MindLinkColors {
    private MindLinkColors() { }

    public static final int CARD_RADIUS_DP = 24;
    public static final int CONTENT_PADDING_DP = 16;
    public static final int SECTION_GAP_DP = 12;

    public static final Palette LIGHT = new Palette(
            0xFF336940, 0xFFFFFFFF, 0xFFB5F1BD, 0xFF19512A,
            0xFF506352, 0xFFFFFFFF, 0xFFD3E8D2, 0xFF394B3B,
            0xFF39656E, 0xFFFFFFFF, 0xFFBDEAF4, 0xFF204D55,
            0xFFBA1A1A, 0xFFFFFFFF, 0xFFFFDAD6, 0xFF93000A,
            0xFFF6FBF2, 0xFF181D18, 0xFFDDE5DA, 0xFF414941,
            0xFF717970, 0xFFC1C9BE, 0xFF2D322C, 0xFFEEF2EA, 0xFF99D4A2,
            0xFFD7DBD3, 0xFFF6FBF2, 0xFFFFFFFF, 0xFFF1F5ED,
            0xFFEBEFE7, 0xFFE5EAE1, 0xFFDFE4DC);

    public static final Palette DARK = new Palette(
            0xFF99D4A2, 0xFF003917, 0xFF19512A, 0xFFB5F1BD,
            0xFFB7CCB7, 0xFF233426, 0xFF394B3B, 0xFFD3E8D2,
            0xFFA2CED8, 0xFF00363E, 0xFF204D55, 0xFFBDEAF4,
            0xFFFFB4AB, 0xFF690005, 0xFF93000A, 0xFFFFDAD6,
            0xFF101510, 0xFFDFE4DC, 0xFF414941, 0xFFC1C9BE,
            0xFF8B9389, 0xFF414941, 0xFFDFE4DC, 0xFF2D322C, 0xFF336940,
            0xFF101510, 0xFF353A35, 0xFF0B0F0B, 0xFF181D18,
            0xFF1C211C, 0xFF262B26, 0xFF313631);

    public static final class Palette {
        public final int primary, onPrimary, primaryContainer, onPrimaryContainer;
        public final int secondary, onSecondary, secondaryContainer, onSecondaryContainer;
        public final int tertiary, onTertiary, tertiaryContainer, onTertiaryContainer;
        public final int error, onError, errorContainer, onErrorContainer;
        public final int surface, onSurface, surfaceVariant, onSurfaceVariant;
        public final int outline, outlineVariant, inverseSurface, inverseOnSurface, inversePrimary;
        public final int surfaceDim, surfaceBright, surfaceContainerLowest, surfaceContainerLow;
        public final int surfaceContainer, surfaceContainerHigh, surfaceContainerHighest;

        private Palette(int primary, int onPrimary, int primaryContainer, int onPrimaryContainer,
                int secondary, int onSecondary, int secondaryContainer, int onSecondaryContainer,
                int tertiary, int onTertiary, int tertiaryContainer, int onTertiaryContainer,
                int error, int onError, int errorContainer, int onErrorContainer,
                int surface, int onSurface, int surfaceVariant, int onSurfaceVariant,
                int outline, int outlineVariant, int inverseSurface, int inverseOnSurface, int inversePrimary,
                int surfaceDim, int surfaceBright, int surfaceContainerLowest, int surfaceContainerLow,
                int surfaceContainer, int surfaceContainerHigh, int surfaceContainerHighest) {
            this.primary = primary; this.onPrimary = onPrimary;
            this.primaryContainer = primaryContainer; this.onPrimaryContainer = onPrimaryContainer;
            this.secondary = secondary; this.onSecondary = onSecondary;
            this.secondaryContainer = secondaryContainer; this.onSecondaryContainer = onSecondaryContainer;
            this.tertiary = tertiary; this.onTertiary = onTertiary;
            this.tertiaryContainer = tertiaryContainer; this.onTertiaryContainer = onTertiaryContainer;
            this.error = error; this.onError = onError;
            this.errorContainer = errorContainer; this.onErrorContainer = onErrorContainer;
            this.surface = surface; this.onSurface = onSurface;
            this.surfaceVariant = surfaceVariant; this.onSurfaceVariant = onSurfaceVariant;
            this.outline = outline; this.outlineVariant = outlineVariant;
            this.inverseSurface = inverseSurface; this.inverseOnSurface = inverseOnSurface;
            this.inversePrimary = inversePrimary; this.surfaceDim = surfaceDim;
            this.surfaceBright = surfaceBright; this.surfaceContainerLowest = surfaceContainerLowest;
            this.surfaceContainerLow = surfaceContainerLow; this.surfaceContainer = surfaceContainer;
            this.surfaceContainerHigh = surfaceContainerHigh; this.surfaceContainerHighest = surfaceContainerHighest;
        }
    }
}
