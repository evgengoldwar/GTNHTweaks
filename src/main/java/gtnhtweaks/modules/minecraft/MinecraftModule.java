package gtnhtweaks.modules.minecraft;

import java.util.Locale;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import gtnhtweaks.core.ModuleManager;
import gtnhtweaks.core.TweakModule;
import gtnhtweaks.modules.minecraft.client.BlockChunks;
import gtnhtweaks.modules.minecraft.client.FallingLeaves;
import gtnhtweaks.modules.minecraft.client.NewItems;
import gtnhtweaks.modules.minecraft.client.OutlineClient;
import gtnhtweaks.modules.minecraft.client.OutlineScreen;
import gtnhtweaks.modules.minecraft.client.ParticleRain;
import gtnhtweaks.modules.minecraft.client.PickupNotifier;
import gtnhtweaks.modules.minecraft.client.ScreenBackground;
import gtnhtweaks.modules.minecraft.client.WaterSplashes;

/**
 * Tweaks of Minecraft itself: the outline of the looked at block, the chat, the inventories, the background of the
 * screens, the splashes in water and the resource pack screen.
 */
public final class MinecraftModule extends TweakModule {

    public static final MinecraftModule INSTANCE = new MinecraftModule();

    public static final String CATEGORY_OUTLINE = "blockOutline";
    public static final String CATEGORY_CHAT = "chat";
    public static final String CATEGORY_PLAYER = "player";
    public static final String CATEGORY_INVENTORY = "inventory";
    public static final String CATEGORY_BACKGROUND = "screenBackground";
    public static final String CATEGORY_SPLASHES = "waterSplashes";
    public static final String CATEGORY_RESOURCE_PACKS = "resourcePacks";
    public static final String CATEGORY_SCREEN_SHAKE = "screenShake";
    public static final String CATEGORY_PICKUPS = "pickupNotifier";
    public static final String CATEGORY_LEAVES = "fallingLeaves";
    public static final String CATEGORY_BLOCK_CHUNKS = "blockChunks";
    public static final String CATEGORY_WEATHER = "particleRain";

    /** NORMAL: hidden by other blocks, like vanilla. THROUGH_BLOCKS: seen through every block. */
    public enum OutlineMode {
        NORMAL,
        THROUGH_BLOCKS
    }

    /** DIM: the dark gradient of vanilla. BLUR: the world blurred behind the screen, as in newer versions. */
    public enum BackgroundMode {
        DIM,
        BLUR
    }

    /** Where the list of picked up items is: in a corner or the middle of a side. */
    public enum PickupPosition {
        BOTTOM_RIGHT,
        MIDDLE_RIGHT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        MIDDLE_LEFT,
        TOP_LEFT;

        public boolean right() {
            return this == BOTTOM_RIGHT || this == MIDDLE_RIGHT || this == TOP_RIGHT;
        }
    }

    public static final int VANILLA_COLOR = 0x66000000;
    public static final float VANILLA_WIDTH = 2F;

    public static boolean outlineEnabled = true;
    /** ARGB. */
    public static int outlineColor = VANILLA_COLOR;
    public static float outlineWidth = VANILLA_WIDTH;
    public static OutlineMode outlineMode = OutlineMode.NORMAL;
    /** ARGB color of the filled faces, alpha 0 for no fill. */
    public static int fillColor = 0;
    /** Lines in the colors of the rainbow, flowing along the outline. */
    public static boolean rainbow = false;
    /** Rounds of the rainbow per second. */
    public static float rainbowSpeed = 0.5F;

    /** Heads of the players before their messages in the chat. */
    public static boolean chatHeads = true;
    /** A bar before the messages not written by a player, as in the chat of newer versions. */
    public static boolean systemMessageBar = true;
    /** Only the head before the messages of players, without their name. */
    public static boolean hideNames = false;

    /** The hand swings when the player throws an item, as in newer versions. */
    public static boolean dropSwing = true;

