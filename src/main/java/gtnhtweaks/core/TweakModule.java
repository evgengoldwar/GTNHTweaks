package gtnhtweaks.core;

import java.util.Locale;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import gtnhtweaks.common.render.GTNHTheme;

/**
 * Base class of every GTNHTweaks module.
 * <p>
 * A module lives in its own package under {@code gtnhtweaks.modules}, can be switched on or off in
 * {@code config/GTNHTweaks/modules.cfg} and keeps its own settings in {@code config/GTNHTweaks/<id>.cfg}.
 * <p>
 * Every module is either a client or a server one: a client module draws and plays on the game client, a server
 * module runs where the world runs (the dedicated server, or the singleplayer world in the client). A feature that
 * needs both sides is two modules, one per side, each with its own switch and settings. A few modules do the same on
 * either side and are shown in both lists.
 * <p>
 * Configs are read during the coremod phase (before any mod is constructed), so {@link #loadConfig} must not touch
 * Minecraft classes. Lifecycle methods are only called for enabled modules.
 */
public abstract class TweakModule {

    private final String id;
    private final String description;
    private final ModuleSide side;
    private boolean enabled;

    /** Where a module runs. */
    public enum ModuleSide {

        /** Only on the game client. */
        CLIENT,
        /** Where the world runs: the dedicated server and the singleplayer world. */
        SERVER,
        /** The same on the client and on a dedicated server. */
        BOTH;

        public boolean onClient() {
            return this != SERVER;
        }

        public boolean onServer() {
            return this != CLIENT;
        }
    }

    protected TweakModule(String id, String description, ModuleSide side) {
        this.id = id;
        this.description = description;
        this.side = side;
    }

    /** Used as the key in modules.cfg and as the name of the module's own config file. */
    public final String getId() {
        return id;
    }

    public final String getDescription() {
        return description;
    }

    public final ModuleSide getSide() {
        return side;
    }

    public final boolean isEnabled() {
        ModuleManager.loadConfig();
        return enabled;
    }

    final void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Every module starts off: it is turned on in the settings screen or in modules.cfg. */
    public boolean isEnabledByDefault() {
        return false;
    }

    /**
     * Reads the color theme of the module from the "theme" key, SHADOW by default. The old "style" key is dropped, so
     * configs written before the SHADOW theme existed switch to it.
     */
    protected static GTNHTheme readTheme(Configuration config, String category, String what) {
        removeKey(config, category, "style");
        return GTNHTheme.parse(
            config.getString(
                "theme",
                category,
                GTNHTheme.SHADOW.name(),
                "Color theme of " + what
                    + ": SHADOW (the gray beveled look of the Shadow UI resource pack), DARK or LIGHT",
                GTNHTheme.names()));
    }

    /** Removes a key that is no longer read and gives its value, or null if it was not there. */
    protected static String removeOldKey(Configuration config, String category, String key) {
        category = category.toLowerCase(Locale.ENGLISH);
        if (!config.hasCategory(category)) return null;
        ConfigCategory entries = config.getCategory(category);
        if (!entries.containsKey(key)) return null;
        String value = entries.get(key)
            .getString();
        entries.remove(key);
        return value;
    }

    /** Removes a key that is no longer read, so it does not linger in the config file. */
    protected static void removeKey(Configuration config, String category, String key) {
        // Forge keeps the category names lower case
        category = category.toLowerCase(Locale.ENGLISH);
        if (!config.hasCategory(category)) return;
        ConfigCategory entries = config.getCategory(category);
        if (entries.containsKey(key)) entries.remove(key);
    }

    /**
     * Whether a setting is shown in the settings screen. Settings with a screen of their own in game can be hidden
     * there; they stay in the config file.
     *
     * @param category the category name, lower case as Forge keeps it
     */
    public boolean showInSettings(String category, String key) {
        return true;
    }

    /**
     * Label of a button shown at the top of a category in the settings screen, to open a screen of its own for it, or
     * null for none.
     *
     * @param category the category name, lower case as Forge keeps it
     */
    public String settingsButton(String category) {
        return null;
    }

    /**
     * Opens what the button of {@link #settingsButton} is for. Only called on the client.
     *
     * @param parent the settings screen, to come back to
     */
    public void openSettingsButton(String category, Object parent) {}

    /** Reads the module's own settings. Called once, even when the module is disabled, so the file is generated. */
    protected abstract void loadConfig(Configuration config);

    /**
     * Called during the coremod phase, right after the configs are read and before any mod is constructed. Like
     * {@link #loadConfig}, it must not touch Minecraft classes.
     */
    public void coremodInit() {}

    public void preInit(FMLPreInitializationEvent event) {}

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}
}
