package gtnhtweaks.common.render;

/** Color sets shared by the GTNH loading screen and main menu. All colors are ARGB. */
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
        0xFFF6F6F6, 0xFFFFFFFF);

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
    }

    public static String[] names() {
        GTNHTheme[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) names[i] = values[i].name();
        return names;
    }

    /** Case-insensitive lookup, DARK if the name is unknown. */
    public static GTNHTheme parse(String name) {
        for (GTNHTheme theme : values()) {
            if (theme.name()
                .equalsIgnoreCase(name.trim())) return theme;
        }
        return DARK;
    }
}
