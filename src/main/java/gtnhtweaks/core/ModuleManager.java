package gtnhtweaks.core;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.modules.backups.BackupsModule;
import gtnhtweaks.modules.debug.DebugModule;
import gtnhtweaks.modules.gregtech.GregTechModule;
import gtnhtweaks.modules.loadingscreen.LoadingScreenModule;
import gtnhtweaks.modules.logs.LogsModule;
import gtnhtweaks.modules.mainmenu.MainMenuModule;
import gtnhtweaks.modules.menus.MenusModule;

public final class ModuleManager {

    private static final String CATEGORY_MODULES = "modules";

    /** Every module of the mod. Register new modules here. */
    private static final List<TweakModule> MODULES = Collections
        .unmodifiableList(
            Arrays.<TweakModule>asList(
                LoadingScreenModule.INSTANCE,
                MainMenuModule.INSTANCE,
                MenusModule.INSTANCE,
                GregTechModule.INSTANCE,
                LogsModule.INSTANCE,
                BackupsModule.INSTANCE,
                DebugModule.INSTANCE));

    private static boolean configLoaded;
    private static Configuration modulesConfig;
    private static final Map<TweakModule, Configuration> CONFIGS = new IdentityHashMap<>();

    private ModuleManager() {}

    public static List<TweakModule> getModules() {
        return MODULES;
    }

    public static File getConfigDir() {
        File home = Launch.minecraftHome != null ? Launch.minecraftHome : new File(".");
        return new File(new File(home, "config"), "GTNHTweaks");
    }

    /**
     * Reads modules.cfg and the config of every module. Safe to call any number of times: it is first called from the
     * early mixin loader, long before mod pre-initialization.
     */
    public static synchronized void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;

        File dir = getConfigDir();
        Configuration modules = new Configuration(new File(dir, "modules.cfg"));
        modulesConfig = modules;
        modules.addCustomCategoryComment(
            CATEGORY_MODULES,
            "Enable or disable GTNHTweaks modules. Every module has its own settings file next to this one.\n"
                + "Changes require a game restart.");

        boolean client = FMLLaunchHandler.side()
            .isClient();
        for (TweakModule module : MODULES) {
            Property property = modules
                .get(CATEGORY_MODULES, module.getId(), module.isEnabledByDefault(), module.getDescription());
            property.setRequiresMcRestart(true);
            boolean enabled = property.getBoolean();
            module.setEnabled(enabled && (client || !module.isClientOnly()));

            Configuration config = new Configuration(new File(dir, module.getId() + ".cfg"));
            CONFIGS.put(module, config);
            try {
                module.loadConfig(config);
            } catch (RuntimeException e) {
                GTNHTweaks.LOG.error("Failed to read the config of module " + module.getId(), e);
            }
            if (config.hasChanged()) config.save();
        }

        if (modules.hasChanged()) modules.save();
    }

    /** modules.cfg, which switches the modules on and off. */
    public static Configuration getModulesConfig() {
        loadConfig();
        return modulesConfig;
    }

    /** The settings file of a module. */
    public static Configuration getConfig(TweakModule module) {
        loadConfig();
        return CONFIGS.get(module);
    }

    /**
     * Saves the configs after they were edited in the config screen and reads the module settings again. Turning a
     * module on or off still needs a restart.
     */
    public static synchronized void saveAndReload() {
        if (modulesConfig.hasChanged()) modulesConfig.save();
        for (TweakModule module : MODULES) {
            Configuration config = CONFIGS.get(module);
            try {
                module.loadConfig(config);
            } catch (RuntimeException e) {
                GTNHTweaks.LOG.error("Failed to read the config of module " + module.getId(), e);
            }
            if (config.hasChanged()) config.save();
        }
    }

    /** Called once by the early mixin loader, before mods are constructed. */
    public static void coremodInit() {
        loadConfig();
        for (TweakModule module : MODULES) {
            if (module.isEnabled()) {
                try {
                    module.coremodInit();
                } catch (RuntimeException e) {
                    GTNHTweaks.LOG.error("Failed to start module " + module.getId(), e);
                }
            }
        }
    }

    public static void preInit(FMLPreInitializationEvent event) {
        loadConfig();
        for (TweakModule module : MODULES) {
            if (module.isEnabled()) {
                GTNHTweaks.LOG.info("Module {} is enabled", module.getId());
                module.preInit(event);
            }
        }
    }

    public static void init(FMLInitializationEvent event) {
        for (TweakModule module : MODULES) {
            if (module.isEnabled()) module.init(event);
        }
    }

    public static void postInit(FMLPostInitializationEvent event) {
        for (TweakModule module : MODULES) {
            if (module.isEnabled()) module.postInit(event);
        }
    }
}
