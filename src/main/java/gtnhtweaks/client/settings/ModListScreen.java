package gtnhtweaks.client.settings;

import static org.lwjgl.opengl.GL11.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.client.IModGuiFactory;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.versioning.ArtifactVersion;
import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.common.render.GLDraw;
import gtnhtweaks.common.render.GTNHTheme;

/**
 * The list of the loaded mods, in place of the one of FML: opened by the Mods button of the main menu and Mod Options
 * of the pause menu.
 * <p>
 * At the left the mods with their logos, searchable by name, id and author; at the right everything known about the
 * selected one: its logo, version, authors, credits, site, description, dependencies, the mods it contains and its
 * file. Its config screen, if it has one, opens from the bottom. The logos are read when they are first shown, a few
 * per frame, and kept while the game runs.
 */
public class ModListScreen extends GuiScreen {

    private static final int MAX_WIDTH = 760;
    private static final int MAX_HEIGHT = 440;
    private static final int GAP = 6;
    private static final int TITLE_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 30;
    private static final int ROW = 26;
    private static final int ICON = 20;
    /** Largest side of a logo kept in memory, in pixels. */
    private static final int LOGO_MAX = 256;
    /** Logos read per frame, so opening the screen does not stall. */
    private static final int LOADS_PER_FRAME = 3;

    /** A logo uploaded as a texture; {@link #NONE} for a mod without one. */
    private static final class Logo {

        static final Logo NONE = new Logo(null, 0, 0);
        final ResourceLocation location;
        final int width, height;

        Logo(ResourceLocation location, int width, int height) {
            this.location = location;
            this.width = width;
            this.height = height;
        }
    }

    /** A line of the details: a label, a value, a clickable link, or a gap. */
    private static final class Line {

        final String text;
        final int kind;
        final String link;

        Line(String text, int kind, String link) {
            this.text = text;
            this.kind = kind;
            this.link = link;
        }
    }

    private static final int LABEL = 0;
    private static final int VALUE = 1;
    private static final int LINK = 2;
    private static final int GAP_LINE = 3;

    private static final Map<String, Logo> LOGOS = new HashMap<>();

    private final GuiScreen parent;
    private final List<ModContainer> all = new ArrayList<>();
    private final List<ModContainer> shown = new ArrayList<>();
    /** The modules of a mod, and the mod each module belongs to: its top one when it is a module of a module. */
    private final Map<ModContainer, List<ModContainer>> modules = new HashMap<>();
    private final Map<ModContainer, ModContainer> heads = new HashMap<>();
    /** The ids of the mods whose modules are shown, kept while the game runs. */
    private static final Set<String> EXPANDED = new HashSet<>();
    /** False while searching: the list is flat then. */
    private boolean grouped = true;
    private ModContainer selected;
    private final List<Line> lines = new ArrayList<>();
    private final Scroll listScroll = new Scroll();
    private final Scroll detailScroll = new Scroll();
    private GuiTextField search;
    private int searchW;
    private int loadsThisFrame;
    private long lastFrame;

    private int windowX, windowY, windowW, windowH;
    private int listX, listY, listW, listH;
    private int detailX, detailY, detailW, detailH;
    private int headerH;

    public ModListScreen(GuiScreen parent) {
        this.parent = parent;
        all.addAll(Loader.instance()
            .getModList());
        all.sort((a, b) -> stripped(a.getName()).compareToIgnoreCase(stripped(b.getName())));
        group();
    }

    /** Puts every module under its mod; see {@link #parentOf}. */
    private void group() {
        for (ModContainer mod : all) {
            ModContainer head = parentOf(mod);
            // a module of a module goes under the top mod
            for (int depth = 0; head != null && depth < 8; depth++) {
                ModContainer up = parentOf(head);
                if (up == null || up == mod) break;
                head = up;
            }
            if (head == null || head == mod) continue;
            heads.put(mod, head);
            modules.computeIfAbsent(head, k -> new ArrayList<>())
                .add(mod);
        }
    }

