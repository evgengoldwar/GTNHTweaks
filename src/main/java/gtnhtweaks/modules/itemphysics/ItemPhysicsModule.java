package gtnhtweaks.modules.itemphysics;

import net.minecraftforge.common.config.Configuration;

import gtnhtweaks.core.TweakModule;

/**
 * Dropped items lie on the ground as if they fell there: flat items on their side, blocks on a face, stacks as a small
 * pile. In the air they tumble and fall over when they land, instead of hovering and spinning in place.
 */
public final class ItemPhysicsModule extends TweakModule {

    public static final ItemPhysicsModule INSTANCE = new ItemPhysicsModule();

    private static final String CATEGORY_ITEMS = "items";

    /** Items tumble while they fly and fall over on a side when they land. */
    public static boolean tumbleInAir = true;
    /** Speed of the tumbling, 1 is the normal one. */
    public static float tumbleSpeed = 1F;
    /** A stack lies as a pile of up to five items, like the vanilla stacks of up to five copies. */
    public static boolean piles = true;
    /** Items next to a wall are moved away from it, so they do not go into the blocks around them. */
    public static boolean keepOutOfBlocks = true;

    private ItemPhysicsModule() {
        super(
            "itemphysics",
            "Dropped items fall, tumble and lie on the ground instead of hovering and spinning in the air",
            true);
    }

    @Override
    protected void loadConfig(Configuration config) {
        config.addCustomCategoryComment(
            CATEGORY_ITEMS,
            "Dropped items lie on the ground: flat items on their side, blocks on a face. Only the look changes: "
                + "the items are picked up and move as in vanilla");
        tumbleInAir = config.getBoolean(
            "tumbleInAir",
            CATEGORY_ITEMS,
            true,
            "Items tumble while they fly and fall over on a side when they land. Off: they fall lying flat");
        tumbleSpeed = config
            .getFloat("tumbleSpeed", CATEGORY_ITEMS, 1F, 0.1F, 3F, "Speed of the tumbling in the air, 1 is normal");
        piles = config.getBoolean(
            "piles",
            CATEGORY_ITEMS,
            true,
            "A stack lies as a pile of up to five items: flat items on top of each other, blocks side by side");
        keepOutOfBlocks = config.getBoolean(
            "keepOutOfBlocks",
            CATEGORY_ITEMS,
            true,
            "Move items lying next to a wall away from it, so they do not go into the blocks around them");
    }
}
