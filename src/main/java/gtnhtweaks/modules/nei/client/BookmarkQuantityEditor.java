package gtnhtweaks.modules.nei.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import codechicken.nei.BookmarkPanel;
import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.bookmark.BookmarksGridSlot;
import gtnhtweaks.client.settings.UI;
import gtnhtweaks.modules.nei.NEIModule;

/** Modal overlay on the existing container: opening it never closes the server-side inventory. */
public final class BookmarkQuantityEditor {

    private GuiTextField field;
    private BookmarkPanel panel;
    private BookmarkGrid grid;
    private BookmarkItem target;
    private BookmarkItem source;
    private int index;
    private int x, y, width;
    private String error = "";
    private String title;
    private long lastTick;

    public boolean active(GuiContainer gui) {
        if (!NEIModule.bookmarkQuantity || Minecraft.getMinecraft().currentScreen != gui) field = null;
        return field != null;
    }

    public boolean key(GuiContainer gui, char character, int key) {
        if (!active(gui)) {
            if (!NEIModule.bookmarkQuantity || key != Keyboard.KEY_Z
                || !GuiScreen.isCtrlKeyDown()
                || Keyboard.isRepeatEvent()
                || NEIClientConfig.isHidden()
                || !NEIClientConfig.isEnabled()
                || NEIClientConfig.isBookmarkPanelHidden()) return false;
            panel = LayoutManager.bookmarkPanel;
            if (panel == null || panel.inEditingState()) return false;
            Minecraft mc = Minecraft.getMinecraft();
            int mouseX = Mouse.getX() * gui.width / mc.displayWidth;
            int mouseY = gui.height - Mouse.getY() * gui.height / mc.displayHeight - 1;
            if (!panel.contains(mouseX, mouseY)) return false;
            BookmarksGridSlot slot = panel.getSlotMouseOver(mouseX, mouseY);
            if (slot == null) return false;
            grid = panel.getGrid();
            index = slot.itemIndex;
            target = grid.getCalculatedItem(index);
            source = grid.getBookmarkItem(index);
            if (target == null || source == null) return false;
            title = slot.getItemStack()
                .getDisplayName();
            width = Math.min(280, gui.width - 16);
            x = (gui.width - width) / 2;
            y = (gui.height - 116) / 2;
            field = new GuiTextField(Minecraft.getMinecraft().fontRenderer, x + 12, y + 36, width - 24, 18);
            field.setMaxStringLength(160);
            field.setText(BookmarkNumber.format(Long.toString(target.getStackSize())));
            field.setFocused(true);
            field.setCanLoseFocus(false);
            field.setCursorPositionEnd();
            field.setSelectionPos(0);
            error = "";
            return true;
        }
        if (key == Keyboard.KEY_ESCAPE) {
            field = null;
        } else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            apply();
        } else {
            edit(character, key);
        }
        return true;
    }

    private void edit(char character, int key) {
        String before = field.getText();
        int cursor = field.getCursorPosition(), selection = field.getSelectionEnd();
        if (cursor == selection) {
            if (key == Keyboard.KEY_BACK && cursor > 0 && before.charAt(cursor - 1) == ',') {
                field.setCursorPosition(cursor - 1);
            } else if (key == Keyboard.KEY_DELETE && cursor < before.length() && before.charAt(cursor) == ',') {
                field.setCursorPosition(cursor + 1);
            }
        }
        field.textboxKeyTyped(character, key);
        if (before.equals(field.getText())) return;
        try {
            int rawCursor = BookmarkNumber.rawCursor(field.getText(), field.getCursorPosition());
            String formatted = BookmarkNumber.format(field.getText());
            if (formatted.length() > 160) throw new IllegalArgumentException("Quantity is too long");
            field.setText(formatted);
            field.setCursorPosition(BookmarkNumber.formattedCursor(formatted, rawCursor));
            error = "";
        } catch (IllegalArgumentException e) {
            field.setText(before);
            field.setCursorPosition(cursor);
            field.setSelectionPos(selection);
            error = e.getMessage();
        }
    }

    private void apply() {
        try {
            if (panel != LayoutManager.bookmarkPanel || panel.getGrid() != grid
                || grid.getBookmarkItem(index) != source
                || grid.getCalculatedItem(index) == null) {
                throw new IllegalArgumentException("Bookmark changed; close and open this dialog again");
            }
            target = grid.getCalculatedItem(index);
            if (grid.getGroup(target.groupId) == null || grid.getGroup(target.groupId).collapsed) {
                throw new IllegalArgumentException("Expand this group to edit an individual bookmark");
            }
            long amount = BookmarkNumber.amount(field.getText());
            BookmarkAmountChange.apply(grid, index, BookmarkAmountChange.multiplier(target, amount));
            panel.save();
            field = null;
        } catch (IllegalArgumentException e) {
            error = e.getMessage();
        }
    }

    public boolean click(GuiContainer gui, int mouseX, int mouseY, int button) {
        if (!active(gui)) return false;
        if (button == 0 && UI.inside(mouseX, mouseY, x + width - 92, y + 90, 80, 18)) apply();
        else if (button == 0 && UI.inside(mouseX, mouseY, x + 12, y + 90, 80, 18)) field = null;
        else field.mouseClicked(mouseX, mouseY, button);
        return true;
    }

    public void draw(GuiContainer gui, int mouseX, int mouseY) {
        if (!active(gui)) return;
        // The hook runs while NEI's screen-space translation is still active (at z = 200).
        GL11.glPushMatrix();
        GL11.glTranslatef(0F, 0F, 200F);
        UI.rect(0, 0, gui.width, gui.height, 0x88000000);
        UI.panel(x, y, width, 116);
        UI.text("Bookmark quantity", x + 12, y + 9, UI.theme().text);
        UI.text(
            UI.font()
                .trimStringToWidth(title, width - 24),
            x + 12,
            y + 22,
            UI.theme().textDim);
        if (System.currentTimeMillis() - lastTick >= 50) {
            field.updateCursorCounter();
            lastTick = System.currentTimeMillis();
        }
        field.drawTextBox();
        String message = error.isEmpty() ? "Enter: apply    Esc: cancel" : error;
        Minecraft.getMinecraft().fontRenderer
            .drawSplitString(message, x + 12, y + 61, width - 24, error.isEmpty() ? UI.theme().textDim : 0xFFFF7777);
        UI.button(x + 12, y + 90, 80, 18, "Cancel", mouseX, mouseY, true);
        UI.button(x + width - 92, y + 90, 80, 18, "Apply", mouseX, mouseY, true);
        GL11.glPopMatrix();
    }
}
