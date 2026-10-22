package gtnhtweaks.common.render;

/** Color sets shared by the GTNH loading screen, main menu, menus and toasts. All colors are ARGB. */
public enum GTNHTheme {

    DARK(0xFF1C1D24, 0xFF101116, // background top, bottom
        0xFFE8E8EE, 0xFF8C8FA0, 0xFF5A5D6A, 0xFFFFFFFF, // text, dim text, disabled text, timer
        0xFFF2A33A, 0xFFFFCB72, 0xFFA8641C, 0x90000000, // logo, logo light edge, logo dark edge, logo shadow
        0xFF3B3E4C, 0xFF24262F, 0xFFF2A33A, // bar border, bar background, bar fill
        0xFF262833, 0xFF323544), // button background, hovered button background

    LIGHT(0xFFFFFFFF, 0xFFEDEDED, //
        0xFF1E1E1E, 0xFF6E6E6E, 0xFFAAAAAA, 0xFF000000, //
        0xFFCB3D35, 0xFFE86A61, 0xFF8E2620, 0x40000000, //
        0xFFC0C0C0, 0xFFF6F6F6, 0xFFCB3D35, //
        0xFFF6F6F6, 0xFFFFFFFF),

    /**
     * The colors of the GTNH Shadow UI resource pack by Ranzuu (CC BY-NC-SA 4.0, github.com/Ranzuu/Shadow-UI): gray
     * beveled buttons and panels with a dark border, sunken slots and bars, vanilla text colors.
     */
    SHADOW(0xFF2F2E2D, 0xFF1A1A1A, //
        0xFFE0E0E0, 0xFFA09F9E, 0xFF6A6968, 0xFFFFFFFF, //
        0xFFF2A33A, 0xFFFFCB72, 0xFFA8641C, 0x90000000, //
        0xFF1C1C1D, 0xFF1E1E1E, 0xFFF2A33A, //
        0xFF434241, 0xFF555452);

    static {
        SHADOW.bevels(
            new Bevel(0xFF1C1C1D, 0xFF595857, 0xFF434241, 0xFF2E2E2D, 1),
            new Bevel(0xFF1C1C1D, 0xFF666564, 0xFF555452, 0xFF292828, 1),
            new Bevel(0xFF1C1C1D, 0xFF31302F, 0xFF2B2A2A, 0xFF262525, 1),
            new Bevel(0xFF111111, 0xFF595857, 0xFF4A4948, 0xFF242323, 2),
            new Bevel(0, 0xFF3C3B3A, 0xFF1F1F1F, 0xFF595857, 1));
        SHADOW.hoverText = 0xFFFFFFA0;
        SHADOW.selectionBorder = 0xFF8A8988;
        SHADOW.selectionFill = 0xFF2B2A2A;
        LIGHT.textShadow = false;
    }

    public final int backgroundTop;
    public final int backgroundBottom;
    public final int text;
    public final int textDim;
    public final int textDisabled;
    public final int timer;
    public final int logo;
    public final int logoLight;
    public final int logoDark;
    public final int logoShadow;
    public final int barBorder;
    public final int barBackground;
    public final int barFill;
    public final int buttonBackground;
    public final int buttonHover;

    /** Label color of a hovered button. */
    public int hoverText;
    /** Border and fill of the selected entry of a list. */
    public int selectionBorder;
    public int selectionFill;
    public boolean textShadow = true;

    /** Beveled look: null for the flat themes. */
    public Bevel button, buttonHovered, buttonDisabled, panel, inset;

    GTNHTheme(int backgroundTop, int backgroundBottom, int text, int textDim, int textDisabled, int timer, int logo,
        int logoLight, int logoDark, int logoShadow, int barBorder, int barBackground, int barFill,
        int buttonBackground, int buttonHover) {
        this.backgroundTop = backgroundTop;
        this.backgroundBottom = backgroundBottom;
        this.text = text;
        this.textDim = textDim;
        this.textDisabled = textDisabled;
        this.timer = timer;
        this.logo = logo;
        this.logoLight = logoLight;
        this.logoDark = logoDark;
        this.logoShadow = logoShadow;
        this.barBorder = barBorder;
        this.barBackground = barBackground;
        this.barFill = barFill;
        this.buttonBackground = buttonBackground;
        this.buttonHover = buttonHover;
        this.hoverText = logo;
        this.selectionBorder = barFill;
        this.selectionFill = buttonHover;
    }

    private void bevels(Bevel button, Bevel buttonHovered, Bevel buttonDisabled, Bevel panel, Bevel inset) {
        this.button = button;
        this.buttonHovered = buttonHovered;
        this.buttonDisabled = buttonDisabled;
        this.panel = panel;
        this.inset = inset;
    }

    public boolean isBeveled() {
        return panel != null;
    }

    /** Fill color of panels: the beveled panel, or the button background of the flat themes. */
    public int panelFill() {
        return panel != null ? panel.fill : buttonBackground;
    }

    public static String[] names() {
        GTNHTheme[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) names[i] = values[i].name();
        return names;
    }

    /** Case-insensitive lookup, SHADOW if the name is unknown. */
    public static GTNHTheme parse(String name) {
        for (GTNHTheme theme : values()) {
            if (theme.name()
                .equalsIgnoreCase(name.trim())) return theme;
        }
        return SHADOW;
    }

    /**
     * A box with an optional 1 px border, a light top left edge, a fill and a dark bottom right edge, like the buttons
     * and panels of Shadow UI. Sunken boxes swap the edge colors.
     */
    public static final class Bevel {

        /** 0 for no border. */
        public final int border;
        public final int topLeft;
        public final int fill;
        public final int bottomRight;
        public final int edge;

        public Bevel(int border, int topLeft, int fill, int bottomRight, int edge) {
            this.border = border;
            this.topLeft = topLeft;
            this.fill = fill;
            this.bottomRight = bottomRight;
            this.edge = edge;
        }
    }
}
