package gtnhtweaks.modules.mainmenu;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiScreenEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gtnhtweaks.client.settings.SettingsScreen;
import gtnhtweaks.mixins.early.common.AccessorGuiButton;

/**
 * Arranges the vanilla main menu buttons into a compact column under the logo. Runs last, after other mods added
 * their buttons; buttons it does not know are left where their mod put them.
 */
public class MainMenuLayout {

    static final int BUTTON_WIDTH = 200;
    static final int BUTTON_HEIGHT = 20;
    static final int ROW_STEP = 24;
    static final int GAP = 4;

    /** Vanilla and Forge button ids, see GuiMainMenu.actionPerformed. */
    static final int OPTIONS = 0, SINGLEPLAYER = 1, MULTIPLAYER = 2, QUIT = 4, LANGUAGE = 5, MODS = 6, DEMO_PLAY = 11,
        DEMO_RESET = 12, REALMS = 14;

    /** Id of the GTNHTweaks settings button beside Mods, far from the ids used by vanilla and other mods. */
    static final int SETTINGS = 0x47544E48;

    /** Y of the first button row, read by the renderer to place the logo above it. */
    static volatile int buttonsTop = -1;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiMainMenu) || MainMenuRenderer.replacedByPack()) return;
        @SuppressWarnings("unchecked")
        List<GuiButton> buttons = event.buttonList;

        List<List<GuiButton>> rows = new ArrayList<>();
        addRow(rows, find(buttons, SINGLEPLAYER), find(buttons, DEMO_PLAY));
        addRow(rows, find(buttons, MULTIPLAYER), find(buttons, DEMO_RESET));
        // the GTNHTweaks settings share the row of Mods, as Language shares the one of Options
        GuiButton settings = MainMenuModule.showSettingsButton ? settingsButton() : null;
        if (settings != null) buttons.add(settings);
        addRow(rows, find(buttons, MODS), settings, find(buttons, REALMS));
        addRow(rows, find(buttons, OPTIONS), find(buttons, LANGUAGE));
        addRow(rows, find(buttons, QUIT));

        int width = event.gui.width;
        int height = event.gui.height;
        int total = rows.size() * ROW_STEP - GAP;
        // leave room for the footer line
        int top = Math.max(0, Math.min(height / 4 + 48, height - 24 - total));
        buttonsTop = top;

        for (int row = 0; row < rows.size(); row++) {
            List<GuiButton> cells = rows.get(row);
            int cellWidth = (BUTTON_WIDTH - (cells.size() - 1) * GAP) / cells.size();
            for (int i = 0; i < cells.size(); i++) {
                GuiButton button = cells.get(i);
                button.xPosition = width / 2 - BUTTON_WIDTH / 2 + i * (cellWidth + GAP);
                button.yPosition = top + row * ROW_STEP;
                ((AccessorGuiButton) button).gtnhtweaks$setWidth(cellWidth);
                ((AccessorGuiButton) button).gtnhtweaks$setHeight(BUTTON_HEIGHT);
            }
        }
    }

    private static GuiButton settingsButton() {
        return new GuiButton(SETTINGS, 0, 0, MainMenuModule.textSettings);
    }

    @SubscribeEvent
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.gui instanceof GuiMainMenu) || event.button.id != SETTINGS) return;
        Minecraft.getMinecraft()
            .displayGuiScreen(new SettingsScreen(event.gui));
        event.setCanceled(true);
    }

    private static void addRow(List<List<GuiButton>> rows, GuiButton... buttons) {
        List<GuiButton> row = new ArrayList<>(buttons.length);
        for (GuiButton button : buttons) {
            if (button != null && button.visible) row.add(button);
        }
        if (!row.isEmpty()) rows.add(row);
    }

    private static GuiButton find(List<GuiButton> buttons, int id) {
        for (GuiButton button : buttons) {
            if (button.id == id) return button;
        }
        return null;
    }

    static boolean isKnown(int id) {
        switch (id) {
            case OPTIONS:
            case SINGLEPLAYER:
            case MULTIPLAYER:
            case QUIT:
            case LANGUAGE:
            case MODS:
            case DEMO_PLAY:
            case DEMO_RESET:
            case REALMS:
            case SETTINGS:
                return true;
            default:
                return false;
        }
    }
}
