package gtnhtweaks.client.settings;

import static org.lwjgl.opengl.GL11.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.common.render.GLDraw;
import gtnhtweaks.common.render.GTNHTheme;
import gtnhtweaks.modules.minecraft.client.PackCatalog;
import gtnhtweaks.modules.minecraft.client.PackCatalog.Pack;
import gtnhtweaks.modules.minecraft.client.PackCatalog.PackFile;
import gtnhtweaks.modules.minecraft.client.PackCatalog.Page;
import gtnhtweaks.modules.minecraft.client.PackCatalog.Source;

/**
 * Resource packs for Minecraft 1.7.10 from Modrinth and CurseForge, opened by the Get packs button of the resource pack
 * screen: searched, looked at and installed into the resource pack folder, from where the resource pack screen shows
 * them when it comes back.
 * <p>
 * At the left the found packs, the next page loaded when the list is scrolled to its end; at the right the selected
 * one with its description, its file for 1.7.10 and the Install button. Everything from the network comes on worker
 * threads and is taken on the tick of the screen.
 */
public class PackBrowserScreen extends GuiScreen {

    private static final int MAX_WIDTH = 760;
    private static final int MAX_HEIGHT = 440;
    private static final int GAP = 6;
    private static final int TITLE_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 30;
    private static final int ROW = 36;
    private static final int ICON = 30;
    private static final int TAB_W = 72;
    /** The search starts this long after the last key, so it does not run for every letter. */
    private static final long SEARCH_DELAY_MS = 400;
    private static final int OK = 0xFF55C060;

    /** The source and the search are kept while the game runs. */
    private static Source source = Source.MODRINTH;
    private static String lastQuery = "";

    /** The icon of a pack: read on a worker, made a texture on the tick. */
    private static final class Icon {

        Future<BufferedImage> image;
        ResourceLocation texture;
        int width, height;
        boolean done;
    }

    /** What is known of the file of a pack for 1.7.10 and its download. */
    private static final class FileState {

        Future<PackFile> lookup;
        PackFile file;
        boolean none;
        String error;
        Future<File> download;
        volatile double progress;
        boolean installed;
    }

    private static final Map<String, Icon> ICONS = new HashMap<>();
    private static final Map<String, FileState> FILES = new HashMap<>();

    private final GuiScreen parent;
    private final File folder;
    private final List<Pack> packs = new ArrayList<>();
    private int total;
    private Future<Page> loading;
    private boolean loadingMore;
    private String error;
    private Pack selected;
    private final List<String> description = new ArrayList<>();
    private final Scroll listScroll = new Scroll();
    private final Scroll detailScroll = new Scroll();
    private GuiTextField search;
    private long typedAt;
    private boolean searchPending;
    private long lastFrame;

    private int windowX, windowY, windowW, windowH;
    private int listX, listY, listW, listH;
    private int detailX, detailY, detailW, detailH;
    private int searchX, searchW;

    public PackBrowserScreen(GuiScreen parent) {
        this.parent = parent;
        this.folder = net.minecraft.client.Minecraft.getMinecraft()
            .getResourcePackRepository()
            .getDirResourcepacks();
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
        listW = Math.min(300, windowW / 2);
        listH = windowH - TITLE_HEIGHT - FOOTER_HEIGHT;
        detailX = listX + listW + GAP;
        detailY = listY;
        detailW = windowX + windowW - GAP - detailX;
        detailH = listH;

        boolean first = search == null;
        String query = first ? lastQuery : search.getText();
        boolean focused = first || search.isFocused();
        int tabsRight = windowX + GAP + fontRendererObj.getStringWidth("Resource packs") + 14 + TAB_W * 2 + 4;
        searchX = Math.max(tabsRight + 8, windowX + windowW - GAP - 200);
        searchW = windowX + windowW - GAP - searchX - 8;
        search = new GuiTextField(fontRendererObj, searchX + 4, windowY + 9, searchW, 12);
        search.setEnableBackgroundDrawing(false);
        search.setMaxStringLength(64);
        search.setText(query);
        search.setFocused(focused);
        if (first) startSearch();
        else layoutLists();
    }

