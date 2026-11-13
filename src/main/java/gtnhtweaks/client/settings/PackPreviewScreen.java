package gtnhtweaks.client.settings;

import static org.lwjgl.opengl.GL11.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.common.render.GLDraw;
import gtnhtweaks.common.render.GTNHTheme;
import gtnhtweaks.modules.minecraft.client.PackCatalog;
import gtnhtweaks.modules.minecraft.client.PackCatalog.Pack;
import gtnhtweaks.modules.minecraft.client.PackCatalog.PackFile;
import gtnhtweaks.modules.minecraft.client.PackPreview;
import gtnhtweaks.modules.minecraft.client.PackPreview.Category;
import gtnhtweaks.modules.minecraft.client.PackPreview.Image;
import gtnhtweaks.modules.minecraft.client.PackPreview.Texture;

/**
 * What a resource pack downloaded from the browser changes, before it is installed: its textures by mod and by kind,
 * each beside the one the game shows now, a big comparison under the mouse, and the count of its other files. Install
 * puts the downloaded file in the resource pack folder without downloading it again.
 */
public class PackPreviewScreen extends GuiScreen {

    private static final int MAX_WIDTH = 760;
    private static final int MAX_HEIGHT = 440;
    private static final int GAP = 6;
    private static final int TITLE_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 30;
    private static final int MODS_W = 150;
    private static final int MOD_ROW = 14;
    private static final int TAB_H = 18;
    private static final int CELL_W = 80;
    private static final int CELL_H = 48;
    private static final int THUMB = 32;
    private static final int BIG = 112;
    /** Textures kept on the graphics card at most; the ones out of sight longest go first. */
    private static final int MAX_TEXTURES = 600;

    /** An image of a texture: read on a worker, made a texture on the tick. */
    private static final class Slot {

        Future<Image> image;
        Image loaded;
        ResourceLocation texture;
        boolean done;
        long seen;
    }

    private final PackBrowserScreen browser;
    private final Pack pack;
    private final PackFile file;
    private final File zip;
    private Future<PackPreview> reading;
    private PackPreview preview;
    private String error;
    private String domain;
    private Category category;
    private final List<Texture> shown = new ArrayList<>();
    private final Map<Texture, Slot> before = new IdentityHashMap<>();
    private final Map<Texture, Slot> after = new IdentityHashMap<>();
    private final Map<String, String> modNames = new HashMap<>();
    private final Scroll modScroll = new Scroll();
    private final Scroll gridScroll = new Scroll();
    private long lastFrame;
    private long frame;

    private int windowX, windowY, windowW, windowH;
    private int modsX, modsY, modsH;
    private int gridX, gridY, gridW, gridH, columns;

    public PackPreviewScreen(PackBrowserScreen browser, Pack pack, PackFile file, File zip) {
        this.browser = browser;
        this.pack = pack;
        this.file = file;
        this.zip = zip;
        reading = PackCatalog.run(() -> PackPreview.read(zip));
    }

    // ---- layout

    @Override
    public void initGui() {
        windowW = Math.min(MAX_WIDTH, width - 12);
        windowH = Math.min(MAX_HEIGHT, height - 12);
        windowX = (width - windowW) / 2;
        windowY = (height - windowH) / 2;
        modsX = windowX + GAP;
        modsY = windowY + TITLE_HEIGHT;
        modsH = windowH - TITLE_HEIGHT - FOOTER_HEIGHT;
        gridX = modsX + MODS_W + GAP;
        gridY = modsY + TAB_H + 4;
        gridW = windowX + windowW - GAP - gridX;
        gridH = modsY + modsH - gridY;
        columns = Math.max(1, (gridW - 4 - Scroll.BAR_WIDTH - 3) / CELL_W);
        layout();
    }

    private void layout() {
        modScroll.content = (preview == null ? 0 : preview.domains.size() + 1) * MOD_ROW + 4;
        modScroll.view = modsH;
        modScroll.clamp();
        gridScroll.content = (shown.size() + columns - 1) / columns * CELL_H + 4;
        gridScroll.view = gridH;
        gridScroll.clamp();
    }

