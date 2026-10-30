package gtnhtweaks.client.settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the settings screen calls the modules, their tabs (the categories of their config) and their settings, with a
 * short description of each, in the order they are shown, and which settings need a restart. A setting not listed
 * here keeps the name made from its key and the comment of its config.
 */
final class SettingsText {

    /** When a changed setting takes effect. */
    enum Restart {
        /** As the config comment says: a restart if it mentions one. */
        DEFAULT,
        /** At once. */
        NONE,
        /** After a game restart. */
        ALWAYS,
        /** At once when turned off; turning it on takes effect after a restart. */
        TURN_ON
    }

    static final class Module {

        final String name, info;
        final Map<String, Tab> tabs = new LinkedHashMap<>();

        Module(String name, String info) {
            this.name = name;
            this.info = info;
        }
    }

    static final class Tab {

        final String name, info;
        final Map<String, Setting> settings = new LinkedHashMap<>();

        Tab(String name, String info) {
            this.name = name;
            this.info = info;
        }
    }

    static final class Setting {

        final String name, info;
        final Restart restart;
        /** What each of a fixed set of values does, by value. */
        final Map<String, String> values = new LinkedHashMap<>();

        Setting(String name, String info, Restart restart) {
            this.name = name;
            this.info = info;
            this.restart = restart;
        }
    }

    private static final Map<String, Module> MODULES = new HashMap<>();
    private static Module module;
    private static Tab tab;
    private static Setting setting;

    private SettingsText() {}

    // ---- lookup

    static Module module(String id) {
        return MODULES.get(id);
    }

    static Tab tab(String moduleId, String category) {
        Module m = MODULES.get(moduleId);
        return m == null ? null : m.tabs.get(category.toLowerCase(Locale.ROOT));
    }

    static Setting setting(String moduleId, String category, String key) {
        Tab t = tab(moduleId, category);
        return t == null ? null : t.settings.get(key);
    }

    /** The categories of the module in the order of their tabs; the ones not listed after them, as given. */
    static List<String> orderTabs(String moduleId, List<String> categories) {
        List<String> ordered = new ArrayList<>();
        Module m = MODULES.get(moduleId);
        if (m != null) {
            for (String name : m.tabs.keySet()) if (categories.contains(name)) ordered.add(name);
        }
        for (String name : categories) if (!ordered.contains(name)) ordered.add(name);
        return ordered;
    }

    /** The keys of the category in the order of their rows; the ones not listed after them, as given. */
    static List<String> orderSettings(String moduleId, String category, List<String> keys) {
        List<String> ordered = new ArrayList<>();
        Tab t = tab(moduleId, category);
        if (t != null) {
            for (String key : t.settings.keySet()) if (keys.contains(key)) ordered.add(key);
        }
        for (String key : keys) if (!ordered.contains(key)) ordered.add(key);
        return ordered;
    }

    // ---- building

    private static void module(String id, String name, String info) {
        module = new Module(name, info);
        MODULES.put(id, module);
    }

    private static void tab(String category, String name, String info) {
        tab = new Tab(name, info);
        // Forge keeps the category names lower case
        module.tabs.put(category.toLowerCase(Locale.ROOT), tab);
    }

    private static void set(String key, String name, String info) {
        set(key, name, info, Restart.DEFAULT);
    }

    private static void set(String key, String name, String info, Restart restart) {
        setting = new Setting(name, info, restart);
        tab.settings.put(key, setting);
    }

