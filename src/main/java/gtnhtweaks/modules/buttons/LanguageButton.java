package gtnhtweaks.modules.buttons;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiButtonLanguage;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Hides the Language button of the main menu while {@link ButtonsModule#languageButton} is off. The language stays
 * reachable from the Options screen.
 */
public class LanguageButton {

    /** Id of Language in the vanilla main menu, see GuiMainMenu.actionPerformed. */
    private static final int LANGUAGE = 5;

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new LanguageButton());
    }

    /** Before the GTNH main menu arranges its buttons, so the hidden one leaves no room behind. */
    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (ButtonsModule.languageButton) return;
        @SuppressWarnings("unchecked")
        List<GuiButton> buttons = event.buttonList;
        for (GuiButton button : buttons) {
            if (button instanceof GuiButtonLanguage || event.gui instanceof GuiMainMenu && button.id == LANGUAGE) {
                button.visible = false;
                button.enabled = false;
            }
        }
    }
}
