package gtnhtweaks.modules.minecraft.client;

import static org.lwjgl.opengl.GL11.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
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
    private Slider dragging;
    private final ColorPicker picker = new ColorPicker();
    private float rainbowSwitch = MinecraftModule.rainbow ? 1 : 0;
    private long lastFrame;

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
        sliders.add(new Slider("Rainbow speed") {

            @Override
            float get() {
                return (MinecraftModule.rainbowSpeed - 0.05F) / 4.95F;
            }

            @Override
            void set(float t) {
                MinecraftModule.rainbowSpeed = Math.max(0.05F, Math.round((0.05F + t * 4.95F) * 20) / 20F);
            }

            @Override
            String text() {
                return String.format("%.2f /s", MinecraftModule.rainbowSpeed);
            }
        });

        for (Slider slider : sliders) {
            slider.x = controlsX;
            slider.w = CONTROLS_WIDTH;
        }
        // color and its presets, fill and its presets, rainbow and its speed, mode, width
        sliders.get(1).y = rainbowY() + 18;
        sliders.get(0).y = modeY() + 30;
    }

    private int presetsY() {
        return controlsY + 24;
    }

    private int fillY() {
        return controlsY + 42;
    }

    private int fillPresetsY() {
        return fillY() + 22;
    }

    private int rainbowY() {
        return fillPresetsY() + 18;
    }

    private int modeY() {
        return sliders.get(1).y + 26;
    }

    private int presetSize() {
        return (CONTROLS_WIDTH - (PRESETS.length - 1) * 2) / PRESETS.length;
    }

    /** The preset under the mouse in the row at y, or -1. */
    private int presetAt(int mouseX, int mouseY, int y) {
        int size = presetSize();
        for (int i = 0; i < PRESETS.length; i++) {
            if (UI.inside(mouseX, mouseY, controlsX + i * (size + 2), y, size, 14)) return i;
        }
        return -1;
    }

    /** A preset as fill: its color with the opacity of the fill, or a third opaque when there is no fill. */
    private static int fillPreset(int preset) {
        int alpha = MinecraftModule.fillColor >>> 24;
        return preset & 0xFFFFFF | (alpha > 0 ? alpha : 0x55) << 24;
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
        if (UI.inside(mouseX, mouseY, controlsX, controlsY, CONTROLS_WIDTH, 20)) {
            drawHoveringText(Collections.singletonList("Click to pick a color"), mouseX, mouseY, fontRendererObj);
        }
        picker.draw(pickerX, pickerY, width, height);
    }

    private void drawControls(int mouseX, int mouseY, GTNHTheme theme) {
        // color: a button with the color that opens the color picker, and presets
        boolean colorHovered = UI.inside(mouseX, mouseY, controlsX, controlsY, CONTROLS_WIDTH, 20);
        UI.button(controlsX, controlsY, CONTROLS_WIDTH, 20, colorHovered, true);
        UI.inset(controlsX + 3, controlsY + 3, 26, 14);
        UI.rect(controlsX + 5, controlsY + 5, 22, 10, MinecraftModule.outlineColor | 0xFF000000);
        UI.text("Color", controlsX + 35, controlsY + 6, colorHovered ? theme.hoverText : theme.text);
        String opacity = Math.round((MinecraftModule.outlineColor >>> 24) * 100 / 255F) + "%";
        UI.text(
            opacity,
            controlsX + CONTROLS_WIDTH - 6 - fontRendererObj.getStringWidth(opacity),
            controlsY + 6,
            theme.textDim);
        drawPresets(presetsY(), MinecraftModule.outlineColor, false, mouseX, mouseY, theme);

        // fill: a button with the fill color that opens the color picker
        int fy = fillY();
        boolean fillHovered = UI.inside(mouseX, mouseY, controlsX, fy, CONTROLS_WIDTH, 20);
        UI.button(controlsX, fy, CONTROLS_WIDTH, 20, fillHovered, true);
        UI.inset(controlsX + 3, fy + 3, 26, 14);
        boolean filled = (MinecraftModule.fillColor >>> 24) > 0;
        if (filled) UI.rect(controlsX + 5, fy + 5, 22, 10, MinecraftModule.fillColor | 0xFF000000);
        UI.text("Fill", controlsX + 35, fy + 6, fillHovered ? theme.hoverText : theme.text);
        String fill = filled ? Math.round((MinecraftModule.fillColor >>> 24) * 100 / 255F) + "%" : "off";
        UI.text(fill, controlsX + CONTROLS_WIDTH - 6 - fontRendererObj.getStringWidth(fill), fy + 6, theme.textDim);
        drawPresets(fillPresetsY(), MinecraftModule.fillColor, true, mouseX, mouseY, theme);

        // rainbow switch and its speed
        int ry = rainbowY();
        boolean rainbowHovered = UI.inside(mouseX, mouseY, controlsX, ry, CONTROLS_WIDTH, 16);
        UI.text("Rainbow", controlsX, ry + 4, rainbowHovered ? theme.hoverText : theme.textDim);
        rainbowSwitch = UI.approach(rainbowSwitch, MinecraftModule.rainbow ? 1 : 0, frameMs(), 120);
        UI.toggle(controlsX + CONTROLS_WIDTH - 22, ry + 3, rainbowSwitch, rainbowHovered);

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

    private void drawPresets(int y, int current, boolean fill, int mouseX, int mouseY, GTNHTheme theme) {
        int size = presetSize();
        for (int i = 0; i < PRESETS.length; i++) {
            int x = controlsX + i * (size + 2);
            boolean selected = fill
                ? (current >>> 24) > 0 && (current & 0xFFFFFF) == (PRESETS[i] & 0xFFFFFF)
                : PRESETS[i] == current;
            UI.inset(x, y, size, 14);
            UI.rect(x + 2, y + 2, size - 4, 10, PRESETS[i] | 0xFF000000);
            if (selected || UI.inside(mouseX, mouseY, x, y, size, 14)) {
                UI.rect(x, y + 13, size, 1, theme.barFill);
            }
        }
    }

    /** Milliseconds since the last frame, for the switch animation. */
    private float frameMs() {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(100, (now - lastFrame) / 1_000_000F);
        lastFrame = now;
        return dt;
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
                MinecraftModule.fillColor,
                1.0 / (scale * new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor()));
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
        if (button == 0 && UI.inside(mouseX, mouseY, controlsX, controlsY, CONTROLS_WIDTH, 20)) {
            UI.playClick();
            picker.open(MinecraftModule.outlineColor, color -> MinecraftModule.outlineColor = color);
            return;
        }
        if (button != 0) return;

        int preset = presetAt(mouseX, mouseY, presetsY());
        if (preset >= 0) {
            MinecraftModule.outlineColor = PRESETS[preset];
            UI.playClick();
            return;
        }
        preset = presetAt(mouseX, mouseY, fillPresetsY());
        if (preset >= 0) {
            MinecraftModule.fillColor = fillPreset(PRESETS[preset]);
            UI.playClick();
            return;
        }
        if (UI.inside(mouseX, mouseY, controlsX, rainbowY(), CONTROLS_WIDTH, 16)) {
            MinecraftModule.rainbow = !MinecraftModule.rainbow;
            UI.playClick();
            return;
        }
        if (UI.inside(mouseX, mouseY, controlsX, fillY(), CONTROLS_WIDTH, 20)) {
            UI.playClick();
            int fill = MinecraftModule.fillColor;
            // no fill yet: start with the line color, a third opaque
            int start = (fill >>> 24) > 0 ? fill : MinecraftModule.outlineColor & 0xFFFFFF | 0x55 << 24;
            picker.open(start, fill, color -> MinecraftModule.fillColor = color);
            return;
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
            MinecraftModule.fillColor = 0;
            MinecraftModule.rainbow = false;
            MinecraftModule.rainbowSpeed = 0.5F;
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
    }

    @Override
    public void updateScreen() {
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