    /** The item of a slot squeezes and springs back when the mouse comes onto it. */
    public static boolean hoverBounce = true;
    public static boolean newItemStars = true;

    /** Darkness of the vanilla gradient, in percent of opacity at the top. */
    public static final int VANILLA_DARKNESS = 75;
    public static BackgroundMode backgroundMode = BackgroundMode.DIM;
    /** Darkness of the gradient over the world in the DIM mode, in percent, 75 in vanilla. */
    public static int darkness = VANILLA_DARKNESS;
    /** Strength of the blur, 1 to 10. */
    public static int blurStrength = 5;
    /** Darkness of the gradient over the blurred world, in percent. */
    public static int blurDarkness = 20;

    /** Splashes and rings of waves when something falls into water. */
    public static boolean waterSplashes = true;
    public static boolean splashItems = true;
    public static boolean splashMobs = true;
    public static boolean splashPlayers = true;
    /** Most splashes at the same time. */
    public static int maxSplashes = 48;
    /** Ripples of the rain on water, in percent of the usual amount, 0 for none. */
    public static int rainRipples = 100;

    /** Little cubes a broken block falls apart into, 0 for the particles of vanilla. */
    public static int blockChunks = 14;
    /** Size of the cubes in percent. */
    public static int blockChunkSize = 100;
    /** Seconds the cubes lie before they shrink away. */
    public static float blockChunkSeconds = 2.5F;

    /** Leaves falling from the trees, in percent of the usual amount, 0 for none. */
    public static int fallingLeaves = 100;

    /** Rain, snow and sand dust of particles, in percent of the usual amount, 0 for none. */
    public static int particleRain = 100;
    /** The streaks of rain and snow of vanilla are hidden while the particles fall. */
    public static boolean hideVanillaRain = true;

    /** A list at the side of the screen of the items picked up. */
    public static boolean pickupNotifier = true;
    public static PickupPosition pickupPosition = PickupPosition.BOTTOM_RIGHT;
    /** Seconds a line stays. */
    public static int pickupSeconds = 5;
    /** Most lines at the same time: the oldest go first. */
    public static int pickupMaxLines = 10;

    /** The camera shakes when something explodes near the player. */
    public static boolean screenShake = true;
    /** Strength of the shake in percent. */
    public static int screenShakeStrength = 100;

    /** The resource packs are dragged with the mouse between and inside the lists of the resource pack screen. */
    public static boolean dragResourcePacks = true;
    /** Up to 10 saved sets of selected resource packs, chosen on the resource pack screen. */
    public static boolean resourcePackPresets = true;
    /** Choosing a preset applies it at once, as the Done button does, instead of only filling the lists. */
    public static boolean applyPresetAtOnce = false;
    /** The Default entry of the selected list is hidden while other packs are selected. */
    public static boolean hideDefaultPack = true;
    /** Done without a change of the packs closes the screen at once, without reloading the resources. */
    public static boolean skipReloadWithoutChanges = true;

    private MinecraftModule() {
        super("minecraft", "Tweaks of Minecraft itself, like the outline of the looked at block", true);
    }

