package gtnhtweaks.modules.minecraft.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;

import gtnhtweaks.modules.minecraft.MinecraftModule;
import gtnhtweaks.modules.minecraft.client.PackPresets.Preset;

/**
 * The 10 presets of resource packs in the empty space left of the lists of the resource pack screen. A row is the
 * button that selects the packs of the preset, then Save, Rename and Clear. The keys 1 to 0 select the presets too.
 */
final class PresetPanel {

    /** Ids of the buttons: BASE + slot * 4 + 0 select, 1 save, 2 rename, 3 clear. */
    private static final int BASE = 0x475400;
    private static final int SMALL = 16;
    private static final int MIN_WIDTH = 110;
    private static final int MAX_WIDTH = 190;
    /** How long a first click on Clear waits for the second one, in ms. */
    private static final long CLEAR_CONFIRM = 3000;

    private static boolean active;
    private static GuiScreen screen;
    private static List<GuiButton> buttons;
    private static int left, width, top, rowHeight;

    private static int renaming = -1;
    private static GuiTextField nameField;
    private static int clearing = -1;
    private static long clearingSince;

    private PresetPanel() {}

    /** Lays the panel out left of the lists, from {@code listLeft}; false if it has no room there. */
    static boolean init(GuiScreen gui, List<GuiButton> list, int listLeft, int listTop, int listBottom) {
        int right = listLeft - 10;
        int panelLeft = Math.max(6, right - MAX_WIDTH);
        active = right - panelLeft >= MIN_WIDTH;
        if (!active) return false;
        if (gui != screen) {
            renaming = -1;
            clearing = -1;
            nameField = null;
        }
        screen = gui;
        buttons = list;
        left = panelLeft;
        width = right - panelLeft;
        top = listTop + 16;
        rowHeight = Math.max(14, Math.min(22, (listBottom - top) / PackPresets.COUNT));
        build();
        return true;
    }

    private static boolean on(GuiScreen gui) {
        return active && gui == screen && MinecraftModule.resourcePackPresets;
    }

    private static int chooseWidth() {
        return width - 3 * (SMALL + 2);
    }

    private static int rowY(int slot) {
        return top + slot * rowHeight;
    }

    /** Puts the buttons of the rows on the screen anew, with the presets as they are now. */
    private static void build() {
        for (Iterator<GuiButton> it = buttons.iterator(); it.hasNext();) {
            int id = it.next().id;
            if (id >= BASE && id < BASE + PackPresets.COUNT * 4) it.remove();
        }
        int h = rowHeight - 2;
        int x = left + chooseWidth() + 2;
        for (int i = 0; i < PackPresets.COUNT; i++) {
            Preset preset = PackPresets.get(i);
            int y = rowY(i);
            if (i != renaming) {
                GuiButton choose = new GuiButton(BASE + i * 4, left, y, chooseWidth(), h, label(i, preset));
                choose.enabled = preset != null;
                buttons.add(choose);
            }
            buttons.add(new GuiButton(BASE + i * 4 + 1, x, y, SMALL, h, "S"));
            GuiButton rename = new GuiButton(BASE + i * 4 + 2, x + SMALL + 2, y, SMALL, h, i == renaming ? "OK" : "R");
            rename.enabled = preset != null;
            buttons.add(rename);
            GuiButton clear = new GuiButton(
                BASE + i * 4 + 3,
                x + 2 * (SMALL + 2),
                y,
                SMALL,
                h,
                i == clearing ? EnumChatFormatting.RED + "?" : "X");
            clear.enabled = preset != null;
            buttons.add(clear);
        }
        if (renaming >= 0 && PackPresets.get(renaming) != null) {
            String text = nameField == null ? PackPresets.get(renaming).name : nameField.getText();
            nameField = new GuiTextField(font(), left + 1, rowY(renaming) + 1, chooseWidth() - 2, rowHeight - 4);
            nameField.setMaxStringLength(40);
            nameField.setText(text);
            nameField.setFocused(true);
        } else {
            renaming = -1;
            nameField = null;
        }
    }

    private static String label(int slot, Preset preset) {
        String number = (slot + 1) % 10 + ". ";
        if (preset == null) return number + EnumChatFormatting.DARK_GRAY + "empty";
        int missing = 0;
        for (String pack : preset.packs) {
            if (!ResourcePackScreen.isInstalled(pack)) missing++;
        }
        String count = missing > 0 ? EnumChatFormatting.RED + " (" + preset.packs.size() + ")"
            : EnumChatFormatting.GRAY + " (" + preset.packs.size() + ")";
        FontRenderer font = font();
        int nameWidth = chooseWidth() - 8 - font.getStringWidth(number + " (" + preset.packs.size() + ")");
        return number + font.trimStringToWidth(preset.name, Math.max(10, nameWidth)) + count;
    }

