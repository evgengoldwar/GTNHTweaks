package gtnhtweaks.modules.minecraft;

import java.util.Locale;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import gtnhtweaks.core.ModuleManager;
import gtnhtweaks.core.TweakModule;
import gtnhtweaks.modules.minecraft.client.OutlineClient;

/** Tweaks of Minecraft itself. The first one: the outline of the looked at block. */
public final class MinecraftModule extends TweakModule {

    public static final MinecraftModule INSTANCE = new MinecraftModule();

    public static final String CATEGORY_OUTLINE = "blockOutline";

    /** NORMAL: hidden by other blocks, like vanilla. THROUGH_BLOCKS: seen through every block. */
    public enum OutlineMode {
        NORMAL,
        THROUGH_BLOCKS
    }

    public static final int VANILLA_COLOR = 0x66000000;
    public static final float VANILLA_WIDTH = 2F;

    public static boolean outlineEnabled = false;
    /** ARGB. */
    public static int outlineColor = VANILLA_COLOR;
    public static float outlineWidth = VANILLA_WIDTH;
    public static OutlineMode outlineMode = OutlineMode.NORMAL;
    /** ARGB color of the filled faces, alpha 0 for no fill. */
    public static int fillColor = 0;
    /** Lines in the colors of the rainbow, flowing along the outline. */
    public static boolean rainbow = false;
    /** Rounds of the rainbow per second. */
    public static float rainbowSpeed = 0.5F;

    private MinecraftModule() {
        super("minecraft", "Tweaks of Minecraft itself, like the outline of the looked at block", true);
    }

    @Override
    protected void loadConfig(Configuration config) {
        config.addCustomCategoryComment(
            CATEGORY_OUTLINE,
            "The outline of the looked at block. The settings can also be changed in game, with a live preview, "
                + "on the screen opened by the key \"Block outline\" (Controls, GTNHTweaks)");
        Property enabled = config.get(
            CATEGORY_OUTLINE,
            "enabled",
            false,
            "Draw the block outline in your own color, width and mode. Its look is set in game, with a live "
                + "preview, on the screen of the key \"Block outline\" (O by default, Controls, GTNHTweaks). "
                + "Turning it on needs a restart for the key");
        outlineEnabled = enabled.getBoolean(false);
        outlineColor = parseColor(
            config.getString(
                "color",
                CATEGORY_OUTLINE,
                formatColor(VANILLA_COLOR),
                "Color of the lines as AARRGGBB in hex: alpha, red, green, blue. 66000000 is the vanilla one"),
            VANILLA_COLOR);
        outlineWidth = config.getFloat(
            "width",
            CATEGORY_OUTLINE,
            VANILLA_WIDTH,
            0.5F,
            10F,
            "Width of the lines in pixels, 2 in vanilla");
        try {
            outlineMode = OutlineMode.valueOf(
                config.getString(
                    "mode",
                    CATEGORY_OUTLINE,
                    OutlineMode.NORMAL.name(),
                    "NORMAL: hidden behind other blocks, like vanilla. THROUGH_BLOCKS: seen through every block, "
                        + "like a wireframe",
                    new String[] { OutlineMode.NORMAL.name(), OutlineMode.THROUGH_BLOCKS.name() })
                    .trim()
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            outlineMode = OutlineMode.NORMAL;
        }
        // the fill had the color of the lines with an opacity of its own before it got its own color
        String fillOpacity = removeOldKey(config, CATEGORY_OUTLINE, "fillOpacity");
        int oldFill = 0;
        try {
            float opacity = fillOpacity == null ? 0 : Float.parseFloat(fillOpacity);
            if (opacity > 0) oldFill = Math.round(opacity * 255) << 24 | outlineColor & 0xFFFFFF;
        } catch (NumberFormatException ignored) {}
        fillColor = parseColor(
            config.getString(
                "fillColor",
                CATEGORY_OUTLINE,
                formatColor(oldFill),
                "Color of the filled faces as AARRGGBB in hex, alpha 00 for no fill"),
            0);
        rainbow = config.getBoolean(
            "rainbow",
            CATEGORY_OUTLINE,
            false,
            "Draw the lines in the colors of the rainbow, flowing along the outline. The alpha of the color is kept");
        rainbowSpeed = config.getFloat(
            "rainbowSpeed",
            CATEGORY_OUTLINE,
            0.5F,
            0.05F,
            5F,
            "Rounds of the rainbow per second");
    }

    /** The look of the outline is set on its screen in game, so the settings screen only shows the switch. */
    @Override
    public boolean showInSettings(String category, String key) {
        return !CATEGORY_OUTLINE.equalsIgnoreCase(category) || "enabled".equals(key);
    }

    /** Writes the current outline settings to the config file, after a change on the outline screen. */
    public static void saveOutline() {
        // through Configuration.get, which finds the category whatever its case: Forge keeps it lower case
        Configuration config = ModuleManager.getConfig(INSTANCE);
        config.get(CATEGORY_OUTLINE, "color", formatColor(VANILLA_COLOR))
            .set(formatColor(outlineColor));
        // getFloat keeps floats as text properties: written the same way
        config.get(CATEGORY_OUTLINE, "width", Float.toString(VANILLA_WIDTH))
            .set(Float.toString(outlineWidth));
        config.get(CATEGORY_OUTLINE, "mode", OutlineMode.NORMAL.name())
            .set(outlineMode.name());
        config.get(CATEGORY_OUTLINE, "fillColor", formatColor(0))
            .set(formatColor(fillColor));
        config.get(CATEGORY_OUTLINE, "rainbow", false)
            .set(rainbow);
        config.get(CATEGORY_OUTLINE, "rainbowSpeed", Float.toString(0.5F))
            .set(Float.toString(rainbowSpeed));
        ModuleManager.saveAndReload();
    }

    public static String formatColor(int argb) {
        return String.format("%08X", argb);
    }

    public static int parseColor(String text, int fallback) {
        try {
            String hex = text.trim();
            if (hex.startsWith("#")) hex = hex.substring(1);
            if (hex.startsWith("0x") || hex.startsWith("0X")) hex = hex.substring(2);
            long value = Long.parseLong(hex, 16);
            if (hex.length() <= 6) value |= 0xFF000000L;
            return (int) value;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** True if the outline is drawn with the settings of this module. */
    public static boolean outlineActive() {
        return INSTANCE.isEnabled() && outlineEnabled;
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        if (outlineEnabled) OutlineClient.register();
    }
}