    @Override
    protected void loadConfig(Configuration config) {
        config.addCustomCategoryComment(
            CATEGORY_OUTLINE,
            "The outline of the looked at block. The settings can also be changed in game, with a live preview, "
                + "on the screen opened by the key \"Block outline\" (Controls, GTNHTweaks)");
        Property enabled = config.get(
            CATEGORY_OUTLINE,
            "enabled",
            true,
            "Draw the block outline in your own color, width and mode. Its look is set in game, with a live "
                + "preview, on the screen of the key \"Block outline\" (O by default, Controls, GTNHTweaks). "
                + "Turning it on needs a restart for the key");
        outlineEnabled = enabled.getBoolean(true);
        outlineColor = parseColor(
            config.getString(
                "color",
                CATEGORY_OUTLINE,
                formatColor(VANILLA_COLOR),
                "Color of the lines as AARRGGBB in hex: alpha, red, green, blue. 66000000 is the vanilla one"),
            VANILLA_COLOR);
        outlineWidth = config.getFloat(
            "width",
            CATEGORY_OUTLINE,
            VANILLA_WIDTH,
            0.5F,
            10F,
            "Width of the lines in pixels, 2 in vanilla");
        try {
            outlineMode = OutlineMode.valueOf(
                config
                    .getString(
                        "mode",
                        CATEGORY_OUTLINE,
                        OutlineMode.NORMAL.name(),
                        "NORMAL: hidden behind other blocks, like vanilla. THROUGH_BLOCKS: seen through every block, "
                            + "like a wireframe",
                        new String[] { OutlineMode.NORMAL.name(), OutlineMode.THROUGH_BLOCKS.name() })
                    .trim()
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            outlineMode = OutlineMode.NORMAL;
        }
        // the fill had the color of the lines with an opacity of its own before it got its own color
        String fillOpacity = removeOldKey(config, CATEGORY_OUTLINE, "fillOpacity");
        int oldFill = 0;
        try {
            float opacity = fillOpacity == null ? 0 : Float.parseFloat(fillOpacity);
            if (opacity > 0) oldFill = Math.round(opacity * 255) << 24 | outlineColor & 0xFFFFFF;
        } catch (NumberFormatException ignored) {}
        fillColor = parseColor(
            config.getString(
                "fillColor",
                CATEGORY_OUTLINE,
                formatColor(oldFill),
                "Color of the filled faces as AARRGGBB in hex, alpha 00 for no fill"),
            0);
        rainbow = config.getBoolean(
            "rainbow",
            CATEGORY_OUTLINE,
            false,
            "Draw the lines in the colors of the rainbow, flowing along the outline. The alpha of the color is kept");
        rainbowSpeed = config
            .getFloat("rainbowSpeed", CATEGORY_OUTLINE, 0.5F, 0.05F, 5F, "Rounds of the rainbow per second");

        chatHeads = config.getBoolean(
            "playerHeads",
            CATEGORY_CHAT,
            chatHeads,
            "Show the head of the player before their messages in the chat. The writer is the online player named "
                + "before the first >, : or » of the message, as in <Name> or [Rank] Name:. Needs a restart");
        hideNames = config.getBoolean(
            "hideNames",
            CATEGORY_CHAT,
            hideNames,
            "With playerHeads: only the head before the messages of players, without the name. Everything before "
                + "the text of the message is hidden, ranks and tags too: <Name> hi and [Rank] Name: hi become hi");
        systemMessageBar = config.getBoolean(
            "systemMessageBar",
            CATEGORY_CHAT,
            systemMessageBar,
            "Show a bar before the messages not written by a player, like the ones of the server, as in the chat "
                + "of newer versions. Needs a restart");

        dropSwing = config.getBoolean(
            "dropSwing",
            CATEGORY_PLAYER,
            dropSwing,
            "Swing the hand when throwing an item, as in newer versions: with the drop key and out of any inventory. "
                + "The other players see the swing too. Needs a restart");

        hoverBounce = config.getBoolean(
            "hoverBounce",
            CATEGORY_INVENTORY,
            hoverBounce,
            "The item of a slot squeezes and springs back when the mouse comes onto it, to show that it is hovered. "
                + "In every inventory, of vanilla and of mods, and in the NEI panels. Needs a restart");
        newItemStars = config.getBoolean(
            "newItemStars",
            CATEGORY_INVENTORY,
            newItemStars,
            "A little star in the corner of the slots of new items, until the mouse is brought onto them in an "
                + "inventory. An item is new when it comes into the inventory and was not in it before since joining "
                + "the world: picked up, crafted or taken from a chest. Shown on the hotbar too");

        config.addCustomCategoryComment(
            CATEGORY_BACKGROUND,
            "The background of the menus and inventories opened in a world: the world dimmed or blurred behind them");
        try {
            backgroundMode = BackgroundMode.valueOf(
                config
                    .getString(
                        "mode",
                        CATEGORY_BACKGROUND,
                        BackgroundMode.DIM.name(),
                        "DIM: the world is darkened, like vanilla, by the darkness below. BLUR: the world is blurred, "
                            + "as in newer versions, by blurStrength, and darkened by blurDarkness. Without shader or "
                            + "framebuffer support BLUR falls back to DIM",
                        new String[] { BackgroundMode.DIM.name(), BackgroundMode.BLUR.name() })
                    .trim()
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            backgroundMode = BackgroundMode.DIM;
        }
        darkness = config.getInt(
            "darkness",
            CATEGORY_BACKGROUND,
            VANILLA_DARKNESS,
            0,
            100,
            "DIM mode: how dark the world is behind the screens, in percent. 75 is vanilla, 0 no darkening");
        blurStrength = config
            .getInt("blurStrength", CATEGORY_BACKGROUND, blurStrength, 1, 10, "BLUR mode: how strong the blur is");
        blurDarkness = config.getInt(
            "blurDarkness",
            CATEGORY_BACKGROUND,
            blurDarkness,
            0,
            100,
            "BLUR mode: how dark the blurred world is, in percent. 0 for no darkening");

        config.addCustomCategoryComment(
            CATEGORY_SPLASHES,
            "Splashes of what falls into water, in the look of the splashes of newer versions' mods: a square wall of "
                + "water with foam edges and a higher jet in the middle, a ring of foam on the surface and pixel "
                + "droplets that make ripples where they fall back. Only on the client, off with the Minimal "
                + "particles of the video settings");
        waterSplashes = config
            .getBoolean("enabled", CATEGORY_SPLASHES, waterSplashes, "Splashes when something falls into water");
        splashItems = config.getBoolean("items", CATEGORY_SPLASHES, splashItems, "Splashes of dropped items");
        splashMobs = config.getBoolean("mobs", CATEGORY_SPLASHES, splashMobs, "Splashes of mobs and animals");
        splashPlayers = config.getBoolean("players", CATEGORY_SPLASHES, splashPlayers, "Splashes of players");
        maxSplashes = config.getInt(
            "maxSplashes",
            CATEGORY_SPLASHES,
            maxSplashes,
            1,
            256,
            "Most splashes at the same time: more falling at once make none, so a mass of items does not lag");
        rainRipples = config.getInt(
            "rainRipples",
            CATEGORY_SPLASHES,
            rainRipples,
            0,
            300,
            "Little ripple rings where the rain falls on still water open to the sky, in percent of the usual "
                + "amount: 0 for none, 200 for twice as many. More in heavier rain, none in snow or deserts");

        blockChunks = config.getInt(
            "count",
            CATEGORY_BLOCK_CHUNKS,
            blockChunks,
            0,
            40,
            "A broken block falls apart into this many little cubes of itself, with the textures of its faces, that "
                + "tumble, bounce, lie flat and shrink away, instead of the flat pixels of vanilla. 0 for the pixels "
                + "of vanilla. Only solid blocks; half as many with Decreased particles, vanilla with Minimal");
        blockChunkSize = config.getInt(
            "size",
            CATEGORY_BLOCK_CHUNKS,
            blockChunkSize,
            25,
            300,
            "Size of the cubes in percent: 100 makes them 2 to 4 pixels of a block wide, 200 twice as big");
        blockChunkSeconds = config.getFloat(
            "seconds",
            CATEGORY_BLOCK_CHUNKS,
            blockChunkSeconds,
            0.25F,
            30F,
            "How long the cubes lie on the ground before they shrink away, in seconds: less to have them gone "
                + "sooner. Every cube lies a bit more or less, so they do not all go at once");

        fallingLeaves = config.getInt(
            "amount",
            CATEGORY_LEAVES,
            fallingLeaves,
            0,
            300,
            "Leaves falling from the trees around you: bits of the leaf texture in the color of the biome that flutter "
                + "down with the wind, lie on the ground or float on water and fade. In percent of the usual amount: "
                + "0 for none, 200 for twice as many. For the leaves of every mod. Half as many with Decreased "
                + "particles, none with Minimal");

        config.addCustomCategoryComment(
            CATEGORY_WEATHER,
            "Weather of particles: rain of separate drops that splash on the ground, snow of swaying flakes that lie "
                + "a moment, and sand dust blown along the ground in deserts and mesas when it rains. Only on the "
                + "client, off with the Minimal particles of the video settings");
        particleRain = config.getInt(
            "amount",
            CATEGORY_WEATHER,
            particleRain,
            0,
            300,
            "How much falls, in percent: 0 for none and the rain of vanilla, 200 for twice as much. It follows the "
                + "strength of the rain; half with Decreased particles");
        hideVanillaRain = config.getBoolean(
            "hideVanillaRain",
            CATEGORY_WEATHER,
            hideVanillaRain,
            "Hide the streaks of rain and snow of vanilla while the particles fall, so it does not rain twice. The "
                + "sound of the rain stays. Worlds with a weather of their own, as on other planets, keep it");

        config.addCustomCategoryComment(
            CATEGORY_PICKUPS,
            "A list at the side of the screen of the items you pick up, like \"64x Cobblestone\" with the icon of "
                + "the item, in the color of its rarity. Only on the client");
        pickupNotifier = config.getBoolean(
            "enabled",
            CATEGORY_PICKUPS,
            pickupNotifier,
            "Show the picked up items. Picking up more of an item while its line is shown adds to its count. Items "
                + "taken from chests or crafted are not shown");
        String[] positions = new String[PickupPosition.values().length];
        for (int i = 0; i < positions.length; i++) positions[i] = PickupPosition.values()[i].name();
        try {
            pickupPosition = PickupPosition.valueOf(
                config
                    .getString(
                        "position",
                        CATEGORY_PICKUPS,
                        PickupPosition.BOTTOM_RIGHT.name(),
                        "Where the list is: a corner or the middle of the left or right side of the screen",
                        positions)
                    .trim()
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            pickupPosition = PickupPosition.BOTTOM_RIGHT;
        }
        pickupSeconds = config
            .getInt("seconds", CATEGORY_PICKUPS, pickupSeconds, 1, 30, "How many seconds a line stays on the screen");
        pickupMaxLines = config.getInt(
            "maxLines",
            CATEGORY_PICKUPS,
            pickupMaxLines,
            1,
            30,
            "Most lines at the same time: with more, the oldest go first");

        config.addCustomCategoryComment(
            CATEGORY_SCREEN_SHAKE,
            "The camera shakes when something explodes near the player: TNT, creepers, GregTech machines and "
                + "every other explosion. Only on the client");
        screenShake = config.getBoolean(
            "enabled",
            CATEGORY_SCREEN_SHAKE,
            screenShake,
            "Shake the camera on explosions. The bigger and the closer, the stronger; it calms down in about a second");
        screenShakeStrength = config.getInt(
            "strength",
            CATEGORY_SCREEN_SHAKE,
            screenShakeStrength,
            0,
            300,
            "How strong the shake is, in percent: 50 half as strong, 200 twice as strong");

        config.addCustomCategoryComment(
            CATEGORY_RESOURCE_PACKS,
            "The resource pack screen: dragging the packs with the mouse, presets of selected packs and a quick Done");
        dragResourcePacks = config.getBoolean(
            "dragAndDrop",
            CATEGORY_RESOURCE_PACKS,
            dragResourcePacks,
            "Drag the resource packs with the mouse by their name: from one list to the other and to any place in a "
                + "list. A line shows where the pack goes; near the top or bottom edge the list scrolls. The arrows on "
                + "the icons work as before");
        resourcePackPresets = config.getBoolean(
            "presets",
            CATEGORY_RESOURCE_PACKS,
            resourcePackPresets,
            "Up to 10 saved sets of selected packs in their order, to switch between them in one click or with the "
                + "keys 1 to 0. Shown left of the lists of the resource pack screen, or behind a Presets button when "
                + "the screen is too narrow. Kept in config/GTNHTweaks/resourcepack_presets.json");
        applyPresetAtOnce = config.getBoolean(
            "applyPresetAtOnce",
            CATEGORY_RESOURCE_PACKS,
            applyPresetAtOnce,
            "With presets: choosing a preset applies it at once and reloads the resources, as the Done button does. "
                + "Off: it only puts the packs into the lists, to look at them and press Done");
        hideDefaultPack = config.getBoolean(
            "hideDefaultPack",
            CATEGORY_RESOURCE_PACKS,
            hideDefaultPack,
            "Hide the Default entry of the selected packs while at least one other pack is selected: the default "
                + "look is always under them, so it only takes room. It comes back when no pack is selected");
        skipReloadWithoutChanges = config.getBoolean(
            "skipReloadWithoutChanges",
            CATEGORY_RESOURCE_PACKS,
            skipReloadWithoutChanges,
            "Done closes the resource pack screen at once, like Esc, when the selected packs and their order did not "
                + "change: vanilla reloads all the resources anyway, which takes long in a big pack");
    }

    /** The look of the outline is set on its screen in game, so the settings screen only shows the switch. */
    @Override
    public boolean showInSettings(String category, String key) {
        return !CATEGORY_OUTLINE.equalsIgnoreCase(category) || "enabled".equals(key);
    }

    @Override
    public String settingsButton(String category) {
        return CATEGORY_OUTLINE.equalsIgnoreCase(category) ? "Open the outline editor" : null;
    }

    @Override
    public void openSettingsButton(String category, Object parent) {
        // through Object, so this class, read in the coremod phase, does not load GuiScreen before its mixins
        if (CATEGORY_OUTLINE.equalsIgnoreCase(category)) OutlineScreen.open(parent);
    }

    /** Writes the current outline settings to the config file, after a change on the outline screen. */
    public static void saveOutline() {
        // through Configuration.get, which finds the category whatever its case: Forge keeps it lower case
        Configuration config = ModuleManager.getConfig(INSTANCE);
        config.get(CATEGORY_OUTLINE, "color", formatColor(VANILLA_COLOR))
            .set(formatColor(outlineColor));
        // getFloat keeps floats as text properties: written the same way
        config.get(CATEGORY_OUTLINE, "width", Float.toString(VANILLA_WIDTH))
            .set(Float.toString(outlineWidth));
        config.get(CATEGORY_OUTLINE, "mode", OutlineMode.NORMAL.name())
            .set(outlineMode.name());
        config.get(CATEGORY_OUTLINE, "fillColor", formatColor(0))
            .set(formatColor(fillColor));
        config.get(CATEGORY_OUTLINE, "rainbow", false)
            .set(rainbow);
        config.get(CATEGORY_OUTLINE, "rainbowSpeed", Float.toString(0.5F))
            .set(Float.toString(rainbowSpeed));
        ModuleManager.saveAndReload();
    }

    public static String formatColor(int argb) {
        return String.format("%08X", argb);
    }

    public static int parseColor(String text, int fallback) {
        try {
            String hex = text.trim();
            if (hex.startsWith("#")) hex = hex.substring(1);
            if (hex.startsWith("0x") || hex.startsWith("0X")) hex = hex.substring(2);
            long value = Long.parseLong(hex, 16);
            if (hex.length() <= 6) value |= 0xFF000000L;
            return (int) value;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** True if the outline is drawn with the settings of this module. */
    public static boolean outlineActive() {
        return INSTANCE.isEnabled() && outlineEnabled;
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        if (outlineEnabled) OutlineClient.register();
        ScreenBackground.register();
        WaterSplashes.register();
        NewItems.register();
        PickupNotifier.register();
        FallingLeaves.register();
        BlockChunks.register();
        ParticleRain.register();
    }
}