    /** What the values of the last setting do, as value, description, value, description... */
    private static void values(String... pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2) setting.values.put(pairs[i], pairs[i + 1]);
    }

    private static void theme() {
        set("theme", "Color theme", "The colors and look of the panels.", Restart.NONE);
        values(
            "SHADOW",
            "Gray beveled panels, like the Shadow UI resource pack",
            "DARK",
            "Flat dark panels",
            "LIGHT",
            "Flat light panels");
    }

    private static void backupPatterns(String what) {
        set("keywords", "Backup keywords", "A " + what + " counts as a backup message only if it matches this.");
        set(
            "ignored",
            "Not a backup start",
            "Backup " + what + "s that do not start a backup, like \"Next backup in 30 minutes\".");
        set("started", "Backup started", "A backup " + what + " matching this means a backup started.");
        set(
            "finished",
            "Backup finished",
            "A backup " + what + " matching this means the backup is done, like \"Done! (00:12)\".");
        set("failed", "Backup failed", "A backup " + what + " matching this means the backup failed.");
    }

    static {
        // ---- look

        module("loadingscreen", "Loading Screen", "The GTNH-style screen shown while the game starts.");
        tab("general", "General", "What the loading screen shows.");
        theme();
        set("showTimer", "Loading timer", "Show how long the game has been loading.");
        set(
            "showPreviousLaunch",
            "Compare with last launch",
            "Show how long the last launch took and how far along this one is.");
        set("showMemory", "Memory usage", "Show how much memory the game uses.");
        set(
            "showLoadTimeInMainMenu",
            "Load time in vanilla main menu",
            "Show the total load time in the top left corner of the vanilla main menu. The GTNH main menu has a "
                + "setting of its own for it.",
            Restart.ALWAYS);
        tab(
            "wall",
            "Block Wall",
            "A wall of blocks builds up behind the loading screen and crumbles away when the game is ready.");
        set("buildWall", "Build the wall", "Show the wall of blocks behind the loading screen.", Restart.ALWAYS);
        set("modBlocks", "Mod blocks", "Use the blocks of your mods too, not only vanilla ones.", Restart.ALWAYS);
        set("modBlockShare", "Share of mod blocks", "How much of the wall is mod blocks, in percent.", Restart.ALWAYS);
        set(
            "rows",
            "Wall size",
            "How many rows of blocks fill the screen height. More rows, smaller blocks.",
            Restart.ALWAYS);
        set(
            "textVeil",
            "Backdrop under the text",
            "How much the background covers the wall behind the text, in percent, so it stays readable.",
            Restart.ALWAYS);
        set(
            "crumbleAtEnd",
            "Crumble at the end",
            "When the game is ready, the wall crumbles down and uncovers the main menu.",
            Restart.NONE);
        set("crumbleSpeed", "Crumble speed", "Speed of the crumbling, in percent: 200 is twice as fast.", Restart.NONE);
        set("crumbleSound", "Crumble sound", "Play a sound while the wall crumbles.", Restart.NONE);
        tab("integration", "Compatibility", "Which other loading screens this one replaces.");
        set(
            "replaceVanillaScreen",
            "Replace the Mojang screen",
            "Show this screen instead of the Mojang logo at the very start.",
            Restart.ALWAYS);
        set(
            "replaceForgeSplash",
            "Replace the Forge splash",
            "Draw over the loading screen of Forge (when config/splash.properties has enabled=true).",
            Restart.ALWAYS);
        set(
            "drawOverBetterLoadingScreen",
            "Draw over BetterLoadingScreen",
            "When BetterLoadingScreen is installed, it keeps tracking the progress and this screen shows it.",
            Restart.ALWAYS);
        tab("text", "Texts", "The words of the loading screen. Latin and Cyrillic letters work.");
        set("subtitle", "Subtitle", "The line under the GTNH logo. Leave it empty to hide it.");
        set("loadingTime", "Timer label", "The caption of the loading timer.");
        set("previousLaunch", "Last launch label", "The caption of the time of the last launch.");
        set("firstLaunch", "First launch text", "Shown instead of the last launch time when there is none yet.");
        set("memory", "Memory label", "The caption of the memory usage.");
        set("loadedIn", "Load time label", "The caption of the load time in the main menu.");
        set("unitHours", "Hours", "The unit of hours of the load time in the main menu.");
        set("unitMinutes", "Minutes", "The unit of minutes of the load time in the main menu.");
        set("unitSeconds", "Seconds", "The unit of seconds of the load time in the main menu.");

        module("mainmenu", "Main Menu", "A main menu in the GTNH style, over an animated circuit board.");
        tab("general", "General", "What the main menu shows.");
        theme();
        set("showSplashText", "Splash text", "Show the yellow splash text, as in vanilla.");
        set("showLoadTime", "Load time", "Show how long the game took to load. Needs the Loading Screen module.");
        set("showModCount", "Mod count", "Show how many mods are loaded.");
        set(
            "showSettingsButton",
            "GTNHTweaks button",
            "A button beside Mods, half of the row each, that opens these settings.");
        set(
            "yieldToResourcePacks",
            "Let resource packs win",
            "When a resource pack changes the logo, the panorama or the buttons, show the vanilla menu with the pack "
                + "instead.");
        set(
            "replaceCustomMainMenu",
            "Replace Custom Main Menu",
            "Show this menu instead of the one of the Custom Main Menu mod, the default menu of GTNH.");
        tab("circuit", "Circuit Board", "The animated circuit board behind the menu, with pulses of current.");
        set("showCircuit", "Show the board", "Draw the circuit board behind the menu.");
        set("currentColors", "Pulse colors", "The colors of the pulses running along the traces.");
        values(
            "TIERS",
            "The colors of the GregTech voltage tiers, LV to UEV",
            "THEME",
            "The accent color of the theme");
        set("rows", "Board size", "How many cells fit the screen height. More rows, finer traces.");
        set("pulseSpeed", "Pulse speed", "How fast the pulses run, in cells per second.");
        set("brightness", "Brightness", "Brightness of the traces and the pulses, in percent.");
        set("chips", "Chips", "Place chips on the board.");
        set("parallax", "Depth effect", "The board moves a little after the mouse.");
        tab("text", "Texts", "The words of the main menu. Latin and Cyrillic letters work.");
        set("subtitle", "Subtitle", "The line under the GTNH logo. Leave it empty to hide it.");
        set("mods", "Mod count label", "The caption of the number of loaded mods.");
        set("settings", "Settings button label", "The label of the GTNHTweaks button beside Mods.");

        module("menus", "Menu Style", "Gives the vanilla, Forge and mod menus the look of GTNH.");
        tab("general", "General", "The look of the menus.");
        theme();
        set(
            "recolorText",
            "Theme text colors",
            "Replace the white and gray text of the menus with the colors of the theme. Needed for the LIGHT theme.");
        set(
            "yieldToResourcePacks",
            "Let resource packs win",
            "When a resource pack replaces the backgrounds or the buttons of the menus, show the pack instead of the "
                + "theme.");
        set(
            "fullFramerate",
            "Full frame rate in menus",
            "Use your frame rate limit in the main menu and the other menus outside a world, instead of the 30 FPS "
                + "of vanilla.");
        tab("screens", "Screens", "Which screens get the look of GTNH.");
        set(
            "themeVanillaScreens",
            "Vanilla and Forge screens",
            "Restyle every Minecraft, Forge and FML screen, and the mod screens built on them, like config screens.",
            Restart.ALWAYS);
        set(
            "themeInGame",
            "Menus in game",
            "Also restyle the menus opened in a world: pause, options... The world stays visible behind them.",
            Restart.ALWAYS);
        set(
            "themeInventories",
            "Dark inventories",
            "Darken the vanilla inventory textures in the DARK and SHADOW themes. Mod inventories keep their look.",
            Restart.ALWAYS);
        set(
            "modList",
            "GTNHTweaks mod list",
            "The Mods buttons open the mod list of GTNHTweaks: logos, search and all the details of every mod.",
            Restart.ALWAYS);
        set(
            "extraScreens",
            "More screens to restyle",
            "Full class names of other screens to restyle. Their subclasses are restyled too.",
            Restart.ALWAYS);
        set(
            "untouchedScreens",
            "Screens left alone",
            "Full class names of screens that keep the vanilla look. Their subclasses too.",
            Restart.ALWAYS);

        // ---- gameplay

        module(
            "minecraft",
            "Vanilla Tweaks",
            "Improvements to Minecraft itself: movement, effects, chat, inventories and resource packs.");
        tab(
            "movement",
            "Crouch & Swim",
            "Crouching and swimming as in newer versions. Only in singleplayer and on servers that allow it.");
        set(
            "enabled",
            "Modern crouch and swim",
            "Crouch lower under slabs, swim while sprinting under water, crawl through gaps one block high.",
            Restart.ALWAYS);
        tab(
            "blockOutline",
            "Block Outline",
            "The outline of the block you look at, in your own color, width and style. Set it up in game with a live "
                + "preview on the screen of the Block outline key (O).");
        set(
            "enabled",
            "Custom block outline",
            "Draw the outline of the block you look at with your own settings.",
            Restart.TURN_ON);
        tab("blockChunks", "Block Breaking", "A broken block falls apart into little 3D pieces of itself.");
        set("enabled", "3D block pieces", "Off: the flat pixels of vanilla.", Restart.NONE);
        set("count", "Pieces per block", "How many pieces a block breaks into. 0: no particles at all.");
        set("size", "Piece size", "Size of the pieces, in percent: 200 makes them twice as big.");
        set("seconds", "Time on the ground", "How long the pieces lie before they shrink away, in seconds.");
        tab("fallingLeaves", "Falling Leaves", "Leaves flutter down from the trees around you, in the biome's color.");
        set("amount", "Amount", "In percent of the usual amount. 0: no leaves, 200: twice as many.");
        tab(
            "particleRain",
            "Rain & Snow",
            "Rain falls as drops that splash, snow as swaying flakes, and sand blows across deserts when it rains.");
        set("amount", "Amount", "How much falls, in percent. 0: the rain of vanilla, 200: twice as much.");
        set(
            "hideVanillaRain",
            "Hide vanilla rain",
            "Hide the rain streaks of vanilla while the drops fall, so it does not rain twice.");
        tab("waterSplashes", "Water Splashes", "Splashes, foam and ripples when something falls into water.");
        set("enabled", "Water splashes", "Splash when something falls into water.");
        set("items", "Items", "Splashes of dropped items.");
        set("mobs", "Mobs and animals", "Splashes of mobs and animals.");
        set("players", "Players", "Splashes of players.");
        set("maxSplashes", "Splash limit", "The most splashes at once, so a mass of falling items does not lag.");
        set("rainRipples", "Rain ripples", "Ripples where the rain falls on still water, in percent. 0: none.");
        tab("screenShake", "Screen Shake", "The camera shakes when something explodes near you.");
        set("enabled", "Screen shake", "Shake the camera on explosions. The bigger and closer, the stronger.");
        set("strength", "Strength", "In percent: 50 is half as strong, 200 twice as strong.");
        tab("pickupNotifier", "Item Pickups", "A list at the side of the screen of the items you pick up.");
        set("enabled", "Show picked up items", "Like \"64x Cobblestone\", with the icon of the item.");
        set("position", "Position", "Where on the screen the list is.");
        values(
            "BOTTOM_RIGHT",
            "Bottom right corner",
            "MIDDLE_RIGHT",
            "Middle of the right side",
            "TOP_RIGHT",
            "Top right corner",
            "BOTTOM_LEFT",
            "Bottom left corner",
            "MIDDLE_LEFT",
            "Middle of the left side",
            "TOP_LEFT",
            "Top left corner");
        set("seconds", "Time on screen", "How many seconds a line stays.");
        set("maxLines", "Most lines", "The most lines at once; the oldest go first.");
        tab("inventory", "Inventory", "Little touches for the slots of every inventory.");
        set(
            "hoverBounce",
            "Bounce on hover",
            "An item squeezes and springs back when the mouse comes onto it.",
            Restart.ALWAYS);
        set(
            "newItemStars",
            "Mark new items",
            "A little star on the items new in your inventory, until the mouse comes onto them.");
        tab("chat", "Chat", "Player heads and a cleaner look of the chat.");
        set("playerHeads", "Player heads", "Show the head of the player before their messages.", Restart.ALWAYS);
        set(
            "hideNames",
            "Only the head",
            "With player heads: hide the name, rank and tags before the message, only the head stays.");
        set(
            "systemMessageBar",
            "Bar before server messages",
            "A bar before the messages not written by a player, as in newer versions.",
            Restart.ALWAYS);
        tab("player", "Player", "How your character moves.");
        set(
            "dropSwing",
            "Swing when dropping",
            "Swing the hand when you throw an item, as in newer versions. Others see it too.",
            Restart.ALWAYS);
        tab("screenBackground", "Menu Background", "How the world looks behind the menus opened in game.");
        set("mode", "Style", "Darken the world behind the menus, or blur it.");
        values("DIM", "Darken the world, as vanilla does", "BLUR", "Blur the world, as in newer versions");
        set("darkness", "Darkness (Dim)", "How dark the world gets in the Dim style, in percent. Vanilla is 75.");
        set("blurStrength", "Blur strength (Blur)", "How strong the blur is in the Blur style.");
        set("blurDarkness", "Darkness (Blur)", "How dark the blurred world gets, in percent.");
        tab("resourcePacks", "Resource Packs", "A handier resource pack screen.");
        set(
            "packBrowser",
            "Get packs button",
            "Download, preview and update the packs of the GTNH wiki and of Modrinth.");
        set("dragAndDrop", "Drag and drop", "Move the packs with the mouse, between the lists and inside them.");
        set(
            "presets",
            "Presets",
            "Keep up to 10 sets of packs and switch between them in one click, or with the keys 1 to 0.");
        set(
            "applyPresetAtOnce",
            "Apply presets at once",
            "Choosing a preset applies it right away. Off: it only fills the lists, until you press Done.");
        set("hideDefaultPack", "Hide the Default pack", "Hide the Default entry while other packs are selected.");
        set(
            "skipReloadWithoutChanges",
            "No reload without changes",
            "Done closes the screen at once when nothing changed, without reloading all the resources.");

        module(
            "minecraftServer",
            "Crouch & Swim",
            "Lets the players of this world crouch and swim as in newer versions.");
        tab(
            "movement",
            "Crouch & Swim",
            "Only for players whose game has GTNHTweaks with Crouch & Swim on. They get the changes when they join "
                + "again.");
        set("crouch", "Modern crouch", "Sneaking crouches lower: players fit under slabs 1.5 blocks above the ground.");
        set(
            "swim",
            "Swim and crawl",
            "Sprinting under water swims through gaps one block high; where there is no room to stand, players "
                + "crawl.");

        module("itemphysics", "Dropped Items", "Dropped items lie on the ground for real. Only the look changes.");
        tab("items", "Items", "How dropped items fly and lie.");
        set("tumbleInAir", "Tumble in the air", "Items spin while they fly and land on a side. Off: they fall flat.");
        set("tumbleSpeed", "Tumble speed", "How fast they spin; 1 is normal.");
        set("piles", "Stacks as piles", "A stack lies as a little pile of items or blocks.");
        set(
            "keepOutOfBlocks",
            "Keep out of walls",
            "Move the items next to a wall away from it, so they do not sink into blocks.");

        // ---- mods

        module("gregtech", "GregTech", "Visual tweaks of GregTech.");
        tab("cables", "Cables", "The look of the cables of GregTech.");
        set(
            "hideCableInsulation",
            "Cables without insulation",
            "Placed insulated cables look like bare wires. Cables as items keep it.");
        tab("toolOverlay", "Tool Overlay", "The grid on blocks while you hold a tool.");
        set(
            "modern",
            "Modern tool overlay",
            "The pulsing grid of GregTech CEu Modern, with an icon of what the tool does to each side. Off: the grid "
                + "of GregTech 5.",
            Restart.TURN_ON);

        module("nei", "NEI", "Smoother crafting tree and a recipe shortcut for NotEnoughItems.");
        tab("craftingTree", "Crafting Tree", "Moving around the crafting tree of the bookmarks.");
        set(
            "preciseDrag",
            "Precise dragging",
            "The tree follows the mouse exactly, without the steps of NEI that feel like lag.",
            Restart.TURN_ON);
        set("smoothScroll", "Smooth scrolling", "Animate scrolling and zooming instead of jumping.", Restart.TURN_ON);
        set("scrollTime", "Scroll animation time", "How long a scroll or a zoom takes, in milliseconds.");
        tab("recipes", "Recipes", "Shortcuts of the recipe screens.");
        set(
            "fillOneCraftWithCtrl",
            "Ctrl + Shift fills one craft",
            "Ctrl + Shift + click on the + of a recipe puts the items of one craft in the grid. Shift + click still "
                + "fills as many as it can.",
            Restart.ALWAYS);

        module("chromatictooltips", "Chromatic Tooltips", "Fixes of Chromatic Tooltips and Chromatic Spectrum.");
        tab("fixes", "Fixes", "Fixes of how the tooltips are drawn.");
        set(
            "colorTextTooltips",
            "Color text-only tooltips",
            "Also color the tooltips that have only text, like the fluids of GregTech tanks and BetterQuesting "
                + "quests.",
            Restart.ALWAYS);
        set(
            "fitTextWidth",
            "Fit the frame to the text",
            "Widen the tooltip frame, so text like the quest tooltips of BetterQuesting does not run past it.",
            Restart.ALWAYS);

        module("tinkers", "Tinkers' Construct", "Tweaks of Tinkers' Construct.");
        tab("craftingStation", "Crafting Station", "Shortcuts of the Crafting Station.");
        set(
            "clearGridToSideInventory",
            "Ctrl + Shift clears into the chest",
            "Ctrl + Shift + click on the clear button of Crafting Tweaks moves the grid into the chest next to the "
                + "station.",
            Restart.ALWAYS);

        module("extrautilities", "Extra Utilities", "The Healing Axe as a little living companion.");
        tab(
            "healingAxe",
            "Healing Axe",
            "The Healing Axe is drawn as a little companion that blinks and smiles at you. Where it is off, the axe "
                + "looks as usual.");
        set("model", "Companion", "Which companion the axe is.");
        values(
            "PLANT",
            "A plant in a clay pot, swaying, its leaves falling and growing again",
            "AQUARIUM",
            "A glass tank with a goldfish, seaweed and bubbles",
            "SLIME",
            "A slime in a jar, hopping and bumping the lid",
            "LANTERN",
            "A lantern with a fire spirit, hanging from the hand",
            "PEASHOOTER",
            "A peashooter in a clay pot, shooting a pea now and then",
            "SUNFLOWER",
            "A sunflower in a clay pot, making a sun now and then");
        set("inHand", "In hand", "In your hands, in first and third person, the off hand of Backhand too.");
        set("holdingArm", "Held by the arm", "In first person your arm holds it, instead of it floating in the air.");
        set("moods", "Moods", "It sleeps at night and feels the rain, the heat and the Nether.");
        set("inInventory", "In inventories", "In the inventory slots.");
        set("onGround", "On the ground", "When the axe lies on the ground.");

        module("bees", "Forestry Bees", "Bees drawn as 3D bees with the colors and the character of their species.");
        tab("where", "Where", "Where the bees are drawn in 3D. Elsewhere they look as usual.");
        set("inHand", "In hand", "In your hands, in first and third person.");
        set("inInventory", "In inventories", "In inventory slots, apiaries, other machines and NEI.");
        set("onGround", "On the ground", "When a bee lies on the ground.");
        tab("look", "Look", "The details that give every species its character.");
        set(
            "accessories",
            "Outfits",
            "Every species dresses for what it is: miner helmets, wizard hats, snorkels, gas masks...");
        set("carryProduct", "Carry their product", "A bee carries its comb or its specialty.");
        set(
            "moods",
            "Moods",
            "Bees sleep at night, sweat in the heat, shiver in the cold and hide under an umbrella in the rain.");

        // ---- tools

        module(
            "logs",
            "Logs & Crashes",
            "Clean logs with the errors, warnings and crashes sorted out, and a crash screen in plain words.");
        tab("general", "General", "Where the logs go.");
        set("folder", "Log folder", "The folder of the logs in the game folder.", Restart.ALWAYS);
        set(
            "maxBackups",
            "Old logs to keep",
            "At start the logs of the last launch are zipped. How many zips to keep; 0 keeps none.");
        tab("files", "Files", "Which files are written. Every launch gets files of its own.");
        set("writeAll", "Full log", "Every message, like fml-client-latest.log.", Restart.ALWAYS);
        set("writeErrors", "Errors", "Errors and exceptions, sorted by where they come from.");
        set("writeWarnings", "Warnings", "Warnings, sorted by where they come from.");
        set("writeCrash", "Crash summary", "What crashed, with a short stack trace and the mods in it.");
        set("writeFullCrash", "Full crash report", "The whole crash report.");
        set(
            "writeLoading",
            "Loading times",
            "How long every stage of loading took and the slowest mods, compared with the last launch.");
        set(
            "writeMods",
            "System and mods",
            "Java, Minecraft, Forge, memory, graphics card and the mod list, with what changed since the last launch.");
        tab("crashScreen", "Crash Screen", "What you see after a crash.");
        set(
            "replaceCrashScreen",
            "GTNHTweaks crash screen",
            "After a crash the game stays open and shows the problem in plain words, with buttons to copy the "
                + "report and open the logs.",
            Restart.ALWAYS);
        tab("details", "Details", "What goes into the Errors, Warnings and Crash files.");
        set(
            "maxStackFrames",
            "Stack lines per error",
            "Lines of the stack trace kept per error in Errors and Warnings.");
        set("maxCrashStackFrames", "Stack lines of a crash", "Lines of the stack trace kept per error in Crash.");
        set(
            "stderrAsErrors",
            "System.err counts as errors",
            "Text printed to System.err, mostly by printStackTrace, goes to Errors.");
        set(
            "ignore",
            "Ignored messages",
            "Regular expressions of messages left out of Errors and Warnings, like \"Angelica: Frame pacing.*\".");

        module("backups", "Backup Toast", "A toast with the progress of the backups the server makes.");
        tab("general", "General", "The look and behavior of the toast.");
        theme();
        set("playSound", "Sound when done", "Play a sound when a backup ends.");
        set(
            "hideChatMessages",
            "Hide chat messages",
            "Hide the backup messages of ServerUtilities in the chat: the toast shows them.");
        set("finishedToastSeconds", "Toast time after a backup", "How many seconds the toast stays after the backup.");
        set(
            "timeoutMinutes",
            "Give up after",
            "Hide the toast when nothing was heard of the backup for this many minutes.");
        tab(
            "detection",
            "Detection",
            "How a backup is told from the chat on servers without GTNHTweaks, by regular expressions.");
        set(
            "detectFromChat",
            "Detect from the chat",
            "Notice backups by the chat messages. The progress is guessed from how long the last backup took.");
        backupPatterns("chat message");
        tab("text", "Texts", "The words of the toast. Latin and Cyrillic letters work.");
        set("title", "Title while running", "The title while the backup runs.");
        set("titleDone", "Title when done", "The title after the backup.");
        set("titleFailed", "Title when failed", "The title after a failed backup.");
        set("elapsed", "Elapsed time label", "The caption of the time since the backup started.");
        set("took", "Duration label", "The caption of how long the backup took.");
        set("left", "Time left word", "The word after the time the backup still needs.");

        module(
            "backupsServer",
            "Backup Tracking",
            "Tells the players with GTNHTweaks the real progress of the backups of this server.");
        tab("general", "General", "How the server follows a backup.");
        set(
            "timeoutMinutes",
            "Give up after",
            "Tell the players the backup is over when nothing was heard of it for this many minutes.");
        tab("detection", "Detection", "How a backup is told from the server log, by regular expressions.");
        set("loggers", "Backup loggers", "Loggers whose lines with a percentage are the progress of a backup.");
        backupPatterns("log line");

        module("debug", "Developer Tools", "Tools for the makers of mods and modpacks.");
        tab("guiInfo", "Screen Info", "What the open screen is.");
        set(
            "enabled",
            "Screen info key",
            "Ctrl + Shift + F12 in any screen tells which mod it comes from, its class and the FPS, and copies it.");
        tab("crashTest", "Crash Test", "Crashes the game on purpose, to test the crash screen.");
        set("mixinCrash", "Mixin crash", "When to crash with a mixin that cannot be applied.", Restart.ALWAYS);
        values(
            "OFF",
            "No test crash",
            "BEFORE_WINDOW",
            "When the game starts, before its window opens",
            "STARTUP",
            "While the mods load, the window already open",
            "IN_GAME",
            "3 seconds after entering a world");
        set(
            "screenCloseCrash",
            "Crash when the outline screen closes",
            "Crash when the block outline screen closes. Turn it off after the test.",
            Restart.NONE);
    }
}
