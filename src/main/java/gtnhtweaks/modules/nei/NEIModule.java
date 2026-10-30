package gtnhtweaks.modules.nei;

import net.minecraftforge.common.config.Configuration;

import gtnhtweaks.core.TweakModule;

/** Client side tweaks of NotEnoughItems. Does nothing when NEI is not installed. */
public final class NEIModule extends TweakModule {

    public static final NEIModule INSTANCE = new NEIModule();

    private static final String CATEGORY_CRAFTING_TREE = "craftingTree";
    private static final String CATEGORY_RECIPES = "recipes";

    public static boolean preciseDrag = false;
    public static boolean smoothScroll = false;
    public static int scrollTimeMs = 120;
    public static boolean fillOneCraftWithCtrl = false;

    private NEIModule() {
        super("nei", "Client side tweaks of NotEnoughItems", ModuleSide.CLIENT);
    }

    @Override
    protected void loadConfig(Configuration config) {
        config.addCustomCategoryComment(
            CATEGORY_CRAFTING_TREE,
            "Movement in the crafting tree of the bookmarks. Changing preciseDrag or smoothScroll from off to on needs "
                + "a restart");
        preciseDrag = config.getBoolean(
            "preciseDrag",
            CATEGORY_CRAFTING_TREE,
            preciseDrag,
            "Move the tree by the exact mouse position while dragging. NEI moves it in steps of the GUI scale, "
                + "3 screen pixels at GUI scale 3, which feels like lag");
        smoothScroll = config.getBoolean(
            "smoothScroll",
            CATEGORY_CRAFTING_TREE,
            smoothScroll,
            "Animate scrolling and zooming with the mouse wheel instead of jumping");
        scrollTimeMs = config.getInt(
            "scrollTime",
            CATEGORY_CRAFTING_TREE,
            scrollTimeMs,
            10,
            1000,
            "How long a wheel scroll or zoom takes, in milliseconds");

        fillOneCraftWithCtrl = config.getBoolean(
            "fillOneCraftWithCtrl",
            CATEGORY_RECIPES,
            fillOneCraftWithCtrl,
            "Ctrl + Shift + click on the + of a recipe puts the items of one craft in the crafting grid, Shift + click "
                + "still as many as possible. Needs a restart");
    }
}
