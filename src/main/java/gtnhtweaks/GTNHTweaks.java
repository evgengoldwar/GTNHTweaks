package gtnhtweaks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import gtnhtweaks.client.SettingsButton;
import gtnhtweaks.client.SettingsKey;
import gtnhtweaks.common.Network;
import gtnhtweaks.core.ModuleManager;

@Mod(
    modid = GTNHTweaks.MODID,
    version = Tags.VERSION,
    name = "GTNHTweaks",
    acceptedMinecraftVersions = "[1.7.10]",
    guiFactory = "gtnhtweaks.client.GuiFactory")
public class GTNHTweaks {

    public static final String MODID = "gtnhtweaks";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Network.register();
        ModuleManager.preInit(event);
        if (event.getSide()
            .isClient()) {
            SettingsKey.register();
            SettingsButton.register();
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ModuleManager.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        ModuleManager.postInit(event);
    }
}