    /** True if the button is one of the panel. */
    static boolean actionPerformed(GuiScreen gui, GuiButton button) {
        if (!on(gui) || button.id < BASE || button.id >= BASE + PackPresets.COUNT * 4) return false;
        int slot = (button.id - BASE) / 4;
        switch ((button.id - BASE) % 4) {
            case 0:
                choose(slot);
                return true;
            case 1:
                Preset old = PackPresets.get(slot);
                String name = old == null ? "Preset " + (slot + 1) : old.name;
                PackPresets.set(slot, new Preset(name, ResourcePackScreen.selectedPacks()));
                break;
            case 2:
                if (renaming == slot) finishRename();
                else {
                    if (renaming >= 0) finishRename();
                    renaming = slot;
                    nameField = null;
                }
                break;
            default:
                if (clearing == slot && Minecraft.getSystemTime() - clearingSince < CLEAR_CONFIRM) {
                    PackPresets.set(slot, null);
                    if (renaming == slot) renaming = -1;
                    clearing = -1;
                } else {
                    clearing = slot;
                    clearingSince = Minecraft.getSystemTime();
                }
                break;
        }
        build();
        return true;
    }

    private static void choose(int slot) {
        Preset preset = PackPresets.get(slot);
        if (preset == null) return;
        ResourcePackScreen.arrange(preset.packs);
        if (MinecraftModule.applyPresetAtOnce && screen instanceof PackScreenHooks) {
            ((PackScreenHooks) screen).gtnhtweaks$applyPacks();
        }
    }

    private static void finishRename() {
        Preset preset = renaming < 0 ? null : PackPresets.get(renaming);
        if (preset != null && nameField != null) {
            String name = nameField.getText()
                .trim();
            if (!name.isEmpty()) {
                preset.name = name;
                PackPresets.set(renaming, preset);
            }
        }
        renaming = -1;
        nameField = null;
    }

    /** True if the key is taken: by the name being edited, or 1 to 0 to select a preset. */
    static boolean keyTyped(GuiScreen gui, char c, int code) {
        if (!on(gui)) return false;
        if (nameField != null) {
            if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) {
                finishRename();
                build();
            } else if (code == Keyboard.KEY_ESCAPE) {
                renaming = -1;
                nameField = null;
                build();
            } else {
                nameField.textboxKeyTyped(c, code);
            }
            return true;
        }
        if (code >= Keyboard.KEY_1 && code <= Keyboard.KEY_0) {
            choose(code - Keyboard.KEY_1);
            return true;
        }
        return false;
    }

    static void mouseClicked(GuiScreen gui, int mouseX, int mouseY, int button) {
        if (on(gui) && nameField != null) nameField.mouseClicked(mouseX, mouseY, button);
    }

    static void update(GuiScreen gui) {
        if (!on(gui)) return;
        if (nameField != null) nameField.updateCursorCounter();
        if (clearing >= 0 && Minecraft.getSystemTime() - clearingSince >= CLEAR_CONFIRM) {
            clearing = -1;
            build();
        }
    }

    /** Draws the title and the name being edited; gives the tooltip of the hovered button, or null. */
    static List<String> draw(GuiScreen gui, int mouseX, int mouseY) {
        if (!on(gui)) return null;
        FontRenderer font = font();
        String title = EnumChatFormatting.UNDERLINE + "" + EnumChatFormatting.BOLD + "Presets";
        font.drawStringWithShadow(title, left + (width - font.getStringWidth(title)) / 2, top - 13, 0xFFFFFF);
        if (nameField != null) nameField.drawTextBox();

        if (mouseX < left || mouseX >= left + width || mouseY < top) return null;
        int slot = (mouseY - top) / rowHeight;
        if (slot >= PackPresets.COUNT || mouseY >= rowY(slot) + rowHeight - 2 || slot == renaming) return null;
        Preset preset = PackPresets.get(slot);
        int column = mouseX < left + chooseWidth() ? 0 : Math.min(3, 1 + (mouseX - left - chooseWidth()) / (SMALL + 2));
        List<String> lines = new ArrayList<>();
        switch (column) {
            case 0:
                if (preset == null) {
                    lines.add(EnumChatFormatting.GRAY + "Empty: S saves the selected packs here");
                    break;
                }
                lines.add(preset.name);
                if (preset.packs.isEmpty()) lines.add(EnumChatFormatting.GRAY + "No packs, only the default one");
                for (String pack : preset.packs) {
                    lines.add(
                        ResourcePackScreen.isInstalled(pack) ? EnumChatFormatting.GRAY + pack
                            : EnumChatFormatting.RED + pack + " (missing)");
                }
                lines.add(EnumChatFormatting.DARK_GRAY + "Click or key " + (slot + 1) % 10 + ": select these packs");
                break;
            case 1:
                lines.add("Save the selected packs here, in their order");
                break;
            case 2:
                lines.add("Rename");
                break;
            default:
                lines.add("Clear: click twice");
                break;
        }
        return lines;
    }

    private static FontRenderer font() {
        return Minecraft.getMinecraft().fontRenderer;
    }
}
