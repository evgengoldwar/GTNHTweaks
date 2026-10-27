package gtnhtweaks.modules.minecraft.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;

import gtnhtweaks.modules.minecraft.MinecraftModule;
import gtnhtweaks.modules.minecraft.client.PackPresets.Preset;

/**
 * The 10 presets of resource packs, opened by the Presets button of the resource pack screen. A preset is chosen by
 * its button or by its number key; Save keeps the packs selected on the resource pack screen in it.
 */
public class PackPresetsScreen extends GuiScreen {

    private static final int BACK = 100;
    private static final int WIDTH = 326;
    /** How long a first click on Clear waits for the second one, in ms. */
    private static final long CLEAR_CONFIRM = 3000;

    private final GuiScreen parent;
    private int top, left, rowHeight;
    private int renaming = -1;
    private GuiTextField nameField;
    private int clearing = -1;
    private long clearingSince;

    public PackPresetsScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        rowHeight = Math.max(14, Math.min(22, (height - 80) / PackPresets.COUNT));
        int buttonHeight = rowHeight - 2;
        top = 30;
        left = (width - WIDTH) / 2;
        for (int i = 0; i < PackPresets.COUNT; i++) {
            Preset preset = PackPresets.get(i);
            int y = top + i * rowHeight;
            if (i != renaming) {
                GuiButton choose = new GuiButton(i * 4, left, y, 180, buttonHeight, label(i, preset));
                choose.enabled = preset != null;
                buttonList.add(choose);
            }
            buttonList.add(new GuiButton(i * 4 + 1, left + 184, y, 46, buttonHeight, "Save"));
            GuiButton rename = new GuiButton(
                i * 4 + 2,
                left + 234,
                y,
                50,
                buttonHeight,
                i == renaming ? "OK" : "Rename");
            rename.enabled = preset != null;
            buttonList.add(rename);
            GuiButton clear = new GuiButton(
                i * 4 + 3,
                left + 288,
                y,
                38,
                buttonHeight,
                i == clearing ? EnumChatFormatting.RED + "Sure?" : "Clear");
            clear.enabled = preset != null;
            buttonList.add(clear);
        }
        if (renaming >= 0) {
            String text = nameField == null ? PackPresets.get(renaming).name : nameField.getText();
            nameField = new GuiTextField(fontRendererObj, left + 1, top + renaming * rowHeight + 1, 178, rowHeight - 4);
            nameField.setMaxStringLength(40);
            nameField.setText(text);
            nameField.setFocused(true);
        } else {
            nameField = null;
        }
        buttonList.add(new GuiButton(BACK, width / 2 - 100, top + PackPresets.COUNT * rowHeight + 18, 200, 20, "Back"));
    }

    private String label(int slot, Preset preset) {
        String number = (slot + 1) + ". ";
        if (preset == null) return number + EnumChatFormatting.DARK_GRAY + "empty";
        int missing = 0;
        for (String pack : preset.packs) {
            if (!ResourcePackScreen.isInstalled(pack)) missing++;
        }
        String count = " (" + preset.packs.size() + ")";
        if (missing > 0) count += EnumChatFormatting.RED + " " + missing + " missing";
        int nameWidth = 170 - fontRendererObj.getStringWidth(number + count);
        String name = fontRendererObj.trimStringToWidth(preset.name, nameWidth);
        return number + name + EnumChatFormatting.GRAY + count;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BACK) {
            close();
            return;
        }
        int slot = button.id / 4;
        if (slot < 0 || slot >= PackPresets.COUNT) return;
        switch (button.id % 4) {
            case 0:
                choose(slot);
                return;
            case 1:
                Preset old = PackPresets.get(slot);
                PackPresets.set(
                    slot,
                    new Preset(old == null ? "Preset " + (slot + 1) : old.name, ResourcePackScreen.currentPacks()));
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
        initGui();
    }

    private void choose(int slot) {
        Preset preset = PackPresets.get(slot);
        if (preset == null) return;
        ResourcePackScreen.choose(preset.packs);
        mc.displayGuiScreen(parent);
        if (MinecraftModule.applyPresetAtOnce && parent instanceof PackScreenHooks) {
            ((PackScreenHooks) parent).gtnhtweaks$applyPacks();
        }
    }

    private void finishRename() {
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

    private void close() {
        mc.displayGuiScreen(parent);
    }

    @Override
    protected void keyTyped(char c, int code) {
        if (nameField != null) {
            if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) {
                finishRename();
                initGui();
            } else if (code == Keyboard.KEY_ESCAPE) {
                renaming = -1;
                nameField = null;
                initGui();
            } else {
                nameField.textboxKeyTyped(c, code);
            }
            return;
        }
        if (code == Keyboard.KEY_ESCAPE) {
            close();
            return;
        }
        // 1 to 9 and 0 choose the presets 1 to 10
        if (code >= Keyboard.KEY_1 && code <= Keyboard.KEY_0) choose(code - Keyboard.KEY_1);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (nameField != null) nameField.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void updateScreen() {
        if (nameField != null) nameField.updateCursorCounter();
        if (clearing >= 0 && Minecraft.getSystemTime() - clearingSince >= CLEAR_CONFIRM) {
            clearing = -1;
            initGui();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Resource pack presets", width / 2, 12, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (nameField != null) nameField.drawTextBox();
        drawCenteredString(
            fontRendererObj,
            "Save keeps the selected packs in their order. Keys 1-0 choose a preset",
            width / 2,
            top + PackPresets.COUNT * rowHeight + 4,
            0x808080);

        // the packs of the hovered preset
        for (int i = 0; i < PackPresets.COUNT; i++) {
            int y = top + i * rowHeight;
            if (i == renaming || mouseX < left || mouseX >= left + 180 || mouseY < y || mouseY >= y + rowHeight - 2) {
                continue;
            }
            Preset preset = PackPresets.get(i);
            if (preset == null) break;
            List<String> lines = new ArrayList<>();
            lines.add(preset.name);
            if (preset.packs.isEmpty()) lines.add(EnumChatFormatting.GRAY + "No packs, only the default one");
            for (String pack : preset.packs) {
                lines.add(
                    ResourcePackScreen.isInstalled(pack) ? EnumChatFormatting.GRAY + pack
                        : EnumChatFormatting.RED + pack + " (missing)");
            }
            drawHoveringText(lines, mouseX, mouseY, fontRendererObj);
            break;
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }
}
