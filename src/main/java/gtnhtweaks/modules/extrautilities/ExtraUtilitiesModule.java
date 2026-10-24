package gtnhtweaks.modules.extrautilities;

import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import gtnhtweaks.core.TweakModule;
import gtnhtweaks.modules.extrautilities.client.HealingAxePlant;

/** Client side tweaks of Extra Utilities. Does nothing when it is not installed. */
public final class ExtraUtilitiesModule extends TweakModule {

    public static final ExtraUtilitiesModule INSTANCE = new ExtraUtilitiesModule();

    private static final String CATEGORY_HEALING_AXE = "healingAxe";

    public static boolean plantInHand = true;
    public static boolean plantInInventory = true;
    public static boolean plantOnGround = true;

    private ExtraUtilitiesModule() {
        super("extrautilities", "Client side tweaks of Extra Utilities", true);
    }

    @Override
    protected void loadConfig(Configuration config) {
        config.addCustomCategoryComment(
            CATEGORY_HEALING_AXE,
            "The Healing Axe drawn as a clay pot with a living plant: it sways, blinks and smiles at you, now and then "
                + "shakes with joy, and its leaves turn yellow, fall beside the pot and grow again. Works in the off "
                + "hand of Backhand too. Where it is off, the axe is drawn as usual");
        plantInHand = config.getBoolean(
            "inHand",
            CATEGORY_HEALING_AXE,
            plantInHand,
            "The plant in place of the axe in the hands, in first and third person, the off hand of Backhand included");
        plantInInventory = config
            .getBoolean("inInventory", CATEGORY_HEALING_AXE, plantInInventory, "The plant in the inventory slots");
        plantOnGround = config
            .getBoolean("onGround", CATEGORY_HEALING_AXE, plantOnGround, "The plant when the axe lies on the ground");
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        if (Loader.isModLoaded("ExtraUtilities")) HealingAxePlant.register();
    }
}
