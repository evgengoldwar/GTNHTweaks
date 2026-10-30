package gtnhtweaks.modules.bees;

import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import gtnhtweaks.core.TweakModule;
import gtnhtweaks.modules.bees.client.BeeRenderer;

/**
 * Bees of Forestry and of the mods adding bees to it (GregTech, Magic Bees, Extra Bees and others) drawn as 3D bees,
 * like the bees of Productive Bees in newer versions. Does nothing when Forestry is not installed.
 */
public final class BeesModule extends TweakModule {

    public static final BeesModule INSTANCE = new BeesModule();

    private static final String CATEGORY_WHERE = "where";
    private static final String CATEGORY_LOOK = "look";

    public static boolean inHand = false;
    public static boolean inInventory = false;
    public static boolean onGround = false;
    public static boolean accessories = false;
    public static boolean carryProduct = false;
    public static boolean moods = false;

    private BeesModule() {
        super(
            "bees",
            "Bees of Forestry and its addons drawn as 3D bees, each dressed for what it is",
            ModuleSide.CLIENT);
    }

    @Override
    protected void loadConfig(Configuration config) {
        config.addCustomCategoryComment(
            CATEGORY_WHERE,
            "Where the drones, princesses, queens and larvae are drawn as 3D bees. Where it is off, they are drawn as "
                + "usual");
        inHand = config.getBoolean("inHand", CATEGORY_WHERE, inHand, "In the hands, in first and third person");
        inInventory = config.getBoolean(
            "inInventory",
            CATEGORY_WHERE,
            inInventory,
            "In the inventory slots, the slots of apiaries and other machines and in NEI");
        onGround = config.getBoolean("onGround", CATEGORY_WHERE, onGround, "When a bee lies on the ground");

        config.addCustomCategoryComment(
            CATEGORY_LOOK,
            "Every bee has the colors of its species and a face of its own character: the aggressive ones frown, the "
                + "noble ones smirk, the tipsy ones have spiral eyes. The drone is the smallest, the princess wears a "
                + "tiara, the queen is the biggest, with a crown and a royal cape. The larva is a little grub");
        accessories = config.getBoolean(
            "accessories",
            CATEGORY_LOOK,
            accessories,
            "Every species is dressed for what it is, what it gives or where it comes from: the metal bees wear "
                + "miner helmets, the magic ones wizard hats, the icy ones knitted caps, the water ones snorkels, "
                + "the radioactive ones gas masks and glow, and so on");
        carryProduct = config.getBoolean(
            "carryProduct",
            CATEGORY_LOOK,
            carryProduct,
            "A bee carries what it produces, its comb or its specialty, unless it holds a tool of its own");
        moods = config.getBoolean(
            "moods",
            CATEGORY_LOOK,
            moods,
            "A bee feels where it is: it sleeps at night (the nocturnal ones in the day), sweats where it is too hot "
                + "for it, shivers where it is too cold, and in the rain hides under an umbrella, unless it is a "
                + "tolerant flyer, who laughs");
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        if (Loader.isModLoaded("Forestry")) BeeRenderer.register();
    }
}