    /**
     * The mod a mod is a module of, or null. The mod says so itself (its parent in mcmod.info), the other mod lists it
     * among its child mods, or its name starts with the whole name of another mod and a space, a dash or a colon:
     * "EndlessIDs Biome Module" is a module of "EndlessIDs". The longest such name wins.
     */
    private ModContainer parentOf(ModContainer mod) {
        ModMetadata meta = mod.getMetadata();
        if (meta != null && meta.parentMod != null && meta.parentMod != mod && all.contains(meta.parentMod)) {
            return meta.parentMod;
        }
        String name = stripped(mod.getName());
        ModContainer best = null;
        int bestLength = 0;
        for (ModContainer other : all) {
            if (other == mod) continue;
            ModMetadata otherMeta = other.getMetadata();
            if (otherMeta != null && otherMeta.childMods != null && otherMeta.childMods.contains(mod)) return other;
            String otherName = stripped(other.getName());
            int length = otherName.length();
            if (length < 3 || length <= bestLength || name.length() <= length + 1) continue;
            char next = name.charAt(length);
            if ((next == ' ' || next == '-' || next == ':') && name.regionMatches(true, 0, otherName, 0, length)) {
                best = other;
                bestLength = length;
            }
        }
        return best;
    }

    private static String stripped(String text) {
        return text == null ? "" : text.replaceAll("§.", "");
    }

    // ---- layout

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        windowW = Math.min(MAX_WIDTH, width - 12);
        windowH = Math.min(MAX_HEIGHT, height - 12);
        windowX = (width - windowW) / 2;
        windowY = (height - windowH) / 2;
        listX = windowX + GAP;
        listY = windowY + TITLE_HEIGHT;
        listW = Math.min(230, windowW * 2 / 5);
        listH = windowH - TITLE_HEIGHT - FOOTER_HEIGHT;
        detailX = listX + listW + GAP;
        detailY = listY;
        detailW = windowX + windowW - GAP - detailX;
        detailH = listH;

        String query = search != null ? search.getText() : "";
        boolean focused = search != null && search.isFocused();
        // under the list, in the row of the buttons; room is left at its right for the count of the found mods
        searchW = listW - 12 - 46;
        search = new GuiTextField(fontRendererObj, listX + 6, footerY() + 6, searchW, 12);
        search.setEnableBackgroundDrawing(false);
        search.setMaxStringLength(64);
        search.setText(query);
        search.setFocused(focused);

