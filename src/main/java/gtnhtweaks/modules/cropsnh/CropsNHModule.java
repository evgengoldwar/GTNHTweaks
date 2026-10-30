package gtnhtweaks.modules.cropsnh;

import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import gtnhtweaks.core.TweakModule;
import gtnhtweaks.modules.cropsnh.client.CropsClient;

/**
 * Tweaks of CropsNH. Needs GTNHTweaks on both sides: the client asks, the server places the crop sticks and plants;
 * a server without it answers nothing and the game plays as without the module.
 */
public final class CropsNHModule extends TweakModule {

    public static final CropsNHModule INSTANCE = new CropsNHModule();

    public static final String MOD_ID = "cropsnh";
    private static final String CATEGORY_PLANTING = "planting";

    public static boolean plantWithSticks = true;
    /** On the client: the server it plays on answers {@link CropsPackets.Plant}. */
    public static volatile boolean serverPlants;

    private CropsNHModule() {
        super("cropsnh", "Tweaks of CropsNH", ModuleSide.BOTH);
    }

    @Override
    protected void loadConfig(Configuration config) {
        config.addCustomCategoryComment(CATEGORY_PLANTING, "Planting seeds");
        plantWithSticks = config.getBoolean(
            "plantWithSticks",
            CATEGORY_PLANTING,
            plantWithSticks,
            "With crop sticks in the off hand of Backhand and a seed in the main hand, right click on a soil the seed "
                + "grows on places the crop sticks and plants the seed in them at once. Sneaking places the sticks "
                + "as usual. Needs GTNHTweaks on the server too");
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        if (!Loader.isModLoaded(MOD_ID)) return;
        CropsServer.register();
        if (event.getSide()
            .isClient()) CropsClient.register();
    }

    /** Whether the module is on and CropsNH is there: the packets of the module do nothing otherwise. */
    public static boolean isActive() {
        return INSTANCE.isEnabled() && Loader.isModLoaded(MOD_ID);
    }
}
