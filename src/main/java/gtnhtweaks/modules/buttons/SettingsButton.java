package gtnhtweaks.modules.buttons;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gtnhtweaks.client.settings.SettingsScreen;
import gtnhtweaks.mixins.early.common.AccessorGuiButton;

/**
 * The GTNHTweaks button beside Mods in the main menu, which opens the settings of GTNHTweaks. It takes a share of the
 * row of Mods; the GTNH main menu places it there itself. Shown while {@link ButtonsModule#settingsButton} is on.
 */
public class SettingsButton {

    /** Far from the ids used by vanilla and other mods. */
    public static final int ID = 0x47544E48;
    public static final String LABEL = "GTNHTweaks";

    private static final int MODS = 6;
    private static final int GAP = 4;
    private static final String CUSTOM_MAIN_MENU = "lumien.custommainmenu.gui.GuiCustom";

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new SettingsButton());
    }

    private static boolean isMainMenu(Object gui) {
        return gui instanceof GuiMainMenu || gui != null && CUSTOM_MAIN_MENU.equals(
            gui.getClass()
                .getName());
    }

    /** Before the GTNH main menu arranges its buttons, which runs last. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!ButtonsModule.settingsButton || !isMainMenu(event.gui)) return;
        @SuppressWarnings("unchecked")
        List<GuiButton> buttons = event.buttonList;
        for (GuiButton button : buttons) if (button.id == ID) return;
        GuiButton button = new GuiButton(ID, 4, 4, 70, 20, LABEL);
        buttons.add(button);
        GuiButton mods = mods(buttons);
        if (mods == null) return;

        // the row of Mods shares its width with the button, the order of its buttons kept
        List<GuiButton> row = new ArrayList<>();
        int left = Integer.MAX_VALUE, right = Integer.MIN_VALUE;
        for (GuiButton other : buttons) {
            if (other == button || !other.visible || other.yPosition != mods.yPosition) continue;
            row.add(other);
            left = Math.min(left, other.xPosition);
            right = Math.max(right, other.xPosition + ((AccessorGuiButton) other).gtnhtweaks$getWidth());
        }
        row.sort((a, b) -> Integer.compare(a.xPosition, b.xPosition));
        row.add(row.indexOf(mods) + 1, button);
        int cell = (right - left - (row.size() - 1) * GAP) / row.size();
        int height = ((AccessorGuiButton) mods).gtnhtweaks$getHeight();
        for (int i = 0; i < row.size(); i++) {
            GuiButton cellButton = row.get(i);
            cellButton.xPosition = left + i * (cell + GAP);
            cellButton.yPosition = mods.yPosition;
            ((AccessorGuiButton) cellButton).gtnhtweaks$setWidth(cell);
            ((AccessorGuiButton) cellButton).gtnhtweaks$setHeight(height);
        }
    }

    /** The Mods button: by its id in the vanilla menu, by its label in Custom Main Menu. */
    private static GuiButton mods(List<GuiButton> buttons) {
        String label = I18n.format("fml.menu.mods");
        for (GuiButton button : buttons) if (button.id == MODS && button.visible) return button;
        for (GuiButton button : buttons) {
            if (button.visible && button.displayString != null && button.displayString.equals(label)) return button;
        }
        return null;
    }

    @SubscribeEvent
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!isMainMenu(event.gui) || event.button.id != ID) return;
        Minecraft.getMinecraft()
            .displayGuiScreen(new SettingsScreen(event.gui));
        event.setCanceled(true);
    }
}
