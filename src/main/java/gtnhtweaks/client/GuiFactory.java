package gtnhtweaks.client;

import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import cpw.mods.fml.client.IModGuiFactory;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.client.settings.SettingsScreen;
import gtnhtweaks.core.ModuleManager;

/** Adds the "Config" button of GTNHTweaks in the mod list and applies the settings saved there. */
public class GuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraft) {
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return SettingsScreen.class;
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }

    @Override
    public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
        return null;
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (GTNHTweaks.MODID.equals(event.modID)) ModuleManager.saveAndReload();
    }
}
