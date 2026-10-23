package gtnhtweaks.modules.minecraft.client;

import static org.lwjgl.opengl.GL11.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import cpw.mods.fml.common.Loader;
import gtnhtweaks.client.settings.ColorPicker;
import gtnhtweaks.client.settings.UI;
import gtnhtweaks.common.render.BlockOutline;
import gtnhtweaks.common.render.GLDraw;
import gtnhtweaks.common.render.GTNHTheme;
import gtnhtweaks.modules.debug.DebugModule;
import gtnhtweaks.modules.gregtech.client.GridOverlay;
import gtnhtweaks.modules.gregtech.client.PipePreview;
import gtnhtweaks.modules.minecraft.MinecraftModule;
import gtnhtweaks.modules.minecraft.MinecraftModule.OutlineMode;

/**
 * Settings of the block outline with a live preview: blocks turning around so the outline is seen from every side, and
 * with GregTech a pipe with the tool overlay of a wrench. Color, width, mode and fill change the preview at once and
 * are saved when the screen is closed.
 */
public class OutlineScreen extends GuiScreen {

    private static final int GAP = 6;
    private static final int CONTROLS_WIDTH = 170;
    private static final int[] PRESETS = { MinecraftModule.VANILLA_COLOR, 0xFFFFFFFF, 0xFFE5534B, 0xFFF2A33A,
        0xFFFFD866, 0xFF4CC264, 0xFF4CC9F0, 0xFF4C6EF5, 0xFFB05CE6, 0xFF000000 };

    private final GuiScreen parent;
    private final RenderBlocks renderBlocks = new RenderBlocks();
    private final boolean gregtech = Loader.isModLoaded("gregtech");
    private final List<Slider> sliders = new ArrayList<>();
    private GuiTextField hex;
    private Slider dragging;
    private final ColorPicker picker = new ColorPicker();

    private int windowX, windowY, windowW, windowH;
    private int previewX, previewY, previewW, previewH;
    private int controlsX, controlsY;

    public OutlineScreen(GuiScreen parent) {
        this.parent = parent;
    }

    /** A value slider; reads and writes its setting through the methods below. */
    private abstract class Slider {

        final String label;
        int x, y, w;

        Slider(String label) {
            this.label = label;
        }

        abstract float get();

        abstract void set(float t);

        abstract String text();

        void draw(int mouseX, int mouseY) {
            GTNHTheme theme = UI.theme();
            UI.text(label, x, y, theme.textDim);
            String value = text();
            UI.text(value, x + w - fontRendererObj.getStringWidth(value), y, theme.text);
            int trackY = y + 11;
            UI.inset(x, trackY, w, 8);
            UI.rect(x + 1, trackY + 1, (w - 2) * get(), 6, theme.barFill);
            int knobX = x + Math.round((w - 6) * get());
            boolean hovered = this == dragging || UI.inside(mouseX, mouseY, x, trackY - 2, w, 12);
            UI.button(knobX, trackY - 2, 6, 12, hovered, true);
        }

        boolean hit(int mouseX, int mouseY) {
            return UI.inside(mouseX, mouseY, x, y + 9, w, 12);
        }

        void drag(int mouseX) {
            set(Math.max(0, Math.min(1, (mouseX - x - 3) / (float) Math.max(1, w - 6))));
        }
    }

    private Slider channel(String label, int shift) {
        return new Slider(label) {

            @Override
            float get() {
                return (MinecraftModule.outlineColor >>> shift & 0xFF) / 255F;
            }

            @Override
            void set(float t) {
                int value = Math.round(t * 255);
                MinecraftModule.outlineColor = MinecraftModule.outlineColor & ~(0xFF << shift) | value << shift;
                syncHex();
            }

            @Override
            String text() {
                return Integer.toString(MinecraftModule.outlineColor >>> shift & 0xFF);
            }
        };
    }

    // ---- layout

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        windowW = Math.min(520, width - 16);
        windowH = Math.min(320, height - 16);
        windowX = (width - windowW) / 2;
        windowY = (height - windowH) / 2;
        previewX = windowX + GAP;
        previewY = windowY + 22;
        previewW = windowW - CONTROLS_WIDTH - GAP * 3;
        previewH = windowH - 22 - GAP;
        controlsX = previewX + previewW + GAP;
        controlsY = previewY;

