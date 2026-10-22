package gtnhtweaks.modules.mainmenu;

import net.minecraft.client.gui.GuiMainMenu;

/**
 * The vanilla main menu under its own class, opened in place of the Custom Main Menu screen. DummyCore swaps a menu
 * of exactly the GuiMainMenu class for its own one, which Custom Main Menu swaps again, so a plain GuiMainMenu would
 * be opened over and over.
 */
public class GTNHMainMenu extends GuiMainMenu {
}
