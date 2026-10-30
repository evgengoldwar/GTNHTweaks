package gtnhtweaks.modules.buttons.mods;

import java.util.Locale;

/**
 * The mods of the author of GTNHTweaks that the mods window can install, update and remove, GTNHTweaks itself first.
 * The descriptions use the color codes of Minecraft: §b for what the mod adds, §6 for other mods, §e for keys.
 */
public enum OwnMod {

    GTNHTWEAKS("gtnhtweaks", "GTNHTweaks", "GTNHTweaks",
        "Tweaks for §6GT New Horizons§r: a §bGTNH-style main menu§r and §bloading screen§r, §bstyled menus§r, "
            + "this §bmod list§r, readable §bcrash screens§r and many small fixes, each module with its own switch."),
    WAYFARERS_MAP("wayfarmap", "Wayfarer's Map", "Wayfarer-s-Map",
        "A fast §bminimap§r and §bworld map§r: an isometric §b3D map§r like Dynmap, §bwaypoints§r, caves, biomes "
            + "and topography, with layers for §6GregTech§r ore veins."),
    LAG_FINDER("lagfinder", "LagFinder", "LagFinder",
        "Finds what §cslows your world down§r: scans the §bserver ticks (TPS)§r or the §bclient frames (FPS)§r and "
            + "shows, per mod, machine and block, what took the time. Press §eL§r to open it."),
    INFO_HUD("infohud", "InfoHUD", "InfoHUD",
        "A configurable §bHUD§r with player and world info, a classic bar renderer and a §blight-level overlay§r."),
    NEI_EXTEND("neiextend", "NEIExtend", "NEIExtend",
        "An §6NEI§r addon: §bhide the recipe tabs§r and §bsingle recipes§r you don't need, from a screen in the "
            + "game."),
    CRAFT_INSPECTOR("craftinspector", "CraftInspector", "CraftInspector", "§7In development.§r"),
    MULTIBLOCK_VISUALIZER("multiblockvisualizer", "MultiblockVisualizer", "MultiblockVisualizer",
        "Hold a key over a §6GregTech§r multiblock controller to watch its §bstructure assemble§r in an animated "
            + "3D viewer."),
    CHEAT_CHECKER("cheatchecker", "CheatChecker", "CheatChecker",
        "Checks the §bmods of EvgenWarGold§r for updates and links to their latest releases.");

    public static final String OWNER = "evgengoldwar";

    public final String modId;
    public final String displayName;
    public final String repo;
    public final String description;
    /** Starts of the file names of the mod, letters and digits only, lower case. */
    private final String[] fileKeys;

    OwnMod(String modId, String displayName, String repo, String description) {
        this.modId = modId;
        this.displayName = displayName;
        this.repo = repo;
        this.description = description;
        fileKeys = new String[] { key(modId), key(displayName), key(repo) };
    }

    public String repoUrl() {
        return "https://github.com/" + OWNER + "/" + repo;
    }

    /** Whether a file in the mods folder is a jar of this mod, enabled or not, by the start of its name. */
    public boolean ownsFile(String fileName) {
        String name = key(fileName);
        for (String fileKey : fileKeys) {
            if (!fileKey.isEmpty() && name.startsWith(fileKey)) return true;
        }
        return false;
    }

    private static String key(String text) {
        return text.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "");
    }
}
