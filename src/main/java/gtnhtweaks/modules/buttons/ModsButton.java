package gtnhtweaks.modules.buttons;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gtnhtweaks.client.settings.OwnModsScreen;
import gtnhtweaks.common.render.PixelIcon;
import gtnhtweaks.common.render.ThemedButton;
import gtnhtweaks.mixins.early.common.AccessorGuiButton;

/**
 * The small square with an arrow right of the row of Options in the main menu, as Language is left of it, which opens
 * the mods of the author of GTNHTweaks: {@link OwnModsScreen}. Shown while {@link ButtonsModule#modsButton} is on; the
 * GTNH main menu places and draws it itself.
 */
public class ModsButton extends GuiButton {

    /** Next to the id of the GTNHTweaks button, far from the ids used by vanilla and other mods. */
    public static final int ID = SettingsButton.ID + 1;

    private static final int OPTIONS = 0;
    private static final int SIZE = 20;
    private static final int GAP = 4;
    private static final String CUSTOM_MAIN_MENU = "lumien.custommainmenu.gui.GuiCustom";

    private ModsButton(int x, int y) {
        super(ID, x, y, SIZE, SIZE, "");
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new Handler());
    }

    /** The vanilla button, or the GTNH one of the Menu Style module, with the arrow over it. */
    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        super.drawButton(mc, mouseX, mouseY);
        if (!visible) return;
        int color = !enabled ? 0xFFA0A0A0 : ThemedButton.isHovered(this, mouseX, mouseY) ? 0xFFFFFFA0 : 0xFFE0E0E0;
        PixelIcon.drawCentered(
            PixelIcon.DOWNLOAD,
            xPosition,
            yPosition,
            ThemedButton.width(this),
            ThemedButton.height(this),
            color);
    }

    public static final class Handler {

        private static boolean isMainMenu(Object gui) {
            return gui instanceof GuiMainMenu || gui != null && CUSTOM_MAIN_MENU.equals(
                gui.getClass()
                    .getName());
        }

        /** Before the GTNH main menu arranges its buttons, which runs last. */
        @SubscribeEvent(priority = EventPriority.LOW)
        public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
            if (!ButtonsModule.modsButton || !isMainMenu(event.gui)) return;
            @SuppressWarnings("unchecked")
            List<GuiButton> buttons = event.buttonList;
            for (GuiButton button : buttons) if (button.id == ID) return;
            // right of the row of Options, as Language is left of it in vanilla; top left when there is no Options
            GuiButton options = options(buttons);
            int x = 4, y = 4;
            if (options != null) {
                int right = options.xPosition + ((AccessorGuiButton) options).gtnhtweaks$getWidth();
                for (GuiButton other : buttons) {
                    if (other.visible && other.yPosition == options.yPosition) {
                        right = Math.max(right, other.xPosition + ((AccessorGuiButton) other).gtnhtweaks$getWidth());
                    }
                }
                x = right + GAP;
                y = options.yPosition;
            }
            buttons.add(new ModsButton(x, y));
        }

        /** The Options button: by its id in the vanilla menu, by its label in Custom Main Menu. */
        private static GuiButton options(List<GuiButton> buttons) {
            for (GuiButton button : buttons) if (button.id == OPTIONS && button.visible) return button;
            String label = I18n.format("menu.options");
            for (GuiButton button : buttons) {
                if (button.visible && label.equals(button.displayString)) return button;
            }
            return null;
        }

        @SubscribeEvent
        public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Pre event) {
            if (!isMainMenu(event.gui) || event.button.id != ID) return;
            Minecraft.getMinecraft()
                .displayGuiScreen(new OwnModsScreen(event.gui));
            event.setCanceled(true);
        }
    }
}