        sliders.clear();
        sliders.add(channel("Red", 16));
        sliders.add(channel("Green", 8));
        sliders.add(channel("Blue", 0));
        sliders.add(channel("Opacity", 24));
        sliders.add(new Slider("Width") {

            @Override
            float get() {
                return (MinecraftModule.outlineWidth - 0.5F) / 9.5F;
            }

            @Override
            void set(float t) {
                MinecraftModule.outlineWidth = Math.round((0.5F + t * 9.5F) * 2) / 2F;
            }

            @Override
            String text() {
                return MinecraftModule.outlineWidth + " px";
            }
        });
        sliders.add(new Slider("Fill") {

            @Override
            float get() {
                return MinecraftModule.fillOpacity;
            }

            @Override
            void set(float t) {
                MinecraftModule.fillOpacity = Math.round(t * 20) / 20F;
            }

            @Override
            String text() {
                return MinecraftModule.fillOpacity == 0 ? "off" : Math.round(MinecraftModule.fillOpacity * 100) + "%";
            }
        });

        int y = controlsY + 46;
        for (Slider slider : sliders) {
            slider.x = controlsX;
            slider.w = CONTROLS_WIDTH;
            slider.y = y;
            y += 26;
            // a gap between the color and the other settings
            if (slider.label.equals("Opacity")) y += 4;
        }