    /** The textures of the chosen mod and kind. */
    private void filter() {
        shown.clear();
        if (preview != null) {
            for (Texture texture : preview.textures) {
                if ((domain == null || domain.equals(texture.domain))
                    && (category == null || category == texture.category)) shown.add(texture);
            }
        }
        gridScroll.reset();
        layout();
    }

    private int count(Category of) {
        if (preview == null) return 0;
        int count = 0;
        for (Texture texture : preview.textures) {
            if ((domain == null || domain.equals(texture.domain)) && (of == null || of == texture.category)) count++;
        }
        return count;
    }

    // ---- tick

    @Override
    public void updateScreen() {
        if (reading != null && reading.isDone()) {
            try {
                preview = reading.get();
                filter();
            } catch (ExecutionException e) {
                error = e.getCause() == null ? "Unknown error"
                    : String.valueOf(
                        e.getCause()
                            .getMessage());
                GTNHTweaks.LOG.warn("Could not read the resource pack " + zip, e.getCause());
            } catch (Exception e) {
                error = "Cancelled";
            }
            reading = null;
        }
        frame++;
    }

    private Slot slot(Texture texture, boolean pack) {
        Map<Texture, Slot> slots = pack ? after : before;
        Slot slot = slots.get(texture);
        if (slot == null) {
            slot = new Slot();
            PackPreview from = preview;
            slot.image = PackCatalog.run(() -> pack ? from.after(texture) : PackPreview.before(texture));
            slots.put(texture, slot);
        }
        slot.seen = frame;
        if (!slot.done && slot.image.isDone()) {
            slot.done = true;
            try {
                slot.loaded = slot.image.get();
                if (slot.loaded != null) slot.texture = mc.getTextureManager()
                    .getDynamicTextureLocation("gtnhtweaks_packpreview", new DynamicTexture(slot.loaded.image));
            } catch (Exception ignored) {
                // no image: shown as missing
            }
        }
        return slot;
    }

    /** Frees the textures out of sight longest when too many are kept. */
    private void trim() {
        int loaded = 0;
        for (Slot slot : before.values()) if (slot.texture != null) loaded++;
        for (Slot slot : after.values()) if (slot.texture != null) loaded++;
        if (loaded <= MAX_TEXTURES) return;
        trim(before);
        trim(after);
    }

    private void trim(Map<Texture, Slot> slots) {
        for (Iterator<Slot> it = slots.values()
            .iterator(); it.hasNext();) {
            Slot slot = it.next();
            if (frame - slot.seen < 2 || !slot.done) continue;
            if (slot.texture != null) mc.getTextureManager()
                .deleteTexture(slot.texture);
            it.remove();
        }
    }

    // ---- drawing

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(100, (now - lastFrame) / 1_000_000F);
        lastFrame = now;
        dragScroll(modScroll, modsY + 2, modsH - 4, mouseY);
        dragScroll(gridScroll, gridY + 2, gridH - 4, mouseY);
        modScroll.update(dt);
        gridScroll.update(dt);

        GTNHTheme theme = UI.theme();
        if (mc.theWorld != null) drawDefaultBackground();
        else {
            UI.beginShapes();
            GLDraw.verticalGradient(0, 0, width, height, theme.backgroundTop, theme.backgroundBottom);
            UI.endShapes();
        }
        UI.panel(windowX, windowY, windowW, windowH);
        String title = "Preview: " + pack.title + (file.label.equals(pack.title) ? "" : " - " + file.label);
        UI.text(fontRendererObj.trimStringToWidth(title, windowW / 2), windowX + GAP + 2, windowY + 10, theme.logo);
        if (preview != null) {
            String summary = String
                .format(Locale.ROOT, "%,d textures from %d mods", preview.textures.size(), preview.domains.size());
            UI.text(
                summary,
                windowX + windowW - GAP - 2 - fontRendererObj.getStringWidth(summary),
                windowY + 10,
                theme.textDim);
        }

