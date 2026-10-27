package gtnhtweaks.modules.menus;

import java.lang.reflect.Field;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiOpenEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.client.settings.ModListScreen;

/**
 * Opens the mod list of GTNHTweaks wherever the one of FML would open: the Mods button of the main menu, Mod Options of
 * the pause menu, and any mod opening it. Screens of mods that extend the FML list are left alone.
 */
public final class ModListReplacer {

    private static final String FML_MOD_LIST = "cpw.mods.fml.client.GuiModList";

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (!MenusModule.modList || event.gui == null
            || !FML_MOD_LIST.equals(
                event.gui.getClass()
                    .getName()))
            return;
        event.gui = new ModListScreen(parentOf(event.gui));
    }

    /** The screen the FML list was opened from, to go back to: its only field of the type of a screen. */
    private static GuiScreen parentOf(GuiScreen modList) {
        try {
            for (Field field : modList.getClass()
                .getDeclaredFields()) {
                if (field.getType() == GuiScreen.class) {
                    field.setAccessible(true);
                    return (GuiScreen) field.get(modList);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            GTNHTweaks.LOG.warn("Could not find the screen the mod list was opened from", e);
        }
        return null;
    }
}