    private void layoutLists() {
        listScroll.content = packs.size() * ROW + 4 + (hasMore() || loading != null ? ROW : 0);
        listScroll.view = listH;
        listScroll.clamp();
    }

    private boolean hasMore() {
        return packs.size() < total;
    }

    // ---- network

    private void startSearch() {
        lastQuery = search.getText()
            .trim();
        searchPending = false;
        if (loading != null) loading.cancel(true);
        packs.clear();
        total = 0;
        error = null;
        selected = null;
        description.clear();
        listScroll.reset();
        String query = lastQuery;
        Source from = source;
        loadingMore = false;
        loading = PackCatalog.run(() -> PackCatalog.search(from, query, 0));
        layoutLists();
    }

    private void loadMore() {
        if (loading != null || !hasMore() || error != null) return;
        String query = lastQuery;
        Source from = source;
        int offset = packs.size();
        loadingMore = true;
        loading = PackCatalog.run(() -> PackCatalog.search(from, query, offset));
        layoutLists();
    }

    @Override
    public void updateScreen() {
        search.updateCursorCounter();
        if (searchPending && System.currentTimeMillis() - typedAt >= SEARCH_DELAY_MS) startSearch();
        if (loading != null && loading.isDone()) {
            try {
                Page page = loading.get();
                if (!loadingMore) packs.clear();
                packs.addAll(page.packs);
                total = page.packs.isEmpty() ? packs.size() : page.total;
                if (selected == null && !packs.isEmpty()) select(packs.get(0));
            } catch (ExecutionException e) {
                error = message(e.getCause());
                GTNHTweaks.LOG.warn("Could not search the resource packs on " + source.label, e.getCause());
            } catch (Exception e) {
                // cancelled by a new search
            }
            loading = null;
            layoutLists();
        }
        // the end of the list is in sight: the next page
        if (listScroll.offset() + listScroll.view >= listScroll.content - ROW * 3) loadMore();
        if (selected != null) updateFile(fileState(selected));
    }

    private static String key(Pack pack) {
        return pack.source + ":" + pack.id;
    }

    private FileState fileState(Pack pack) {
        FileState state = FILES.get(key(pack));
        if (state == null) {
            state = new FileState();
            state.lookup = PackCatalog.run(() -> PackCatalog.fileFor(pack));
            FILES.put(key(pack), state);
        }
        return state;
    }

    private void updateFile(FileState state) {
        if (state.lookup != null && state.lookup.isDone()) {
            try {
                state.file = state.lookup.get();
                state.none = state.file == null;
            } catch (ExecutionException e) {
                state.error = message(e.getCause());
            } catch (Exception e) {
                state.error = "Cancelled";
            }
            state.lookup = null;
        }
        if (state.download != null && state.download.isDone()) {
            try {
                state.download.get();
                state.installed = true;
                state.error = null;
            } catch (ExecutionException e) {
                state.error = message(e.getCause());
                GTNHTweaks.LOG.warn("Could not download the resource pack", e.getCause());
            } catch (Exception e) {
                state.error = "Cancelled";
            }
            state.download = null;
        }
        if (state.file != null && state.download == null && PackCatalog.target(folder, state.file)
            .exists()) state.installed = true;
    }

    private static String message(Throwable t) {
        if (t == null) return "Unknown error";
        String text = t.getMessage();
        if (t instanceof java.net.UnknownHostException) return "No connection to " + text;
        if (t instanceof java.net.SocketTimeoutException) return "The site does not answer";
        return text == null || text.isEmpty() ? t.getClass()
            .getSimpleName() : text;
    }

    private void install(Pack pack) {
        FileState state = fileState(pack);
        if (state.file == null || state.file.url == null || state.download != null) return;
        PackFile file = state.file;
        state.error = null;
        state.progress = 0;
        state.download = PackCatalog.run(() -> PackCatalog.download(file, folder, done -> state.progress = done));
    }