        filter();
        if (selected == null && !shown.isEmpty()) select(shown.get(0));
        else if (selected != null) select(selected);
    }

    private String query() {
        return search == null ? ""
            : search.getText()
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    /**
     * The mods shown: without a search, the mods with their modules under them when opened; with one, every mod
     * matching it, by name, id or author, modules too.
     */
    private void filter() {
        String query = query();
        shown.clear();
        grouped = query.isEmpty();
        for (ModContainer mod : all) {
            if (grouped) {
                if (heads.containsKey(mod)) continue;
                shown.add(mod);
                List<ModContainer> parts = modules.get(mod);
                if (parts != null && EXPANDED.contains(mod.getModId())) shown.addAll(parts);
            } else if (matches(mod, query)) shown.add(mod);
        }
        listScroll.content = shown.size() * ROW + 4;
        listScroll.view = listH - 4;
        listScroll.clamp();
    }

    private static boolean matches(ModContainer mod, String query) {
        if (stripped(mod.getName()).toLowerCase(Locale.ROOT)
            .contains(query)
            || mod.getModId()
                .toLowerCase(Locale.ROOT)
                .contains(query))
            return true;
        ModMetadata meta = mod.getMetadata();
        if (meta != null && meta.authorList != null) {
            for (String author : meta.authorList) {
                if (author != null && author.toLowerCase(Locale.ROOT)
                    .contains(query)) return true;
            }
        }
        return false;
    }

    /** Shows or hides the modules of a mod. */
    private void toggle(ModContainer mod) {
        if (!EXPANDED.remove(mod.getModId())) EXPANDED.add(mod.getModId());
        // a hidden module gives the selection to its mod
        if (heads.get(selected) == mod && !EXPANDED.contains(mod.getModId())) selected = mod;
        filter();
        select(selected);
    }

    private void select(ModContainer mod) {
        if (mod != selected) detailScroll.reset();
        selected = mod;
        buildLines();
        int index = shown.indexOf(mod);
        if (index >= 0) listScroll.show(index * ROW, index * ROW + ROW + 4);
    }

    /** The details of the selected mod, wrapped to the width of the panel. */
    private void buildLines() {
        lines.clear();
        if (selected == null) return;
        ModMetadata meta = selected.getMetadata();
        int textW = detailW - 16 - Scroll.BAR_WIDTH;
        headerH = 54;

        if (meta != null && meta.authorList != null && !meta.authorList.isEmpty()) {
            section("Authors", String.join(", ", meta.authorList), textW);
        }
        if (meta != null && notEmpty(meta.credits)) section("Credits", meta.credits, textW);
        if (meta != null && notEmpty(meta.url)) {
            lines.add(new Line("Website", LABEL, null));
            String url = meta.url.trim();
            lines.add(new Line(fontRendererObj.trimStringToWidth(url, textW), LINK, url));
            lines.add(new Line("", GAP_LINE, null));
        }
        if (meta != null && notEmpty(meta.description)) section("Description", meta.description, textW);

        List<String> requirements = new ArrayList<>();
        for (ArtifactVersion version : selected.getRequirements()) {
            if (version != null) requirements.add(version.getLabel());
        }
        if (!requirements.isEmpty()) section("Requires", String.join(", ", requirements), textW);
        List<String> children = new ArrayList<>();
        if (meta != null && meta.childMods != null) {
            for (ModContainer child : meta.childMods) children.add(stripped(child.getName()));
        }
        List<ModContainer> parts = modules.get(selected);
        if (parts != null) {
            for (ModContainer part : parts) {
                String name = stripped(part.getName());
                if (!children.contains(name)) children.add(name);
            }
        }
        if (!children.isEmpty()) section("Contains", String.join(", ", children), textW);
        ModContainer head = heads.get(selected);
        if (head != null && (meta == null || meta.parentMod != head)) {
            section("Module of", stripped(head.getName()), textW);
        }
        if (meta != null && meta.parentMod != null) {
            section("Part of", stripped(meta.parentMod.getName()), textW);
        }
        File source = selected.getSource();
        if (source != null) section("File", source.getName(), textW);

        int height = headerH;
        for (Line line : lines) height += line.kind == GAP_LINE ? 5 : 10;
        detailScroll.content = height + 6;
        detailScroll.view = detailH - 4;
        detailScroll.clamp();
    }

    private static boolean notEmpty(String text) {
        return text != null && !text.trim()
            .isEmpty();
    }

    private void section(String label, String value, int width) {
        lines.add(new Line(label, LABEL, null));
        for (String part : value.replace("\r", "")
            .split("\n")) {
            for (Object wrapped : fontRendererObj.listFormattedStringToWidth(part, width)) {
                lines.add(new Line((String) wrapped, VALUE, null));
            }
        }
        lines.add(new Line("", GAP_LINE, null));
    }

    // ---- drawing

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(100, (now - lastFrame) / 1_000_000F);
        lastFrame = now;
        loadsThisFrame = 0;
        dragBar(listScroll, listY, listH, mouseY);
        dragBar(detailScroll, detailY, detailH, mouseY);
        listScroll.update(dt);
        detailScroll.update(dt);

        GTNHTheme theme = UI.theme();
        if (mc.theWorld != null) drawDefaultBackground();
        else {
            UI.beginShapes();
            GLDraw.verticalGradient(0, 0, width, height, theme.backgroundTop, theme.backgroundBottom);
            UI.endShapes();
        }
        UI.panel(windowX, windowY, windowW, windowH);
        drawTitle(theme);
        drawList(mouseX, mouseY, theme);
        drawDetails(mouseX, mouseY, theme);
        drawSearch(theme);
        drawFooter(mouseX, mouseY);
    }

    private void dragBar(Scroll scroll, int y, int h, int mouseY) {
        if (!scroll.dragging) return;
        if (Mouse.isButtonDown(0)) scroll.drag(y + 2, h - 4, mouseY);
        else scroll.dragging = false;
    }

    private void drawTitle(GTNHTheme theme) {
        UI.text("Mods", windowX + GAP + 2, windowY + 10, theme.logo);
        String count = all.size() + " loaded";
        UI.text(count, windowX + GAP + 8 + fontRendererObj.getStringWidth("Mods"), windowY + 10, theme.textDisabled);

    }

    /** The search box under the list, with the count of the found mods at its right. */
    private void drawSearch(GTNHTheme theme) {
        int y = footerY();
        UI.inset(listX, y, listW, 20);
        if (search.isFocused()) UI.rect(listX, y + 19, listW, 1, theme.barFill);
        if (search.getText()
            .isEmpty() && !search.isFocused()) {
            UI.text("Search mods", search.xPosition, search.yPosition, theme.textDisabled);
        } else if (!query().isEmpty()) {
            String found = String.valueOf(shown.size());
            UI.text(
                found,
                listX + listW - 6 - fontRendererObj.getStringWidth(found),
                search.yPosition,
                shown.isEmpty() ? Rows.ERROR : theme.textDim);
        }
        search.setTextColor(theme.text & 0xFFFFFF);
        search.drawTextBox();
    }

    /** Top of the row of the buttons and the search, under the list. */
    private int footerY() {
        return windowY + windowH - FOOTER_HEIGHT + 6;
    }

    private void drawList(int mouseX, int mouseY, GTNHTheme theme) {
        UI.inset(listX, listY, listW, listH);
        boolean over = UI.inside(mouseX, mouseY, listX, listY, listW, listH);
        int barW = listScroll.active() ? Scroll.BAR_WIDTH + 3 : 0;
        int w = listW - 4 - barW;
        UI.scissor(listX + 1, listY + 1, listW - 2, listH - 2);
        int first = Math.max(0, listScroll.offset() / ROW);
        for (int i = first; i < shown.size(); i++) {
            int y = listY + 2 + i * ROW - listScroll.offset();
            if (y > listY + listH) break;
            ModContainer mod = shown.get(i);
            boolean isSelected = mod == selected;
            boolean hovered = over && UI.inside(mouseX, mouseY, listX + 2, y, w, ROW);
            if (isSelected) UI.button(listX + 2, y, w, ROW, true, true);
            else if (hovered) UI.rect(listX + 2, y, w, ROW, 0x14FFFFFF);
            if (isSelected) UI.rect(listX + 3, y + 4, 2, ROW - 8, theme.barFill);

            // modules are indented under their mod, which has an arrow to show or hide them and their count
            boolean module = grouped && heads.containsKey(mod);
            List<ModContainer> parts = grouped ? modules.get(mod) : null;
            int iconX = listX + 16 + (module ? 12 : 0);
            if (module) UI.rect(listX + 19, y, 1, ROW, theme.textDisabled & 0x60FFFFFF);
            int right = listX + 2 + w - 4;
            if (parts != null) {
                boolean open = EXPANDED.contains(mod.getModId());
                boolean arrowHovered = over && UI.inside(mouseX, mouseY, listX + 2, y, 14, ROW);
                arrow(listX + 6, y + ROW / 2, open, arrowHovered ? theme.hoverText : theme.textDim);
                String count = "+" + parts.size();
                right -= fontRendererObj.getStringWidth(count) + 4;
                UI.text(count, right + 4, y + (ROW - 8) / 2, theme.textDisabled);
            }
            drawIcon(mod, iconX, y + (ROW - ICON) / 2, ICON, ICON);
            int textX = iconX + ICON + 6;
            int textW = right - textX;
            String name = fontRendererObj.trimStringToWidth(stripped(mod.getName()), textW);
            UI.text(name, textX, y + 4, isSelected ? theme.hoverText : theme.text);
            String version = fontRendererObj.trimStringToWidth(stripped(mod.getDisplayVersion()), textW);
            UI.text(version, textX, y + 14, theme.textDisabled);
        }
        UI.endScissor();
        listScroll.drawBar(listX + listW - Scroll.BAR_WIDTH - 2, listY + 2, listH - 4, mouseX, mouseY);
    }

    /** A small pixel triangle pointing right, or down when open, centered on its left middle. */
    private static void arrow(int x, int middleY, boolean open, int color) {
        for (int i = 0; i < 4; i++) {
            if (open) UI.rect(x + i, middleY - 2 + i, 7 - i * 2, 1, color);
            else UI.rect(x + 1 + i, middleY - 3 + i, 1, 7 - i * 2, color);
        }
    }

    private void drawDetails(int mouseX, int mouseY, GTNHTheme theme) {
        UI.inset(detailX, detailY, detailW, detailH);
        // a cleared search brings back the mods: the first one is shown again
        if (selected == null && !shown.isEmpty()) select(shown.get(0));
        if (selected == null) {
            String none = "No mod matches the search";
            UI.text(none, detailX + (detailW - fontRendererObj.getStringWidth(none)) / 2, detailY + 20, theme.textDim);
            return;
        }
        boolean over = UI.inside(mouseX, mouseY, detailX, detailY, detailW, detailH);
        UI.scissor(detailX + 1, detailY + 1, detailW - 2, detailH - 2);
        int x = detailX + 8;
        int y = detailY + 6 - detailScroll.offset();

        // the logo, as wide as it fits beside the name and at most 44 high
        Logo logo = logo(selected);
        int logoH = 44, logoW = 44;
        if (logo != null && logo.location != null) {
            logoW = Math.min(detailW / 2, Math.round(logo.width * logoH / (float) logo.height));
            logoH = Math.round(logo.height * logoW / (float) logo.width);
        }
        drawIcon(selected, x, y + (44 - logoH) / 2, logoW, logoH);
        int nameX = x + logoW + 10;
        glPushMatrix();
        glTranslatef(nameX, y + 6, 0);
        glScalef(1.5F, 1.5F, 1);
        String name = fontRendererObj
            .trimStringToWidth(stripped(selected.getName()), (int) ((detailX + detailW - 10 - nameX) / 1.5F));
        UI.text(name, 0, 0, theme.hoverText);
        glPopMatrix();
        UI.text("Version " + stripped(selected.getDisplayVersion()), nameX, y + 22, theme.textDim);
        UI.text("ID: " + selected.getModId(), nameX, y + 33, theme.textDisabled);
        UI.rect(x, y + headerH - 6, detailW - 16 - Scroll.BAR_WIDTH, 1, theme.isBeveled() ? theme.inset.topLeft
            : theme.barBorder);

        y += headerH;
        for (Line line : lines) {
            if (line.kind == GAP_LINE) {
                y += 5;
                continue;
            }
            if (y + 10 >= detailY && y <= detailY + detailH) {
                if (line.kind == LABEL) UI.text(line.text.toUpperCase(Locale.ROOT), x, y, theme.textDisabled);
                else if (line.kind == LINK) {
                    boolean hovered = over && UI.inside(mouseX, mouseY, x, y - 1, fontRendererObj.getStringWidth(
                        line.text), 10);
                    UI.text(line.text, x, y, hovered ? theme.hoverText : theme.barFill);
                    if (hovered) UI.rect(x, y + 9, fontRendererObj.getStringWidth(line.text), 1, theme.hoverText);
                } else UI.text(line.text, x, y, theme.text);
            }
            y += 10;
        }
        UI.endScissor();
        detailScroll.drawBar(detailX + detailW - Scroll.BAR_WIDTH - 2, detailY + 2, detailH - 4, mouseX, mouseY);
    }

    private void drawFooter(int mouseX, int mouseY) {
        int y = footerY();
        int w = 110;
        UI.button(detailX, y, w, 20, "Config", mouseX, mouseY, configOf(selected) != null);
        UI.button(detailX + w + 6, y, w, 20, "Open mods folder", mouseX, mouseY, true);
        UI.button(windowX + windowW - GAP - 90, y, 90, 20, "Done", mouseX, mouseY, true);
    }

    /** The logo of the mod fitted into the box, or a badge with its first letter for a mod without one. */
    private void drawIcon(ModContainer mod, int x, int y, int w, int h) {
        Logo logo = logo(mod);
        if (logo != null && logo.location != null) {
            // fitted inside the box, keeping its proportions
            float scale = Math.min(w / (float) logo.width, h / (float) logo.height);
            float dw = logo.width * scale, dh = logo.height * scale;
            float dx = x + (w - dw) / 2, dy = y + (h - dh) / 2;
            mc.getTextureManager()
                .bindTexture(logo.location);
            glColor4f(1, 1, 1, 1);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            t.addVertexWithUV(dx, dy + dh, 0, 0, 1);
            t.addVertexWithUV(dx + dw, dy + dh, 0, 1, 1);
            t.addVertexWithUV(dx + dw, dy, 0, 1, 0);
            t.addVertexWithUV(dx, dy, 0, 0, 0);
            t.draw();
            return;
        }
        // a colored badge, its hue from the id of the mod, so every mod keeps its own
        float hue = (mod.getModId()
            .hashCode() & 0xFFFF) / 65535F;
        int color = Color.HSBtoRGB(hue, 0.45F, 0.55F) | 0xFF000000;
        int size = Math.min(w, h);
        int bx = x + (w - size) / 2, by = y + (h - size) / 2;
        UI.rect(bx, by, size, size, color);
        UI.rect(bx, by + size - 2, size, 2, 0x40000000);
        String name = stripped(mod.getName());
        String letter = name.isEmpty() ? "?" : name.substring(0, 1)
            .toUpperCase(Locale.ROOT);
        float scale = size >= 32 ? 2 : 1;
        glPushMatrix();
        glTranslatef(bx + size / 2F, by + size / 2F, 0);
        glScalef(scale, scale, 1);
        fontRendererObj.drawStringWithShadow(letter, -fontRendererObj.getStringWidth(letter) / 2, -4, 0xFFFFFF);
        glPopMatrix();
    }

    /** The logo of the mod, read the first time it is shown; null while it waits for its turn. */
    private Logo logo(ModContainer mod) {
        Logo cached = LOGOS.get(mod.getModId());
        if (cached != null) return cached;
        if (loadsThisFrame >= LOADS_PER_FRAME) return null;
        loadsThisFrame++;
        Logo logo = Logo.NONE;
        try {
            BufferedImage image = readLogo(mod);
            if (image != null && image.getWidth() > 0 && image.getHeight() > 0) {
                image = shrink(image);
                ResourceLocation location = mc.getTextureManager()
                    .getDynamicTextureLocation("gtnhtweaks_modlogo", new DynamicTexture(image));
                logo = new Logo(location, image.getWidth(), image.getHeight());
            }
        } catch (Exception | LinkageError e) {
            GTNHTweaks.LOG.debug("Could not read the logo of " + mod.getModId(), e);
        }
        LOGOS.put(mod.getModId(), logo);
        return logo;
    }

    private static BufferedImage readLogo(ModContainer mod) throws Exception {
        ModMetadata meta = mod.getMetadata();
        if (meta == null || !notEmpty(meta.logoFile)) return null;
        IResourcePack pack = FMLClientHandler.instance()
            .getResourcePackFor(mod.getModId());
        if (pack != null) return pack.getPackImage();
        try (InputStream in = ModListScreen.class.getResourceAsStream(meta.logoFile)) {
            return in == null ? null : ImageIO.read(in);
        }
    }

    /** Big logos are scaled down, so a few hundred mods do not take much memory. */
    private static BufferedImage shrink(BufferedImage image) {
        int side = Math.max(image.getWidth(), image.getHeight());
        if (side <= LOGO_MAX) return image;
        float scale = LOGO_MAX / (float) side;
        int w = Math.max(1, Math.round(image.getWidth() * scale));
        int h = Math.max(1, Math.round(image.getHeight() * scale));
        BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, w, h, null);
        g.dispose();
        return small;
    }

    /** The class of the config screen of the mod, or null if it has none. */
    private static Class<? extends GuiScreen> configOf(ModContainer mod) {
        if (mod == null) return null;
        try {
            IModGuiFactory factory = FMLClientHandler.instance()
                .getGuiFactoryFor(mod);
            return factory == null ? null : factory.mainConfigGuiClass();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    // ---- input

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (UI.inside(mouseX, mouseY, listX, footerY(), listW, 20)) {
            search.setFocused(true);
            if (button == 1) {
                search.setText("");
                searched();
            }
            return;
        }
        search.setFocused(false);
        if (button != 0) return;

        if (listScroll.press(listX + listW - Scroll.BAR_WIDTH - 2, listY + 2, listH - 4, mouseX, mouseY)) return;
        if (detailScroll.press(detailX + detailW - Scroll.BAR_WIDTH - 2, detailY + 2, detailH - 4, mouseX, mouseY)) {
            return;
        }
        if (UI.inside(mouseX, mouseY, listX, listY, listW, listH)) {
            int index = (mouseY - listY - 2 + listScroll.offset()) / ROW;
            if (index >= 0 && index < shown.size() && mouseY - listY - 2 + listScroll.offset() >= 0) {
                UI.playClick();
                ModContainer mod = shown.get(index);
                // the arrow shows or hides the modules
                if (grouped && modules.containsKey(mod) && mouseX < listX + 16) toggle(mod);
                else select(mod);
            }
            return;
        }
        if (UI.inside(mouseX, mouseY, detailX, detailY, detailW, detailH)) {
            clickLink(mouseX, mouseY);
            return;
        }

        int y = footerY(), w = 110;
        if (UI.inside(mouseX, mouseY, detailX, y, w, 20)) openConfig();
        else if (UI.inside(mouseX, mouseY, detailX + w + 6, y, w, 20)) {
            UI.playClick();
            UI.open(new File(mc.mcDataDir, "mods"));
        } else if (UI.inside(mouseX, mouseY, windowX + windowW - GAP - 90, y, 90, 20)) {
            UI.playClick();
            mc.displayGuiScreen(parent);
        }
    }

    private void clickLink(int mouseX, int mouseY) {
        int y = detailY + 6 - detailScroll.offset() + headerH;
        for (Line line : lines) {
            if (line.kind == GAP_LINE) {
                y += 5;
                continue;
            }
            if (line.kind == LINK
                && UI.inside(mouseX, mouseY, detailX + 8, y - 1, fontRendererObj.getStringWidth(line.text), 10)) {
                UI.playClick();
                String url = line.link.contains("://") ? line.link : "http://" + line.link;
                try {
                    Sys.openURL(url);
                } catch (Throwable t) {
                    GTNHTweaks.LOG.warn("Could not open " + url, t);
                }
                return;
            }
            y += 10;
        }
    }

    private void openConfig() {
        Class<? extends GuiScreen> config = configOf(selected);
        if (config == null) return;
        UI.playClick();
        try {
            mc.displayGuiScreen(
                config.getConstructor(GuiScreen.class)
                    .newInstance(this));
        } catch (Exception | LinkageError e) {
            GTNHTweaks.LOG.warn("Could not open the config screen of " + selected.getModId(), e);
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (UI.inside(mouseX, mouseY, listX, listY, listW, listH)) listScroll.wheel(Integer.signum(wheel));
        else detailScroll.wheel(Integer.signum(wheel));
    }

    @Override
    protected void keyTyped(char c, int code) {
        if (search.isFocused()) {
            if (code == Keyboard.KEY_ESCAPE || code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) {
                search.setFocused(false);
                if (code == Keyboard.KEY_ESCAPE) {
                    search.setText("");
                    searched();
                }
                return;
            }
            if (search.textboxKeyTyped(c, code)) searched();
            return;
        }
        if (code == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        if ((code == Keyboard.KEY_RIGHT || code == Keyboard.KEY_LEFT) && grouped && selected != null) {
            // right opens the modules of the mod, left closes them or goes back to the mod of a module
            boolean open = EXPANDED.contains(selected.getModId());
            if (modules.containsKey(selected) && open == (code == Keyboard.KEY_LEFT)) toggle(selected);
            else if (code == Keyboard.KEY_LEFT && heads.containsKey(selected)) select(heads.get(selected));
            return;
        }
        if (code == Keyboard.KEY_UP || code == Keyboard.KEY_DOWN) {
            int index = shown.indexOf(selected) + (code == Keyboard.KEY_UP ? -1 : 1);
            if (index >= 0 && index < shown.size()) select(shown.get(index));
            return;
        }
        if (code == Keyboard.KEY_F && isCtrlKeyDown() || c >= ' ' && c != 127) {
            // typing starts a search
            search.setFocused(true);
            if (code != Keyboard.KEY_F || !isCtrlKeyDown()) {
                search.textboxKeyTyped(c, code);
                searched();
            }
        }
    }

    private void searched() {
        filter();
        // a module found by a search stays selected when the search is cleared: its mod opens
        ModContainer head = heads.get(selected);
        if (grouped && head != null && !shown.contains(selected)) {
            EXPANDED.add(head.getModId());
            filter();
        }
        listScroll.reset();
        if (!shown.isEmpty() && !shown.contains(selected)) select(shown.get(0));
        else if (shown.isEmpty()) {
            selected = null;
            lines.clear();
        }
    }

    @Override
    public void updateScreen() {
        search.updateCursorCounter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return parent == null || parent.doesGuiPauseGame();
    }
}
