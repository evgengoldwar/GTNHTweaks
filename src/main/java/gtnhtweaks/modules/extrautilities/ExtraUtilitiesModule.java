package gtnhtweaks.modules.extrautilities;

import java.util.Locale;

import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import gtnhtweaks.core.TweakModule;
import gtnhtweaks.modules.extrautilities.client.HealingAxeRenderer;

/** Client side tweaks of Extra Utilities. Does nothing when it is not installed. */
public final class ExtraUtilitiesModule extends TweakModule {

    public static final ExtraUtilitiesModule INSTANCE = new ExtraUtilitiesModule();

    private static final String CATEGORY_HEALING_AXE = "healingAxe";

    /** What the Healing Axe is drawn as. */
    public enum Companion {
        /** A clay pot with a living plant. */
        PLANT,
        /** A glass tank with a goldfish. */
        AQUARIUM,
        /** A slime in a glass jar. */
        SLIME,
        /** An iron lantern with a fire spirit, hanging from the hand. */
        LANTERN,
        /** A peashooter in the clay pot, shooting peas. */
        PEASHOOTER,
        /** A sunflower in the clay pot, making suns. */
        SUNFLOWER
    }

    public static Companion companion = Companion.PLANT;
    public static boolean inHand = true;
    public static boolean holdingArm = true;
    public static boolean moods = true;
    public static boolean inInventory = true;
    public static boolean onGround = true;

    private ExtraUtilitiesModule() {
        super("extrautilities", "Client side tweaks of Extra Utilities", true);
    }

    @Override
    protected void loadConfig(Configuration config) {
        config.addCustomCategoryComment(
            CATEGORY_HEALING_AXE,
            "The Healing Axe drawn as a little living companion that blinks and smiles at you. Works in the off hand of "
                + "Backhand too. Where it is off, the axe is drawn as usual");
        String[] names = new String[Companion.values().length];
        for (Companion c : Companion.values()) names[c.ordinal()] = c.name();
        try {
            companion = Companion.valueOf(
                config
                    .getString(
                        "model",
                        CATEGORY_HEALING_AXE,
                        Companion.PLANT.name(),
                        "PLANT: a clay pot with a plant that sways, its leaves falling and growing again.\n"
                            + "AQUARIUM: a glass tank with a goldfish swimming round, seaweed and bubbles.\n"
                            + "SLIME: a slime in a jar, hopping about and bumping the lid.\n"
                            + "LANTERN: an iron lantern with a fire spirit in it, hanging from the hand and swinging "
                            + "as you turn and walk.\n"
                            + "PEASHOOTER: a peashooter in the clay pot, shooting a pea every few seconds.\n"
                            + "SUNFLOWER: a sunflower in the clay pot, swaying its head and making a sun now and then",
                        names)
                    .trim()
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            companion = Companion.PLANT;
        }
        inHand = config.getBoolean(
            "inHand",
            CATEGORY_HEALING_AXE,
            inHand,
            "In place of the axe in the hands, in first and third person, the off hand of Backhand included");
        holdingArm = config.getBoolean(
            "holdingArm",
            CATEGORY_HEALING_AXE,
            holdingArm,
            "In first person the arm of the player holds it, the arm vanilla draws for an empty hand, instead of it "
                + "floating in the air. The left arm in the off hand of Backhand. The lantern hangs from its hand");
        moods = config.getBoolean(
            "moods",
            CATEGORY_HEALING_AXE,
            moods,
            "It feels where it is: it sleeps at night, reacts to the rain falling on it and to hot biomes and the "
                + "Nether");
        inInventory = config.getBoolean("inInventory", CATEGORY_HEALING_AXE, inInventory, "In the inventory slots");
        onGround = config.getBoolean("onGround", CATEGORY_HEALING_AXE, onGround, "When the axe lies on the ground");
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        if (Loader.isModLoaded("ExtraUtilities")) HealingAxeRenderer.register();
    }
}