        hex = new GuiTextField(fontRendererObj, controlsX + 26, controlsY + 4, CONTROLS_WIDTH - 30, 12);
        hex.setEnableBackgroundDrawing(false);
        hex.setMaxStringLength(9);
        syncHex();
    }

    private void syncHex() {
        if (hex != null && !hex.isFocused()) hex.setText(MinecraftModule.formatColor(MinecraftModule.outlineColor));
    }

    private int modeY() {
        return sliders.get(sliders.size() - 1).y + 28;
    }

    private int buttonsY() {
        return windowY + windowH - GAP - 18;
    }

    // ---- drawing

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // the open color picker takes the mouse: no hover on the screen behind it
        int pickerX = mouseX, pickerY = mouseY;
        if (picker.isOpen()) {
            mouseX = -1000;
            mouseY = -1000;
        }
        if (dragging != null) {
            if (Mouse.isButtonDown(0)) dragging.drag(mouseX);
            else dragging = null;
        }
        GTNHTheme theme = UI.theme();
        if (mc.theWorld != null) drawDefaultBackground();
        else {
            UI.beginShapes();
            GLDraw.verticalGradient(0, 0, width, height, theme.backgroundTop, theme.backgroundBottom);
            UI.endShapes();
        }
        UI.panel(windowX, windowY, windowW, windowH);
        UI.text("Block outline", windowX + GAP + 2, windowY + 8, theme.logo);

        UI.inset(previewX, previewY, previewW, previewH);
        drawPreview();

        drawControls(mouseX, mouseY, theme);
        if (UI.inside(mouseX, mouseY, controlsX, controlsY, 20, 20)) {
            drawHoveringText(Collections.singletonList("Click to pick a color"), mouseX, mouseY, fontRendererObj);
        }
        picker.draw(pickerX, pickerY, width, height);
    }

    private void drawControls(int mouseX, int mouseY, GTNHTheme theme) {
        // color: swatch opening the color picker, hex field and presets
        boolean swatchHovered = UI.inside(mouseX, mouseY, controlsX, controlsY, 20, 20);
        UI.button(controlsX, controlsY, 20, 20, swatchHovered, true);
        UI.rect(controlsX + 3, controlsY + 3, 14, 14, MinecraftModule.outlineColor | 0xFF000000);

        UI.inset(controlsX + 22, controlsY, CONTROLS_WIDTH - 22, 20);
        hex.setTextColor(theme.text & 0xFFFFFF);
        hex.yPosition = controlsY + 6;
        hex.drawTextBox();
        int size = (CONTROLS_WIDTH - (PRESETS.length - 1) * 2) / PRESETS.length;
        for (int i = 0; i < PRESETS.length; i++) {
            int x = controlsX + i * (size + 2);
            boolean selected = PRESETS[i] == MinecraftModule.outlineColor;
            UI.inset(x, controlsY + 24, size, 14);
            UI.rect(x + 2, controlsY + 26, size - 4, 10, PRESETS[i] | 0xFF000000);
            if (selected || UI.inside(mouseX, mouseY, x, controlsY + 24, size, 14)) {
                UI.rect(x, controlsY + 37, size, 1, theme.barFill);
            }
        }

        for (Slider slider : sliders) slider.draw(mouseX, mouseY);

        // mode
        int y = modeY();
        UI.text("Mode", controlsX, y, theme.textDim);
        int half = (CONTROLS_WIDTH - 4) / 2;
        boolean through = MinecraftModule.outlineMode == OutlineMode.THROUGH_BLOCKS;
        modeButton(controlsX, y + 10, half, "Normal", !through, mouseX, mouseY);
        modeButton(controlsX + half + 4, y + 10, half, "Through blocks", through, mouseX, mouseY);

        int by = buttonsY();
        UI.button(controlsX, by, half, 18, "Vanilla", mouseX, mouseY, true);
        UI.button(controlsX + half + 4, by, half, 18, "Done", mouseX, mouseY, true);
    }

    private void modeButton(int x, int y, int w, String label, boolean selected, int mouseX, int mouseY) {
        boolean hovered = UI.inside(mouseX, mouseY, x, y, w, 16);
        UI.button(x, y, w, 16, hovered || selected, true);
        if (selected) UI.rect(x + 3, y + 13, w - 6, 1, UI.theme().barFill);
        GTNHTheme theme = UI.theme();
        UI.text(
            label,
            x + (w - fontRendererObj.getStringWidth(label)) / 2,
            y + 4,
            selected ? theme.hoverText : theme.text);
    }

    /** The blocks turning around, with the outline, and the GregTech pipe with the tool overlay. */
    private void drawPreview() {
        float angle = (System.currentTimeMillis() % 12000L) / 12000F * 360F;
        int count = gregtech ? 2 : 1;
        int scale = Math.max(16, Math.min(previewW / (count * 2 + 1), previewH / 3));
        UI.scissor(previewX + 1, previewY + 1, previewW - 2, previewH - 2);
        glClear(GL_DEPTH_BUFFER_BIT);
        for (int i = 0; i < count; i++) {
            float cx = previewX + previewW * (i + 1F) / (count + 1);
            float cy = previewY + previewH / 2F;
            boolean pipe = gregtech && i == 1;
            drawBlock(cx, cy, scale, angle + i * 45, pipe ? null : Blocks.stone, pipe);
        }
        UI.endScissor();
        String caption = gregtech ? "Stone and a GregTech pipe with the wrench overlay" : "Stone";
        UI.text(
            caption,
            previewX + (previewW - fontRendererObj.getStringWidth(caption)) / 2,
            previewY + previewH - 12,
            UI.theme().textDisabled);
    }

    private void drawBlock(float x, float y, float scale, float angle, Block block, boolean pipe) {
        glPushMatrix();
        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_CURRENT_BIT | GL_LINE_BIT | GL_DEPTH_BUFFER_BIT);
        try {
            glTranslatef(x, y, 150);
            glScalef(scale, -scale, scale);
            glRotatef(25, 1, 0, 0);
            glRotatef(angle, 0, 1, 0);
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LEQUAL);
            glDisable(GL_CULL_FACE);
            glEnable(GL_TEXTURE_2D);
            glColor4f(1, 1, 1, 1);

            // the block, lit like in the inventory
            mc.getTextureManager()
                .bindTexture(TextureMap.locationBlocksTexture);
            RenderHelper.enableGUIStandardItemLighting();
            boolean drawn = true;
            if (pipe) drawn = PipePreview.render(renderBlocks);
            else renderBlocks.renderBlockAsItem(block, 0, 1F);
            RenderHelper.disableStandardItemLighting();

            // the outline and, on the pipe, the overlay of a wrench
            glDisable(GL_TEXTURE_2D);
            glDisable(GL_LIGHTING);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            AxisAlignedBB box = AxisAlignedBB.getBoundingBox(-0.502, -0.502, -0.502, 0.502, 0.502, 0.502);
            BlockOutline.draw(
                box,
                MinecraftModule.outlineColor,
                MinecraftModule.outlineWidth,
                MinecraftModule.outlineMode == OutlineMode.THROUGH_BLOCKS,
                MinecraftModule.fillOpacity,
                false);
            if (pipe && drawn) {
                // the highlighted cell goes round the sides, as if the cursor moved over the grid
                ForgeDirection hovered = ForgeDirection.VALID_DIRECTIONS[(int) (System.currentTimeMillis() / 1000
                    % 6)];
                GridOverlay.drawGridAt(0, 0, 0, ForgeDirection.UP, PipePreview::wrenchIcon, hovered, 3);
            }
        } finally {
            glPopAttrib();
            glPopMatrix();
            glColor4f(1, 1, 1, 1);
        }
    }

    // ---- input

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (picker.mouseClicked(mouseX, mouseY, button)) return;
        if (button == 0 && UI.inside(mouseX, mouseY, controlsX, controlsY, 20, 20)) {
            UI.playClick();
            picker.open(MinecraftModule.outlineColor, color -> {
                MinecraftModule.outlineColor = color;
                syncHex();
            });
            return;
        }
        hex.mouseClicked(mouseX, mouseY, button);
        if (!hex.isFocused()) syncHex();
        if (button != 0) return;

        int size = (CONTROLS_WIDTH - (PRESETS.length - 1) * 2) / PRESETS.length;
        for (int i = 0; i < PRESETS.length; i++) {
            if (UI.inside(mouseX, mouseY, controlsX + i * (size + 2), controlsY + 24, size, 14)) {
                MinecraftModule.outlineColor = PRESETS[i];
                syncHex();
                UI.playClick();
                return;
            }
        }
        for (Slider slider : sliders) {
            if (slider.hit(mouseX, mouseY)) {
                dragging = slider;
                slider.drag(mouseX);
                return;
            }
        }
        int half = (CONTROLS_WIDTH - 4) / 2;
        int y = modeY() + 10;
        if (UI.inside(mouseX, mouseY, controlsX, y, half, 16)) {
            MinecraftModule.outlineMode = OutlineMode.NORMAL;
            UI.playClick();
        } else if (UI.inside(mouseX, mouseY, controlsX + half + 4, y, half, 16)) {
            MinecraftModule.outlineMode = OutlineMode.THROUGH_BLOCKS;
            UI.playClick();
        }
        int by = buttonsY();
        if (UI.inside(mouseX, mouseY, controlsX, by, half, 18)) {
            MinecraftModule.outlineColor = MinecraftModule.VANILLA_COLOR;
            MinecraftModule.outlineWidth = MinecraftModule.VANILLA_WIDTH;
            MinecraftModule.outlineMode = OutlineMode.NORMAL;
            MinecraftModule.fillOpacity = 0;
            syncHex();
            UI.playClick();
        } else if (UI.inside(mouseX, mouseY, controlsX + half + 4, by, half, 18)) {
            UI.playClick();
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    protected void keyTyped(char c, int code) {
        if (picker.keyTyped(c, code)) return;
        if (code == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (hex.isFocused()) {
            if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) {
                hex.setFocused(false);
                syncHex();
                return;
            }
            hex.textboxKeyTyped(c, code);
            String text = hex.getText()
                .trim();
            if (text.length() == 6 || text.length() == 8) {
                MinecraftModule.outlineColor = MinecraftModule.parseColor(text, MinecraftModule.outlineColor);
            }
        }
    }

    @Override
    public void updateScreen() {
        hex.updateCursorCounter();
        picker.update();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        if (DebugModule.INSTANCE.isEnabled() && DebugModule.screenCloseCrash) {
            throw new NullPointerException("GTNHTweaks crash test: crash while the outline screen closes");
        }
        MinecraftModule.saveOutline();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
