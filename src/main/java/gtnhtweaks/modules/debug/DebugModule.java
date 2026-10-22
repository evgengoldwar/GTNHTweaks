package gtnhtweaks.modules.debug;

import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import gtnhtweaks.core.TweakModule;

/** Tools for mod and pack developers. Every tool has its own switch. */
public final class DebugModule extends TweakModule {

    public static final DebugModule INSTANCE = new DebugModule();

    private static final String CATEGORY_GUI_INFO = "guiInfo";
    private static final String CATEGORY_CRASH_TEST = "crashTest";

    public static boolean guiInfo = true;
    public static boolean crashTestMixin = false;

    private DebugModule() {
        super("debug", "Tools for mod and pack developers", true);
    }

    @Override
    protected void loadConfig(Configuration config) {
        config.addCustomCategoryComment(CATEGORY_GUI_INFO, "Information about the open screen");
        guiInfo = config.getBoolean(
            "enabled",
            CATEGORY_GUI_INFO,
            guiInfo,
            "Ctrl + Shift + F12 in any open screen writes to the chat which mod the screen comes from, its name, "
                + "class and FPS, and copies the same text to the clipboard");

        config.addCustomCategoryComment(CATEGORY_CRASH_TEST, "Crashes on purpose, to test the crash screen");
        crashTestMixin = config.getBoolean(
            "mixin",
            CATEGORY_CRASH_TEST,
            crashTestMixin,
            "Apply a mixin that cannot be applied: the game crashes with a mixin error when the main menu class is "
                + "loaded, at the end of the start. Turn it off again after the test. Changes require a game restart");
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        GuiInfo.register();
    }
}
