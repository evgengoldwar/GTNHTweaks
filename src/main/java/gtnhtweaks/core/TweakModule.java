package gtnhtweaks.core;

import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * Base class of every GTNHTweaks module.
 * <p>
 * A module lives in its own package under {@code gtnhtweaks.modules}, can be switched on or off in
 * {@code config/GTNHTweaks/modules.cfg} and keeps its own settings in {@code config/GTNHTweaks/<id>.cfg}.
 * <p>
 * Configs are read during the coremod phase (before any mod is constructed), so {@link #loadConfig} must not touch
 * Minecraft classes. Lifecycle methods are only called for enabled modules.
 */
public abstract class TweakModule {

    private final String id;
    private final String description;
    private final boolean clientOnly;
    private boolean enabled;

    protected TweakModule(String id, String description, boolean clientOnly) {
        this.id = id;
        this.description = description;
        this.clientOnly = clientOnly;
    }

    /** Used as the key in modules.cfg and as the name of the module's own config file. */
    public final String getId() {
        return id;
    }

    public final String getDescription() {
        return description;
    }

    public final boolean isClientOnly() {
        return clientOnly;
    }

    public final boolean isEnabled() {
        ModuleManager.loadConfig();
        return enabled;
    }

    final void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    /** Reads the module's own settings. Called once, even when the module is disabled, so the file is generated. */
    protected abstract void loadConfig(Configuration config);

    public void preInit(FMLPreInitializationEvent event) {}

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}
}
