package gtnhtweaks.modules.debug;

import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import gtnhtweaks.core.TweakModule;

/** Tools for mod and pack developers. Every tool has its own switch. */
public final class DebugModule extends TweakModule {

    public static final DebugModule INSTANCE = new DebugModule();

    private static final String CATEGORY_GUI_INFO = "guiInfo";
    private static final String CATEGORY_CRASH_TEST = "crashTest";

    public static boolean guiInfo = true;
    /** OFF, BEFORE_WINDOW, STARTUP or IN_GAME. */
    public static String mixinCrash = "OFF";

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
        removeKey(config, CATEGORY_CRASH_TEST, "mixin");
        mixinCrash = config.getString(
            "mixinCrash",
            CATEGORY_CRASH_TEST,
            "OFF",
            "Crash with a mixin that cannot be applied:\n"
                + "BEFORE_WINDOW - when the game starts, before its window is made (plain Java crash window);\n"
                + "STARTUP - while the mods are loaded, the game window already open;\n"
                + "IN_GAME - 3 seconds after a world is entered, every time;\n"
                + "OFF - no test crash. Set it back to OFF after the test. Changes require a game restart",
            new String[] { "OFF", "BEFORE_WINDOW", "STARTUP", "IN_GAME" });
    }

    public static boolean crashTestEnabled() {
        return "BEFORE_WINDOW".equals(mixinCrash) || "STARTUP".equals(mixinCrash) || "IN_GAME".equals(mixinCrash);
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        GuiInfo.register();
    }

    @Override
    public void init(FMLInitializationEvent event) {
        if (crashTestEnabled()) CrashTest.start(mixinCrash);
    }
}