    private void select(Pack pack) {
        selected = pack;
        detailScroll.reset();
        description.clear();
        String text = pack.description == null || pack.description.isEmpty() ? "No description." : pack.description;
        description.addAll(wrap(text, detailW - 16));
        fileState(pack);
    }

    // ---- drawing

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(100, (now - lastFrame) / 1_000_000F);
        lastFrame = now;
        if (listScroll.dragging) {
            if (Mouse.isButtonDown(0)) listScroll.drag(listY + 2, listH - 4, mouseY);
            else listScroll.dragging = false;
        }
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
        drawHeader(mouseX, mouseY, theme);
        drawList(mouseX, mouseY, theme);
        drawDetails(mouseX, mouseY, theme);
        drawFooter(mouseX, mouseY);
    }

    private void drawHeader(int mouseX, int mouseY, GTNHTheme theme) {
        String title = "Resource packs";
        UI.text(title, windowX + GAP + 2, windowY + 10, theme.logo);
        int x = windowX + GAP + fontRendererObj.getStringWidth(title) + 14;
        for (Source tab : Source.values()) {
            boolean on = tab == source;
            boolean hovered = !on && UI.inside(mouseX, mouseY, x, windowY + 5, TAB_W, 18);
            UI.button(x, windowY + 5, TAB_W, 18, on || hovered, true);
            if (on) UI.rect(x + 3, windowY + 20, TAB_W - 6, 1, theme.barFill);
            UI.text(
                tab.label,
                x + (TAB_W - fontRendererObj.getStringWidth(tab.label)) / 2,
                windowY + 10,
                on ? theme.hoverText : theme.textDim);
            x += TAB_W + 4;
        }
        // the search box, with the count of the found packs at its right
        UI.inset(searchX, windowY + 5, searchW + 8, 18);
        if (search.isFocused()) UI.rect(searchX, windowY + 22, searchW + 8, 1, theme.barFill);
        if (search.getText()
            .isEmpty() && !search.isFocused()) {
            UI.text("Search packs for 1.7.10", search.xPosition, search.yPosition, theme.textDisabled);
        }
        search.setTextColor(theme.text & 0xFFFFFF);
        search.drawTextBox();
    }

    private void drawList(int mouseX, int mouseY, GTNHTheme theme) {
        UI.inset(listX, listY, listW, listH);
        boolean over = UI.inside(mouseX, mouseY, listX, listY, listW, listH);
        int barW = listScroll.active() ? Scroll.BAR_WIDTH + 3 : 0;
        int w = listW - 4 - barW;
        UI.scissor(listX + 1, listY + 1, listW - 2, listH - 2);
        int first = Math.max(0, listScroll.offset() / ROW);
        for (int i = first; i < packs.size(); i++) {
            int y = listY + 2 + i * ROW - listScroll.offset();
            if (y > listY + listH) break;
            Pack pack = packs.get(i);
            boolean isSelected = pack == selected;
            boolean hovered = over && UI.inside(mouseX, mouseY, listX + 2, y, w, ROW);
            if (isSelected) UI.button(listX + 2, y, w, ROW, true, true);
            else if (hovered) UI.rect(listX + 2, y, w, ROW, 0x14FFFFFF);
            if (isSelected) UI.rect(listX + 3, y + 5, 2, ROW - 10, theme.barFill);
            int iconX = listX + 9;
            drawIcon(pack, iconX, y + (ROW - ICON) / 2, ICON);
            int textX = iconX + ICON + 6;
            int textW = listX + 2 + w - 4 - textX;
            FileState state = FILES.get(key(pack));
            String name = pack.title;
            if (state != null && state.installed) {
                String mark = " ✔";
                name = fontRendererObj.trimStringToWidth(name, textW - fontRendererObj.getStringWidth(mark));
                UI.text(name, textX, y + 5, isSelected ? theme.hoverText : theme.text);
                UI.text(mark, textX + fontRendererObj.getStringWidth(name), y + 5, OK);
            } else {
                UI.text(
                    fontRendererObj.trimStringToWidth(name, textW),
                    textX,
                    y + 5,
                    isSelected ? theme.hoverText : theme.text);
            }
            String byline = (pack.author.isEmpty() ? "" : "by " + pack.author + "  ")
                + formatCount(pack.downloads)
                + " downloads";
            UI.text(fontRendererObj.trimStringToWidth(byline, textW), textX, y + 15, theme.textDisabled);
            UI.text(
                fontRendererObj.trimStringToWidth(pack.description.replace('\n', ' '), textW),
                textX,
                y + 25,
                theme.textDim);
        }
        // the state of the search under the packs
        int endY = listY + 2 + packs.size() * ROW - listScroll.offset();
        String note = null;
        int color = theme.textDisabled;
        if (loading != null) note = loadingMore ? "Loading more..." : "Searching " + source.label + "...";
        else if (error != null) {
            note = "Error: " + error;
            color = Rows.ERROR;
        } else if (packs.isEmpty()) note = "No resource packs for 1.7.10 found";
        if (note != null) {
            List<String> lines = wrap(note, w - 12);
            for (int i = 0; i < lines.size(); i++) UI.text(lines.get(i), listX + 8, endY + 8 + i * 10, color);
        }
        UI.endScissor();
        listScroll.drawBar(listX + listW - Scroll.BAR_WIDTH - 2, listY + 2, listH - 4, mouseX, mouseY);
    }

    private void drawDetails(int mouseX, int mouseY, GTNHTheme theme) {
        UI.inset(detailX, detailY, detailW, detailH);
        if (selected == null) return;
        int x = detailX + 8;
        int y = detailY + 8;
        drawIcon(selected, x, y, 64);
        int textX = x + 72;
        int textW = detailX + detailW - 8 - textX;
        List<String> titleLines = wrap(selected.title, textW);
        int ty = y + 2;
        for (int i = 0; i < Math.min(2, titleLines.size()); i++, ty += 10)
            UI.text(titleLines.get(i), textX, ty, theme.logo);
        if (!selected.author.isEmpty()) {
            UI.text(fontRendererObj.trimStringToWidth("by " + selected.author, textW), textX, ty + 2, theme.textDim);
            ty += 12;
        }
        UI.text(
            formatCount(selected.downloads) + " downloads on " + selected.source.label,
            textX,
            ty + 2,
            theme.textDisabled);

        // the install button and what is known of the file
        FileState state = fileState(selected);
        int by = y + 72;
        int bw = Math.min(150, detailW - 16);
        String label;
        boolean enabled = false;
        String info = null;
        int infoColor = theme.textDisabled;
        if (state.lookup != null) label = "Looking for the file...";
        else if (state.download != null) label = "Downloading " + Math.round(state.progress * 100) + "%";
        else if (state.installed) {
            label = "Installed";
            info = state.file == null ? null : "In the resource pack folder as " + state.file.fileName;
            infoColor = OK;
        } else if (state.none) {
            label = "No file for 1.7.10";
        } else if (state.file != null && state.file.url == null) {
            label = "Open the page";
            enabled = true;
            info = "The author lets the pack be downloaded only from " + selected.source.label;
        } else {
            label = state.error != null ? "Retry" : "Install";
            enabled = state.file != null || state.error != null;
        }
        if (state.error != null && state.download == null) {
            info = "Error: " + state.error;
            infoColor = Rows.ERROR;
        } else if (info == null && state.file != null && !state.file.version.isEmpty()) {
            info = "Version " + state.file.version + " for 1.7.10";
        }
        UI.button(x, by, bw, 20, label, mouseX, mouseY, enabled);
        if (state.download != null) {
            // the progress under the label
            UI.rect(x + 3, by + 16, Math.round((bw - 6) * (float) state.progress), 2, theme.barFill);
        }
        UI.button(x + bw + 6, by, 90, 20, "Open the page", mouseX, mouseY, !selected.pageUrl.isEmpty());
        int dy = by + 26;
        if (info != null) {
            for (String line : wrap(info, detailW - 16)) {
                UI.text(line, x, dy, infoColor);
                dy += 10;
            }
        }
        dy += 6;
        UI.rect(x, dy, detailW - 16, 1, theme.textDisabled & 0x40FFFFFF);
        dy += 6;

        // the description, scrolled
        int top = dy;
        int bottom = detailY + detailH - 4;
        detailScroll.content = description.size() * 10 + 4;
        detailScroll.view = bottom - top;
        detailScroll.clamp();
        UI.scissor(detailX + 1, top, detailW - 2, bottom - top);
        for (int i = 0; i < description.size(); i++) {
            int ly = top + i * 10 - detailScroll.offset();
            if (ly < top - 10 || ly > bottom) continue;
            UI.text(description.get(i), x, ly, theme.text);
        }
        UI.endScissor();
        detailScroll.drawBar(detailX + detailW - Scroll.BAR_WIDTH - 2, top, bottom - top, mouseX, mouseY);
    }

    private int footerY() {
        return listY + listH + (FOOTER_HEIGHT - 4 - 20) / 2;
    }

    private void drawFooter(int mouseX, int mouseY) {
        int y = footerY();
        UI.button(listX, y, 150, 20, "Open resource pack folder", mouseX, mouseY, true);
        UI.button(windowX + windowW - GAP - 90, y, 90, 20, "Done", mouseX, mouseY, true);
        String hint = "Only packs for Minecraft 1.7.10 are shown";
        int hx = listX + 150 + 10;
        if (hx + fontRendererObj.getStringWidth(hint) < windowX + windowW - GAP - 100)
            UI.text(hint, hx, y + 6, UI.theme().textDisabled);
    }

    /** The icon of the pack, or a colored badge with its first letter while it loads or when it has none. */
    private void drawIcon(Pack pack, int x, int y, int size) {
        Icon icon = ICONS.get(key(pack));
        if (icon == null) {
            icon = new Icon();
            icon.image = PackCatalog.run(() -> PackCatalog.icon(pack));
            ICONS.put(key(pack), icon);
        }
        if (!icon.done && icon.image.isDone()) {
            icon.done = true;
            try {
                BufferedImage image = icon.image.get();
                if (image != null) {
                    icon.texture = mc.getTextureManager()
                        .getDynamicTextureLocation("gtnhtweaks_packicon", new DynamicTexture(image));
                    icon.width = image.getWidth();
                    icon.height = image.getHeight();
                }
            } catch (Exception ignored) {
                // no icon: the badge stays
            }
        }
        if (icon.texture != null) {
            float scale = Math.min(size / (float) icon.width, size / (float) icon.height);
            float dw = icon.width * scale, dh = icon.height * scale;
            float dx = x + (size - dw) / 2, dy = y + (size - dh) / 2;
            mc.getTextureManager()
                .bindTexture(icon.texture);
            glColor4f(1, 1, 1, 1);
            glEnable(GL_TEXTURE_2D);
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
        // a badge in a color of its own for every pack
        float hue = (pack.id.hashCode() & 0xFFFF) / 65535F;
        int color = 0xFF000000 | Color.HSBtoRGB(hue, 0.45F, 0.55F) & 0xFFFFFF;
        UI.rect(x, y, size, size, color);
        String letter = pack.title.isEmpty() ? "?"
            : pack.title.substring(0, 1)
                .toUpperCase(Locale.ROOT);
        glPushMatrix();
        float scale = size >= 48 ? 3 : 2;
        glTranslatef(x + size / 2F, y + size / 2F, 0);
        glScalef(scale, scale, 1);
        fontRendererObj.drawStringWithShadow(letter, -fontRendererObj.getStringWidth(letter) / 2, -4, 0xFFFFFF);
        glPopMatrix();
    }

    @SuppressWarnings("unchecked")
    private List<String> wrap(String text, int width) {
        return fontRendererObj.listFormattedStringToWidth(text, width);
    }

    private static String formatCount(long count) {
        if (count >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0);
        if (count >= 1_000) return String.format(Locale.ROOT, "%.1fK", count / 1_000.0);
        return String.valueOf(count);
    }

    // ---- input

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (UI.inside(mouseX, mouseY, searchX, windowY + 5, searchW + 8, 18)) {
            search.setFocused(true);
            if (button == 1) {
                search.setText("");
                typed();
            }
            return;
        }
        search.setFocused(false);
        if (button != 0) return;

        int x = windowX + GAP + fontRendererObj.getStringWidth("Resource packs") + 14;
        for (Source tab : Source.values()) {
            if (UI.inside(mouseX, mouseY, x, windowY + 5, TAB_W, 18) && tab != source) {
                UI.playClick();
                source = tab;
                startSearch();
                return;
            }
            x += TAB_W + 4;
        }

        if (listScroll.press(listX + listW - Scroll.BAR_WIDTH - 2, listY + 2, listH - 4, mouseX, mouseY)) return;
        if (UI.inside(mouseX, mouseY, listX, listY, listW, listH)) {
            int position = mouseY - listY - 2 + listScroll.offset();
            int index = position / ROW;
            if (position >= 0 && index < packs.size()) {
                UI.playClick();
                select(packs.get(index));
            }
            return;
        }

        if (selected != null) {
            int bx = detailX + 8, by = detailY + 8 + 72;
            int bw = Math.min(150, detailW - 16);
            FileState state = fileState(selected);
            if (UI.inside(mouseX, mouseY, bx, by, bw, 20)) {
                if (state.lookup != null || state.download != null || state.installed || state.none) return;
                UI.playClick();
                if (state.file != null && state.file.url == null) openUrl(selected.pageUrl);
                else if (state.file != null) install(selected);
                else if (state.error != null) {
                    // the lookup failed: asked again
                    FILES.remove(key(selected));
                    fileState(selected);
                }
                return;
            }
            if (UI.inside(mouseX, mouseY, bx + bw + 6, by, 90, 20) && !selected.pageUrl.isEmpty()) {
                UI.playClick();
                openUrl(selected.pageUrl);
                return;
            }
        }

        int y = footerY();
        if (UI.inside(mouseX, mouseY, listX, y, 150, 20)) {
            UI.playClick();
            UI.open(folder);
        } else if (UI.inside(mouseX, mouseY, windowX + windowW - GAP - 90, y, 90, 20)) {
            UI.playClick();
            close();
        }
    }

    private static void openUrl(String url) {
        try {
            Sys.openURL(url);
        } catch (Throwable t) {
            GTNHTweaks.LOG.warn("Could not open " + url, t);
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
            if (code == Keyboard.KEY_ESCAPE) {
                search.setFocused(false);
                return;
            }
            if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) {
                search.setFocused(false);
                startSearch();
                return;
            }
            if (code == Keyboard.KEY_UP || code == Keyboard.KEY_DOWN) {
                moveSelection(code == Keyboard.KEY_UP ? -1 : 1);
                return;
            }
            if (search.textboxKeyTyped(c, code)) typed();
            return;
        }
        if (code == Keyboard.KEY_ESCAPE) {
            close();
            return;
        }
        if (code == Keyboard.KEY_UP || code == Keyboard.KEY_DOWN) {
            moveSelection(code == Keyboard.KEY_UP ? -1 : 1);
            return;
        }
        if (code == Keyboard.KEY_F && isCtrlKeyDown() || c >= ' ' && c != 127) {
            // typing starts a search
            search.setFocused(true);
            if (code != Keyboard.KEY_F || !isCtrlKeyDown()) {
                search.textboxKeyTyped(c, code);
                typed();
            }
        }
    }

    private void moveSelection(int direction) {
        int index = packs.indexOf(selected) + direction;
        if (index < 0 || index >= packs.size()) return;
        select(packs.get(index));
        listScroll.show(index * ROW, (index + 1) * ROW + 4);
    }

    private void typed() {
        typedAt = System.currentTimeMillis();
        searchPending = true;
    }

    /** Back to the resource pack screen, which finds the installed packs in the folder when it comes back. */
    private void close() {
        mc.getResourcePackRepository()
            .updateRepositoryEntriesAll();
        mc.displayGuiScreen(parent);
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
