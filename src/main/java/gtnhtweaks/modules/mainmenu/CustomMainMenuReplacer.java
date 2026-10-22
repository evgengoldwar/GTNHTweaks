package gtnhtweaks.modules.mainmenu;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiOpenEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gtnhtweaks.GTNHTweaks;

/**
 * Custom Main Menu, the main menu of the GTNH pack, swaps every opened GuiMainMenu for its own "mainmenu" screen.
 * Runs after it and opens the vanilla main menu again, which the GTNH main menu draws over. The other Custom Main
 * Menu screens are left alone.
 */
public class CustomMainMenuReplacer {

    static final String MOD_ID = "custommainmenu";
    private static final String GUI_CUSTOM = "lumien.custommainmenu.gui.GuiCustom";

    private Field guiConfigField;
    private Field nameField;
    private boolean failed;
    private String loggedReason;
    private long windowStart;
    private int replacedInWindow;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onOpenGui(GuiOpenEvent event) {
        if (failed || !MainMenuModule.replaceCustomMainMenu || !isCustomMainMenu(event.gui)) return;
        String reason = MainMenuRenderer.inactiveReason();
        if (reason != null) {
            if (!reason.equals(loggedReason)) {
                GTNHTweaks.LOG.info("Keeping the main menu of Custom Main Menu: " + reason);
                loggedReason = reason;
            }
            return;
        }
        if (isLooping()) return;
        event.gui = new GTNHMainMenu();
    }

    /** Stops replacing for good when another mod keeps swapping the menu back, instead of overflowing the stack. */
    private boolean isLooping() {
        long now = Minecraft.getSystemTime();
        if (now - windowStart > 1000) {
            windowStart = now;
            replacedInWindow = 0;
        }
        if (++replacedInWindow <= 10) return false;
        failed = true;
        GTNHTweaks.LOG.error(
            "Another mod keeps replacing the main menu, the main menu of Custom Main Menu is kept",
            new Throwable("Stack of the last replacement"));
        return true;
    }

    private boolean isCustomMainMenu(GuiScreen gui) {
        if (gui == null || !GUI_CUSTOM.equals(
            gui.getClass()
                .getName()))
            return false;
        try {
            if (guiConfigField == null) {
                guiConfigField = gui.getClass()
                    .getField("guiConfig");
                nameField = guiConfigField.getType()
                    .getField("name");
            }
            return "mainmenu".equals(nameField.get(guiConfigField.get(gui)));
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            GTNHTweaks.LOG.error("Unknown version of Custom Main Menu, its main menu is kept", e);
            return false;
        }
    }
}
