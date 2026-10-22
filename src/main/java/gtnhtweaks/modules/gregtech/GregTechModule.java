package gtnhtweaks.modules.gregtech;

import net.minecraftforge.common.config.Configuration;

import gtnhtweaks.core.TweakModule;

/**
 * Client side tweaks of GregTech 5 Unofficial. Does nothing when GregTech is not installed.
 */
public final class GregTechModule extends TweakModule {

    public static final GregTechModule INSTANCE = new GregTechModule();

    private static final String CATEGORY_CABLES = "cables";

    public static boolean hideCableInsulation = true;

    private GregTechModule() {
        super("gregtech", "Client side tweaks of GregTech", true);
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
    }
}
