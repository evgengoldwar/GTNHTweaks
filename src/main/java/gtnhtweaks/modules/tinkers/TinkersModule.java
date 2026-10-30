package gtnhtweaks.modules.tinkers;

import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import gtnhtweaks.core.TweakModule;
import gtnhtweaks.modules.tinkers.client.CraftingStationClear;

/** Client side tweaks of Tinkers' Construct. Does nothing when it is not installed. */
public final class TinkersModule extends TweakModule {

    public static final TinkersModule INSTANCE = new TinkersModule();

    private static final String CATEGORY_CRAFTING_STATION = "craftingStation";

    public static boolean clearGridToSideInventory = false;

    private TinkersModule() {
        super("tinkers", "Client side tweaks of Tinkers' Construct", ModuleSide.CLIENT);
    }

    @Override
    protected void loadConfig(Configuration config) {
        clearGridToSideInventory = config.getBoolean(
            "clearGridToSideInventory",
            CATEGORY_CRAFTING_STATION,
            clearGridToSideInventory,
            "Ctrl + Shift + click on the clear button of Crafting Tweaks moves the crafting grid of a Crafting "
                + "Station into the inventory attached to its side. What does not fit stays in the grid. Needs a "
                + "restart");
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        if (clearGridToSideInventory && Loader.isModLoaded("TConstruct") && Loader.isModLoaded("craftingtweaks")) {
            CraftingStationClear.register();
        }
    }
}
