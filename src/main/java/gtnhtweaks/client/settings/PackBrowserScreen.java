package gtnhtweaks.client.settings;

import static org.lwjgl.opengl.GL11.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
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
import gtnhtweaks.modules.minecraft.client.InstalledPacks;
import gtnhtweaks.modules.minecraft.client.PackCatalog;
import gtnhtweaks.modules.minecraft.client.PackCatalog.Pack;
import gtnhtweaks.modules.minecraft.client.PackCatalog.PackFile;
import gtnhtweaks.modules.minecraft.client.PackCatalog.Page;
import gtnhtweaks.modules.minecraft.client.PackCatalog.Release;
import gtnhtweaks.modules.minecraft.client.PackCatalog.Source;

/**
 * Resource packs to get, opened by the Get packs button of the resource pack screen: the ones of the GTNH wiki and
 * the ones for Minecraft 1.7.10 on Modrinth, searched, looked at and installed into the resource pack folder, from
 * where the resource pack screen shows them when it comes back. A pack installed in an older version gets an Update
 * button, which replaces the old file.
 * <p>
 * At the left the found packs, the next page loaded when the list is scrolled to its end; at the right the selected
 * one with its description and its files, each with its button. Everything from the network comes on worker threads
 * and is taken on the tick of the screen.
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
    private static final int FILE_ROW = 26;
    private static final int FILE_BUTTON_W = 86;
    private static final int OK = 0xFF55C060;
    private static final int UPDATE = 0xFFE0B040;

    /** The source and the search are kept while the game runs. */
    private static Source source = Source.GTNH;
    private static String lastQuery = "";

    /** The icon of a pack: read on a worker, made a texture on the tick. */
    private static final class Icon {

        Future<BufferedImage> image;
        ResourceLocation texture;
        int width, height;
        boolean done;
    }

    /** What is known of the files of a pack, and their downloads. */
    private static final class FileState {

        Future<Release> lookup;
        Release release;
        String error;
        final Map<PackFile, Download> downloads = new IdentityHashMap<>();

        boolean installed() {
            if (release != null) for (PackFile file : release.files) if (file.installedAs != null) return true;
            return false;
        }

        boolean outdated() {
            if (release != null) for (PackFile file : release.files) if (file.outdated) return true;
            return false;
        }
    }

    /** A file being downloaded, or the error of its last download. */
    private static final class Download {

        Future<File> future;
        /** The share done, or the bytes done as a negative number when the size is not known. */
        volatile double progress;
        String error;
    }

    /** A file button drawn on the details, to find it on a click. */
    private static final class FileButton {

        final PackFile file;
        final int x, y;

        FileButton(PackFile file, int x, int y) {
            this.file = file;
            this.x = x;
            this.y = y;
        }
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
    private final List<FileButton> fileButtons = new ArrayList<>();
    private int detailTop, detailBottom;
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
        loading = PackCatalog.run(() -> PackCatalog.search(from, query, 0, folder));
        layoutLists();
    }

    private void loadMore() {
        if (loading != null || !hasMore() || error != null) return;
        String query = lastQuery;
        Source from = source;
        int offset = packs.size();
        loadingMore = true;
        loading = PackCatalog.run(() -> PackCatalog.search(from, query, offset, folder));
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
                // the packs seen in the folder are looked up at once, to show the ones with an update
                for (Pack pack : page.packs) if (pack.likelyInstalled) fileState(pack);
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
        for (FileState state : FILES.values()) updateFile(state);
    }

    private static String key(Pack pack) {
        return pack.key();
    }

    private FileState fileState(Pack pack) {
        FileState state = FILES.get(key(pack));
        if (state == null) {
            state = new FileState();
            state.lookup = PackCatalog.run(() -> PackCatalog.files(pack, folder));
            FILES.put(key(pack), state);
        }
        return state;
    }

    private void updateFile(FileState state) {
        if (state.lookup != null && state.lookup.isDone()) {
            try {
                state.release = state.lookup.get();
                state.error = null;
            } catch (ExecutionException e) {
                state.error = message(e.getCause());
                GTNHTweaks.LOG.warn("Could not look up the files of the resource pack", e.getCause());
            } catch (Exception e) {
                state.error = "Cancelled";
            }
            state.lookup = null;
        }
        for (Map.Entry<PackFile, Download> entry : state.downloads.entrySet()) {
            Download download = entry.getValue();
            if (download.future == null || !download.future.isDone()) continue;
            try {
                installed(
                    entry.getKey(),
                    download.future.get()
                        .getName());
                download.error = null;
            } catch (ExecutionException e) {
                download.error = message(e.getCause());
                GTNHTweaks.LOG.warn("Could not download the resource pack", e.getCause());
            } catch (Exception e) {
                download.error = "Cancelled";
            }
            download.future = null;
        }
    }

    /** The downloaded file is the installed one of its pack: the older version it updates is replaced by it. */
    private void installed(PackFile file, String name) {
        String old = file.installedAs;
        InstalledPacks.installed(file, name);
        if (old != null && !old.equals(name)) InstalledPacks.replace(folder, old, name);
        file.installedAs = name;
        file.outdated = false;
    }

    private static String message(Throwable t) {
        if (t == null) return "Unknown error";
        String text = t.getMessage();
        if (t instanceof java.net.UnknownHostException) return "No connection to " + text;
        if (t instanceof java.net.SocketTimeoutException) return "The site does not answer";
        return text == null || text.isEmpty() ? t.getClass()
            .getSimpleName() : text;
    }

    private void install(Pack pack, PackFile file) {
        FileState state = fileState(pack);
        Download download = state.downloads.get(file);
        if (download == null) state.downloads.put(file, download = new Download());
        if (download.future != null || file.url == null || file.url.isEmpty()) return;
        Download job = download;
        job.error = null;
        job.progress = 0;
        job.future = PackCatalog.run(() -> PackCatalog.download(file, folder, done -> job.progress = done));
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
        if (detailScroll.dragging) {
            if (Mouse.isButtonDown(0)) detailScroll.drag(detailTop, detailBottom - detailTop, mouseY);
            else detailScroll.dragging = false;
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
            UI.text(
                source == Source.GTNH ? "Filter the packs of the wiki" : "Search packs for 1.7.10",
                search.xPosition,
                search.yPosition,
                theme.textDisabled);
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
            boolean outdated = state != null && state.outdated();
            if (outdated || state != null && state.installed()) {
                // installed, or with a newer version to update to
                String mark = outdated ? " ↑ Update" : " ✔";
                name = fontRendererObj.trimStringToWidth(name, textW - fontRendererObj.getStringWidth(mark));
                UI.text(name, textX, y + 5, isSelected ? theme.hoverText : theme.text);
                UI.text(mark, textX + fontRendererObj.getStringWidth(name), y + 5, outdated ? UPDATE : OK);
            } else {
                UI.text(
                    fontRendererObj.trimStringToWidth(name, textW),
                    textX,
                    y + 5,
                    isSelected ? theme.hoverText : theme.text);
            }
            String byline = (pack.author.isEmpty() ? "" : "by " + pack.author + "  ")
                + (pack.source == Source.GTNH ? "GTNH " + pack.versions : formatCount(pack.downloads) + " downloads");
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
        } else if (packs.isEmpty())
            note = source == Source.GTNH ? "No pack of the wiki matches" : "No resource packs for 1.7.10 found";
        if (note != null) {
            List<String> lines = wrap(note, w - 12);
            for (int i = 0; i < lines.size(); i++) UI.text(lines.get(i), listX + 8, endY + 8 + i * 10, color);
        }
        UI.endScissor();
        listScroll.drawBar(listX + listW - Scroll.BAR_WIDTH - 2, listY + 2, listH - 4, mouseX, mouseY);
    }

    private void drawDetails(int mouseX, int mouseY, GTNHTheme theme) {
        UI.inset(detailX, detailY, detailW, detailH);
        fileButtons.clear();
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
        String about = selected.source == Source.GTNH ? selected.category + "  ·  GTNH " + selected.versions
            : formatCount(selected.downloads) + " downloads on " + selected.source.label;
        UI.text(fontRendererObj.trimStringToWidth(about, textW), textX, ty + 2, theme.textDisabled);

        // the page of the pack, and the state of the lookup of its files
        FileState state = selected.pageOnly() ? null : fileState(selected);
        int by = y + 72;
        UI.button(x, by, 90, 20, "Open the page", mouseX, mouseY, !selected.pageUrl.isEmpty());
        if (state != null && state.error != null && state.lookup == null) {
            UI.button(x + 96, by, 60, 20, "Retry", mouseX, mouseY, true);
        } else if (state != null && state.lookup != null) {
            UI.text("Looking for the files...", x + 98, by + 6, theme.textDisabled);
        }

        // the notes, the files and the description, scrolled together
        int top = by + 26;
        int bottom = detailY + detailH - 4;
        detailTop = top;
        detailBottom = bottom;
        int w = detailW - 16 - (detailScroll.active() ? Scroll.BAR_WIDTH + 3 : 0);
        UI.scissor(detailX + 1, top, detailW - 2, bottom - top);
        int dy = top - detailScroll.offset();
        List<String> notes = new ArrayList<>();
        if (!selected.note.isEmpty()) notes.add(selected.note);
        if (state != null && state.error != null && state.lookup == null) notes.add("Error: " + state.error);
        else if (state != null && state.release != null) {
            if (!state.release.note.isEmpty()) notes.add(state.release.note);
            if (state.release.files.isEmpty() && selected.source == Source.GTNH)
                notes.add("No file to download: get it from its page.");
        }
        for (String note : notes) {
            int color = note.startsWith("Error: ") ? Rows.ERROR : theme.textDim;
            for (String line : wrap(note, w)) {
                UI.text(line, x, dy, color);
                dy += 10;
            }
            dy += 4;
        }
        if (state != null && state.release != null) {
            for (PackFile file : state.release.files) {
                drawFile(file, state.downloads.get(file), x, dy, w, mouseX, mouseY, theme);
                dy += FILE_ROW;
            }
        }
        dy += 4;
        UI.rect(x, dy, w, 1, theme.textDisabled & 0x40FFFFFF);
        dy += 7;
        for (String line : description) {
            if (dy > top - 10 && dy < bottom) UI.text(line, x, dy, theme.text);
            dy += 10;
        }
        UI.endScissor();
        detailScroll.content = dy + detailScroll.offset() - top + 4;
        detailScroll.view = bottom - top;
        detailScroll.clamp();
        detailScroll.drawBar(detailX + detailW - Scroll.BAR_WIDTH - 2, top, bottom - top, mouseX, mouseY);
    }

    /** A file of the pack: its name and version, and the button to install or update it. */
    private void drawFile(PackFile file, Download download, int x, int y, int w, int mouseX, int mouseY,
        GTNHTheme theme) {
        boolean busy = download != null && download.future != null;
        String label;
        boolean enabled = true;
        if (busy) {
            label = download.progress < 0 ? formatBytes(-download.progress)
                : "Downloading " + Math.round(download.progress * 100) + "%";
            enabled = false;
        } else if (file.url == null || file.url.isEmpty()) {
            label = "Open the page";
        } else if (download != null && download.error != null) {
            label = "Retry";
        } else if (file.outdated) {
            label = "Update";
        } else if (file.installedAs != null) {
            label = "Installed";
            enabled = false;
        } else {
            label = "Install";
        }
        int bx = x + w - FILE_BUTTON_W;
        boolean visible = y + 20 > detailTop && y < detailBottom;
        boolean over = visible && mouseY >= detailTop && mouseY < detailBottom;
        UI.button(bx, y, FILE_BUTTON_W, 20, label, over ? mouseX : -1, over ? mouseY : -1, enabled);
        if (busy && download.progress > 0) {
            UI.rect(bx + 3, y + 16, Math.round((FILE_BUTTON_W - 6) * (float) download.progress), 2, theme.barFill);
        }
        if (visible) fileButtons.add(new FileButton(file, bx, y));

        int textW = bx - 6 - x;
        UI.text(fontRendererObj.trimStringToWidth(file.label, textW), x, y + 1, theme.text);
        String info;
        int color = theme.textDisabled;
        if (download != null && download.error != null && !busy) {
            info = "Error: " + download.error;
            color = Rows.ERROR;
        } else if (file.outdated) {
            info = (file.version.isEmpty() ? "A new version" : "Version " + file.version) + " out, installed: "
                + file.installedAs;
            color = UPDATE;
        } else if (file.installedAs != null) {
            info = "Installed as " + file.installedAs;
            color = OK;
        } else {
            info = file.version.isEmpty() ? file.fileName : "Version " + file.version;
        }
        UI.text(fontRendererObj.trimStringToWidth(info, textW), x, y + 11, color);
    }

    private int footerY() {
        return listY + listH + (FOOTER_HEIGHT - 4 - 20) / 2;
    }

    private void drawFooter(int mouseX, int mouseY) {
        int y = footerY();
        UI.button(listX, y, 150, 20, "Open resource pack folder", mouseX, mouseY, true);
        UI.button(windowX + windowW - GAP - 90, y, 90, 20, "Done", mouseX, mouseY, true);
        String line = PackCatalog.playerLine();
        String hint = source == Source.GTNH
            ? "The packs of the GTNH wiki" + (line == null ? "" : ", releases for GTNH " + line + " first")
            : "Only packs for Minecraft 1.7.10 are shown";
        int hx = listX + 150 + 10;
        if (hx + fontRendererObj.getStringWidth(hint) < windowX + windowW - GAP - 100)
            UI.text(hint, hx, y + 6, UI.theme().textDisabled);
    }

    /** The icon of the pack, or a colored badge with its first letter while it loads or when it has none. */
    private void drawIcon(Pack pack, int x, int y, int size) {
        // by its address: the packs of one author on GitHub share their icon
        String iconKey = pack.iconUrl == null || pack.iconUrl.isEmpty() ? key(pack) : pack.iconUrl;
        Icon icon = ICONS.get(iconKey);
        if (icon == null) {
            icon = new Icon();
            icon.image = PackCatalog.run(() -> PackCatalog.icon(pack));
            ICONS.put(iconKey, icon);
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

    private static String formatBytes(double bytes) {
        if (bytes >= 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB", bytes / (1024 * 1024));
        return String.format(Locale.ROOT, "%d KB", Math.round(bytes / 1024));
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
            if (UI.inside(mouseX, mouseY, bx, by, 90, 20) && !selected.pageUrl.isEmpty()) {
                UI.playClick();
                openUrl(selected.pageUrl);
                return;
            }
            FileState state = selected.pageOnly() ? null : fileState(selected);
            if (state != null && state.error != null
                && state.lookup == null
                && UI.inside(mouseX, mouseY, bx + 96, by, 60, 20)) {
                // the lookup failed: asked again
                UI.playClick();
                FILES.remove(key(selected));
                fileState(selected);
                return;
            }
            if (detailScroll
                .press(detailX + detailW - Scroll.BAR_WIDTH - 2, detailTop, detailBottom - detailTop, mouseX, mouseY))
                return;
            if (mouseY >= detailTop && mouseY < detailBottom) {
                for (FileButton hit : fileButtons) {
                    if (!UI.inside(mouseX, mouseY, hit.x, hit.y, FILE_BUTTON_W, 20)) continue;
                    clickFile(state, hit.file);
                    return;
                }
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

    private void clickFile(FileState state, PackFile file) {
        Download download = state == null ? null : state.downloads.get(file);
        if (download != null && download.future != null) return;
        if (file.url == null || file.url.isEmpty()) {
            UI.playClick();
            openUrl(selected.pageUrl);
            return;
        }
        if (file.installedAs != null && !file.outdated && (download == null || download.error == null)) return;
        UI.playClick();
        install(selected, file);
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