        drawMods(mouseX, mouseY, theme);
        Texture hovered = drawGrid(mouseX, mouseY, theme);
        drawFooter(mouseX, mouseY, theme);
        if (hovered != null) drawComparison(hovered, mouseX, mouseY, theme);
        trim();
    }

    private static void dragScroll(Scroll scroll, int y, int h, int mouseY) {
        if (!scroll.dragging) return;
        if (Mouse.isButtonDown(0)) scroll.drag(y, h, mouseY);
        else scroll.dragging = false;
    }

    private void drawMods(int mouseX, int mouseY, GTNHTheme theme) {
        UI.inset(modsX, modsY, MODS_W, modsH);
        if (preview == null) return;
        int w = MODS_W - 4 - (modScroll.active() ? Scroll.BAR_WIDTH + 3 : 0);
        UI.scissor(modsX + 1, modsY + 1, MODS_W - 2, modsH - 2);
        int y = modsY + 2 - modScroll.offset();
        drawMod(null, "All mods", preview.textures.size(), y, w, mouseX, mouseY, theme);
        y += MOD_ROW;
        for (Map.Entry<String, Integer> entry : preview.domains.entrySet()) {
            if (y > modsY - MOD_ROW && y < modsY + modsH)
                drawMod(entry.getKey(), modName(entry.getKey()), entry.getValue(), y, w, mouseX, mouseY, theme);
            y += MOD_ROW;
        }
        UI.endScissor();
        modScroll.drawBar(modsX + MODS_W - Scroll.BAR_WIDTH - 2, modsY + 2, modsH - 4, mouseX, mouseY);
    }

    private void drawMod(String key, String name, int count, int y, int w, int mouseX, int mouseY, GTNHTheme theme) {
        boolean on = key == null ? domain == null : key.equals(domain);
        boolean hovered = UI.inside(mouseX, mouseY, modsX + 2, y, w, MOD_ROW)
            && UI.inside(mouseX, mouseY, modsX, modsY, MODS_W, modsH);
        if (on) UI.button(modsX + 2, y, w, MOD_ROW, true, true);
        else if (hovered) UI.rect(modsX + 2, y, w, MOD_ROW, 0x14FFFFFF);
        String number = String.format(Locale.ROOT, "%,d", count);
        int numberW = fontRendererObj.getStringWidth(number);
        UI.text(
            fontRendererObj.trimStringToWidth(name, w - numberW - 12),
            modsX + 6,
            y + 3,
            on ? theme.hoverText : theme.text);
        UI.text(number, modsX + 2 + w - 4 - numberW, y + 3, theme.textDisabled);
    }

    /** The name of the mod of a texture domain, the domain itself for one not loaded. */
    private String modName(String key) {
        String name = modNames.get(key);
        if (name == null) {
            name = key;
            if ("minecraft".equals(key)) name = "Minecraft";
            else {
                for (ModContainer mod : Loader.instance()
                    .getModList()) {
                    if (mod.getModId()
                        .equalsIgnoreCase(key)) {
                        name = mod.getName();
                        break;
                    }
                }
            }
            modNames.put(key, name);
        }
        return name;
    }

    private List<Category> tabs() {
        List<Category> tabs = new ArrayList<>();
        tabs.add(null);
        for (Category of : Category.values()) if (count(of) > 0) tabs.add(of);
        return tabs;
    }

    private String tabLabel(Category of) {
        return (of == null ? "All" : of.label) + " " + count(of);
    }

    /** Draws the tabs and the textures; gives the one under the mouse. */
    private Texture drawGrid(int mouseX, int mouseY, GTNHTheme theme) {
        if (preview != null) {
            int x = gridX;
            for (Category of : tabs()) {
                String label = tabLabel(of);
                int w = fontRendererObj.getStringWidth(label) + 12;
                boolean on = of == category;
                boolean hovered = !on && UI.inside(mouseX, mouseY, x, modsY, w, TAB_H);
                UI.button(x, modsY, w, TAB_H, on || hovered, true);
                if (on) UI.rect(x + 3, modsY + TAB_H - 3, w - 6, 1, theme.barFill);
                UI.text(label, x + 6, modsY + 5, on ? theme.hoverText : theme.textDim);
                x += w + 3;
            }
        }
        UI.inset(gridX, gridY, gridW, gridH);
        String note = null;
        if (reading != null) note = "Reading the pack...";
        else if (error != null) note = "Could not read the pack: " + error;
        else if (preview != null && preview.textures.isEmpty()) note = "The pack changes no textures.";
        if (note != null) {
            UI.text(note, gridX + 8, gridY + 8, error != null ? Rows.ERROR : theme.textDisabled);
            return null;
        }
        if (preview == null) return null;

        Texture hovered = null;
        boolean over = UI.inside(mouseX, mouseY, gridX, gridY, gridW, gridH);
        UI.scissor(gridX + 1, gridY + 1, gridW - 2, gridH - 2);
        int first = Math.max(0, gridScroll.offset() / CELL_H) * columns;
        for (int i = first; i < shown.size(); i++) {
            int x = gridX + 2 + i % columns * CELL_W;
            int y = gridY + 2 + i / columns * CELL_H - gridScroll.offset();
            if (y > gridY + gridH) break;
            Texture texture = shown.get(i);
            boolean isHovered = over && UI.inside(mouseX, mouseY, x, y, CELL_W, CELL_H);
            if (isHovered) {
                UI.rect(x, y, CELL_W - 2, CELL_H - 2, 0x14FFFFFF);
                hovered = texture;
            }
            int thumbY = y + 3;
            int beforeX = x + (CELL_W - 2 - THUMB * 2 - 10) / 2;
            drawImage(slot(texture, false), beforeX, thumbY, THUMB, theme);
            UI.text("›", beforeX + THUMB + 3, thumbY + THUMB / 2 - 4, theme.textDisabled);
            drawImage(slot(texture, true), beforeX + THUMB + 10, thumbY, THUMB, theme);
            String name = fontRendererObj.trimStringToWidth(texture.name(), CELL_W - 6);
            UI.text(name, x + (CELL_W - 2 - fontRendererObj.getStringWidth(name)) / 2, y + THUMB + 6, theme.textDim);
        }
        UI.endScissor();
        gridScroll.drawBar(gridX + gridW - Scroll.BAR_WIDTH - 2, gridY + 2, gridH - 4, mouseX, mouseY);
        return hovered;
    }

    /** The texture under the mouse, big: as the game shows it now and as the pack has it. */
    private void drawComparison(Texture texture, int mouseX, int mouseY, GTNHTheme theme) {
        int w = BIG * 2 + 30, h = BIG + 46;
        int x = mouseX + 12, y = mouseY + 12;
        if (x + w > width - 4) x = mouseX - 12 - w;
        if (y + h > height - 4) y = height - 4 - h;
        x = Math.max(4, x);
        y = Math.max(4, y);
        glPushMatrix();
        glTranslatef(0, 0, 300);
        UI.panel(x, y, w, h);
        String path = texture.domain + ":" + texture.path;
        UI.text(fontRendererObj.trimStringToWidth(path, w - 12), x + 6, y + 6, theme.text);
        UI.text("Now", x + 10, y + 20, theme.textDisabled);
        UI.text("With the pack", x + 20 + BIG, y + 20, theme.textDisabled);
        drawImage(slot(texture, false), x + 10, y + 32, BIG, theme);
        drawImage(slot(texture, true), x + 20 + BIG, y + 32, BIG, theme);
        glPopMatrix();
    }

    /** The image fitted in the square, its frame of the moment for an animated one. */
    private void drawImage(Slot slot, int x, int y, int size, GTNHTheme theme) {
        UI.rect(x, y, size, size, 0x30000000);
        if (slot.texture == null) {
            String mark = slot.done ? "none" : "...";
            UI.text(mark, x + (size - fontRendererObj.getStringWidth(mark)) / 2, y + size / 2 - 4, theme.textDisabled);
            return;
        }
        Image image = slot.loaded;
        int w = image.image.getWidth(), h = image.image.getHeight() / image.frames;
        float scale = Math.min(size / (float) w, size / (float) h);
        float dw = w * scale, dh = h * scale;
        float dx = x + (size - dw) / 2, dy = y + (size - dh) / 2;
        int current = image.frames <= 1 ? 0 : (int) (System.currentTimeMillis() / 50 / image.frameTime % image.frames);
        float v0 = current / (float) image.frames, v1 = (current + 1) / (float) image.frames;
        mc.getTextureManager()
            .bindTexture(slot.texture);
        glColor4f(1, 1, 1, 1);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(dx, dy + dh, 0, 0, v1);
        t.addVertexWithUV(dx + dw, dy + dh, 0, 1, v1);
        t.addVertexWithUV(dx + dw, dy, 0, 1, v0);
        t.addVertexWithUV(dx, dy, 0, 0, v0);
        t.draw();
    }

    private int footerY() {
        return modsY + modsH + (FOOTER_HEIGHT - 4 - 20) / 2;
    }

    private String installLabel() {
        if (file.outdated) return "Update";
        return file.installedAs != null ? "Installed" : "Install";
    }

    private boolean canInstall() {
        return file.outdated || file.installedAs == null;
    }

    private void drawFooter(int mouseX, int mouseY, GTNHTheme theme) {
        int y = footerY();
        int backX = windowX + windowW - GAP - 70;
        int installX = backX - 6 - 90;
        UI.button(installX, y, 90, 20, installLabel(), mouseX, mouseY, canInstall() && preview != null);
        UI.button(backX, y, 70, 20, "Back", mouseX, mouseY, true);
        if (preview == null) return;
        List<String> parts = new ArrayList<>();
        if (preview.sounds > 0) parts.add(preview.sounds + " sounds");
        if (preview.languages > 0) parts.add(preview.languages + " languages");
        if (preview.others > 0) parts.add(preview.others + " other files");
        String also = parts.isEmpty() ? "Only textures" : "Also changes " + String.join(", ", parts);
        UI.text(fontRendererObj.trimStringToWidth(also, installX - 12 - modsX), modsX + 2, y + 6, theme.textDisabled);
    }

    // ---- input

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (button != 0) return;
        int y = footerY();
        int backX = windowX + windowW - GAP - 70;
        int installX = backX - 6 - 90;
        if (UI.inside(mouseX, mouseY, backX, y, 70, 20)) {
            UI.playClick();
            back();
            return;
        }
        if (UI.inside(mouseX, mouseY, installX, y, 90, 20) && canInstall() && preview != null) {
            UI.playClick();
            // the zip is let go first: Windows does not move an open file
            release();
            browser.installPreviewed(pack, file, zip);
            mc.displayGuiScreen(browser);
            return;
        }
        if (preview == null) return;

        if (modScroll.press(modsX + MODS_W - Scroll.BAR_WIDTH - 2, modsY + 2, modsH - 4, mouseX, mouseY)) return;
        if (gridScroll.press(gridX + gridW - Scroll.BAR_WIDTH - 2, gridY + 2, gridH - 4, mouseX, mouseY)) return;
        if (UI.inside(mouseX, mouseY, modsX, modsY, MODS_W, modsH)) {
            int index = (mouseY - modsY - 2 + modScroll.offset()) / MOD_ROW;
            List<String> keys = new ArrayList<>(preview.domains.keySet());
            if (index >= 0 && index <= keys.size()) {
                UI.playClick();
                domain = index == 0 ? null : keys.get(index - 1);
                // a kind the mod has no textures of is left
                if (category != null && count(category) == 0) category = null;
                filter();
            }
            return;
        }
        int x = gridX;
        for (Category of : tabs()) {
            int w = fontRendererObj.getStringWidth(tabLabel(of)) + 12;
            if (UI.inside(mouseX, mouseY, x, modsY, w, TAB_H)) {
                if (of != category) {
                    UI.playClick();
                    category = of;
                    filter();
                }
                return;
            }
            x += w + 3;
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        if (mouseX < gridX) modScroll.wheel(Integer.signum(wheel));
        else gridScroll.wheel(Integer.signum(wheel));
    }

    @Override
    protected void keyTyped(char c, int code) {
        if (code == Keyboard.KEY_ESCAPE) back();
    }

    private void back() {
        mc.displayGuiScreen(browser);
    }

    /** Frees the textures and lets the zip go. */
    private void release() {
        if (reading != null) {
            // still being read: closed once it is
            Future<PackPreview> pending = reading;
            reading = null;
            PackCatalog.run(() -> {
                pending.get()
                    .close();
                return null;
            });
        }
        for (Slot slot : before.values()) if (slot.texture != null) mc.getTextureManager()
            .deleteTexture(slot.texture);
        for (Slot slot : after.values()) if (slot.texture != null) mc.getTextureManager()
            .deleteTexture(slot.texture);
        before.clear();
        after.clear();
        if (preview != null) preview.close();
        preview = null;
    }

    @Override
    public void onGuiClosed() {
        release();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return browser.doesGuiPauseGame();
    }
}
