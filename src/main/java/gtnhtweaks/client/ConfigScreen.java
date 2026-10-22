package gtnhtweaks.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.client.config.DummyConfigElement.DummyCategoryElement;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.core.ModuleManager;
import gtnhtweaks.core.TweakModule;

/** Settings of every module: one entry for modules.cfg and one per module settings file. */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class ConfigScreen extends GuiConfig {

    public ConfigScreen(GuiScreen parent) {
        super(parent, elements(), GTNHTweaks.MODID, false, false, "GTNHTweaks");
    }

    private static List<IConfigElement> elements() {
        List<IConfigElement> elements = new ArrayList<>();
        elements.add(file("modules", ModuleManager.getModulesConfig()));
        for (TweakModule module : ModuleManager.getModules()) {
            elements.add(file(module.getId(), ModuleManager.getConfig(module)));
        }
        return elements;
    }

    private static IConfigElement file(String name, Configuration config) {
        List<IConfigElement> categories = new ArrayList<>();
        for (String categoryName : config.getCategoryNames()) {
            ConfigCategory category = config.getCategory(categoryName);
            if (!category.isChild()) categories.add(new ConfigElement(category));
        }
        return new DummyCategoryElement(name, name, categories);
    }
}
