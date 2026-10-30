package gtnhtweaks.modules.gregtech;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import gtnhtweaks.core.TweakModule;
import gtnhtweaks.modules.gregtech.client.ToolOverlay;

/**
 * Client side tweaks of GregTech 5 Unofficial. Does nothing when GregTech is not installed.
 */
public final class GregTechModule extends TweakModule {

    public static final GregTechModule INSTANCE = new GregTechModule();

    private static final String CATEGORY_CABLES = "cables";
    private static final String CATEGORY_OVERLAY = "toolOverlay";

    public static boolean hideCableInsulation = true;
    public static boolean modernToolOverlay = true;

    private GregTechModule() {
        super("gregtech", "Client side tweaks of GregTech", ModuleSide.CLIENT);
    }

    @Override
    protected void loadConfig(Configuration config) {
        config.addCustomCategoryComment(CATEGORY_CABLES, "Look of GregTech cables");
        hideCableInsulation = config.getBoolean(
            "hideCableInsulation",
            CATEGORY_CABLES,
            hideCableInsulation,
            "Draw placed insulated cables like bare wires, without the insulation.\n"
                + "Cables in the inventory, in hand and dropped on the ground keep the insulation");

        config.addCustomCategoryComment(CATEGORY_OVERLAY, "The overlay shown on blocks while holding a tool");
        modernToolOverlay = config.getBoolean(
            "modern",
            CATEGORY_OVERLAY,
            modernToolOverlay,
            "Draw the tool overlay of GregTech CEu Modern: a pulsing grid with an icon in every cell for what the "
                + "tool does to that side (connect or block a pipe or a cable, turn the front or the output, attach, "
                + "remove or configure a cover), and the soft mallet and hard hammer state on machines.\n"
                + "false: the grid of GregTech 5. Turning it on after starting with it off needs a restart");
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        if (Loader.isModLoaded("gregtech")) {
            MinecraftForge.EVENT_BUS.register(new ToolOverlay.SingleIcons());
        }
    }
}
