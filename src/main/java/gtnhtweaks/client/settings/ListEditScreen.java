package gtnhtweaks.client.settings;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraftforge.common.config.Property;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import gtnhtweaks.common.render.GLDraw;
import gtnhtweaks.common.render.GTNHTheme;

/** Edits a list setting: one text field per entry, with buttons to add and remove entries. */
class ListEditScreen extends GuiScreen {

    private static final int ROW = 20;
    private static final int GAP = 6;

    private final SettingsScreen parent;
    private final Property property;
    private final List<GuiTextField> fields = new ArrayList<>();
    private List<String> values;

    private int windowX, windowY, windowW, windowH;
    private int listY, listH;
    private float scroll, scrollTarget;
    private long lastFrame;

    ListEditScreen(SettingsScreen parent, Property property) {
        this.parent = parent;
        this.property = property;
        this.values = new ArrayList<>();
        for (String value : property.getStringList()) values.add(value);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        if (!fields.isEmpty()) values = texts();
        windowW = Math.min(420, width - 16);
        windowH = Math.min(300, height - 16);
        windowX = (width - windowW) / 2;
        windowY = (height - windowH) / 2;
        listY = windowY + 34;
        listH = windowH - 34 - 30;
        fields.clear();
        for (String value : values) addField(value);
    }

    private void addField(String value) {
        GuiTextField field = new GuiTextField(fontRendererObj, 0, 0, windowW - GAP * 2 - 36, 12);
        field.setEnableBackgroundDrawing(false);
        field.setMaxStringLength(1024);
        field.setText(value);
        field.setCursorPositionZero();
        fields.add(field);
    }

    private List<String> texts() {
        List<String> result = new ArrayList<>();
        for (GuiTextField field : fields) result.add(field.getText());
        return result;
    }

    private int maxScroll() {
        return Math.max(0, (fields.size() + 1) * ROW - listH + 4);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(100, (now - lastFrame) / 1_000_000F);
        lastFrame = now;
        scroll = UI.approach(scroll, scrollTarget, dt, 120);

        GTNHTheme theme = UI.theme();
        if (mc.theWorld != null) drawDefaultBackground();
        else {
            UI.beginShapes();
            GLDraw.verticalGradient(0, 0, width, height, theme.backgroundTop, theme.backgroundBottom);
            UI.endShapes();
        }
        UI.panel(windowX, windowY, windowW, windowH);
        UI.text(UI.humanize(property.getName()), windowX + GAP + 2, windowY + 8, theme.logo);
        UI.text(
            fields.size() + (fields.size() == 1 ? " entry" : " entries") + ", empty entries are dropped",
            windowX + GAP + 2,
            windowY + 20,
            theme.textDim);

        UI.inset(windowX + GAP, listY, windowW - GAP * 2, listH);
        UI.scissor(windowX + GAP + 1, listY + 1, windowW - GAP * 2 - 2, listH - 2);
        int y = listY + 2 - Math.round(scroll);
        for (GuiTextField field : fields) {
            int fx = windowX + GAP + 3;
            UI.inset(fx, y + 2, windowW - GAP * 2 - 28, 16);
            if (field.isFocused()) UI.rect(fx, y + 17, windowW - GAP * 2 - 28, 1, theme.barFill);
            field.xPosition = fx + 4;
            field.yPosition = y + 6;
            field.setTextColor(theme.text & 0xFFFFFF);
            field.drawTextBox();
            UI.button(windowX + windowW - GAP - 22, y + 2, 18, 16, "×", mouseX, mouseY, true);
            y += ROW;
        }
        UI.button(windowX + GAP + 3, y + 2, 80, 16, "+ Add", mouseX, mouseY, true);
        UI.endScissor();

        int by = windowY + windowH - 24;
        UI.button(windowX + windowW - GAP - 80, by, 80, 18, "Done", mouseX, mouseY, true);
        UI.button(windowX + windowW - GAP * 2 - 160, by, 80, 18, "Cancel", mouseX, mouseY, true);
        UI.button(windowX + GAP, by, 80, 18, "Default", mouseX, mouseY, true);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        int by = windowY + windowH - 24;
        if (UI.inside(mouseX, mouseY, windowX + windowW - GAP - 80, by, 80, 18)) {
            UI.playClick();
            save();
            return;
        }
        if (UI.inside(mouseX, mouseY, windowX + windowW - GAP * 2 - 160, by, 80, 18)) {
            UI.playClick();
            mc.displayGuiScreen(parent);
            return;
        }
        if (UI.inside(mouseX, mouseY, windowX + GAP, by, 80, 18)) {
            UI.playClick();
            fields.clear();
            for (String value : property.getDefaults()) addField(value);
            return;
        }

        boolean inList = UI.inside(mouseX, mouseY, windowX + GAP, listY, windowW - GAP * 2, listH);
        int y = listY + 2 - Math.round(scroll);
        for (int i = 0; i < fields.size(); i++) {
            GuiTextField field = fields.get(i);
            if (inList && UI.inside(mouseX, mouseY, windowX + windowW - GAP - 22, y + 2, 18, 16)) {
                UI.playClick();
                fields.remove(i);
                return;
            }
            int fieldW = windowW - GAP * 2 - 28;
            boolean inside = inList && UI.inside(mouseX, mouseY, field.xPosition - 4, y + 2, fieldW, 16);
            field.setFocused(inside);
            if (inside) field.mouseClicked(mouseX, mouseY, button);
            y += ROW;
        }
        if (inList && UI.inside(mouseX, mouseY, windowX + GAP + 3, y + 2, 80, 16)) {
            UI.playClick();
            for (GuiTextField field : fields) field.setFocused(false);
            addField("");
            fields.get(fields.size() - 1)
                .setFocused(true);
            scrollTarget = maxScroll();
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget - Integer.signum(wheel) * 30));
    }

    @Override
    protected void keyTyped(char c, int code) {
        if (code == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        for (GuiTextField field : fields) {
            if (field.isFocused()) {
                if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) field.setFocused(false);
                else field.textboxKeyTyped(c, code);
                return;
            }
        }
    }

    @Override
    public void updateScreen() {
        for (GuiTextField field : fields) field.updateCursorCounter();
    }

    private void save() {
        List<String> result = new ArrayList<>();
        for (String text : texts()) {
            if (!text.trim()
                .isEmpty()) result.add(text.trim());
        }
        parent.beforeChange(property);
        property.set(result.toArray(new String[0]));
        parent.afterChange(property);
        mc.displayGuiScreen(parent);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
