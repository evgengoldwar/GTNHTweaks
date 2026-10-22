package gtnhtweaks.modules.debug;

import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import gtnhtweaks.core.TweakModule;

/** Tools for mod and pack developers. Every tool has its own switch. */
public final class DebugModule extends TweakModule {

    public static final DebugModule INSTANCE = new DebugModule();

    private static final String CATEGORY_GUI_INFO = "guiInfo";

    public static boolean guiInfo = true;

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
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        GuiInfo.register();
    }
}
