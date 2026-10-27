package gtnhtweaks.client.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import gtnhtweaks.Tags;
import gtnhtweaks.common.render.GLDraw;
import gtnhtweaks.common.render.GTNHTheme;
import gtnhtweaks.core.ModuleManager;
import gtnhtweaks.core.TweakModule;

/**
 * Settings of GTNHTweaks: the modules on the left, in groups, with their on/off switches; the settings of the selected
 * module on the right, in sections that fold; a search over every setting. Changes are saved and applied at once; the
 * ones that need a restart are counted at the bottom.
 */
public class SettingsScreen extends GuiScreen {

    private static final int MAX_WIDTH = 660;
    private static final int MAX_HEIGHT = 420;
    private static final int SIDEBAR_WIDTH = 146;
    private static final int TITLE_HEIGHT = 26;
    private static final int FOOTER_HEIGHT = 28;
    private static final int MODULE_ROW = 18;
    private static final int GROUP_ROW = 14;
    private static final int SECTION_ROW = 18;
    private static final int GAP = 6;
    private static final int DONE_W = 70, FOLDER_W = 84;

    private static final Map<String, String> NAMES = new HashMap<>();
    /**
     * Groups of the client and the server section of the sidebar, in order, with the ids of their modules. Modules in
     * none go to "Other" of the section of their side; the modules of both sides are in both sections.
     */
    private static final Map<String, List<String>> CLIENT_GROUPS = new LinkedHashMap<>();
    private static final Map<String, List<String>> SERVER_GROUPS = new LinkedHashMap<>();

    static {
        NAMES.put("loadingscreen", "Loading screen");
        NAMES.put("mainmenu", "Main menu");
        NAMES.put("menus", "Menus");
        NAMES.put("minecraft", "Minecraft");
        NAMES.put("minecraftServer", "Minecraft");
        NAMES.put("itemphysics", "Item physics");
        NAMES.put("gregtech", "GregTech");
        NAMES.put("nei", "NEI");
        NAMES.put("chromatictooltips", "Chromatic Tooltips");
        NAMES.put("tinkers", "Tinkers' Construct");
        NAMES.put("extrautilities", "Extra Utilities");
        NAMES.put("bees", "Bees");
        NAMES.put("logs", "Logs");
        NAMES.put("backups", "Backups");
        NAMES.put("backupsServer", "Backups");
        NAMES.put("debug", "Debug");

        CLIENT_GROUPS.put("Interface", Arrays.asList("loadingscreen", "mainmenu", "menus"));
        CLIENT_GROUPS.put("Game", Arrays.asList("minecraft", "itemphysics"));
        CLIENT_GROUPS
            .put("Mods", Arrays.asList("gregtech", "nei", "chromatictooltips", "tinkers", "extrautilities", "bees"));
        CLIENT_GROUPS.put("Tools", Arrays.asList("logs", "backups", "debug"));
        SERVER_GROUPS.put("Game", Arrays.asList("minecraftServer"));
        SERVER_GROUPS.put("Tools", Arrays.asList("logs", "backupsServer"));
    }

    /** Page shown when the screen opens again. */
    private static int lastPage;
    /** Folded sections, as module id + "." + category, kept while the game runs. */
    private static final Set<String> FOLDED = new HashSet<>();

    private final GuiScreen parent;
    private final List<Page> pages = new ArrayList<>();
    /** Sidebar lines: a group name or a page. */
    private final List<SideEntry> side = new ArrayList<>();
    private final List<Rows.Row> rows = new ArrayList<>();
    /** Values before the first change in this screen, to tell which changes need a restart. */
    private final Map<Property, String> initialValues = new IdentityHashMap<>();
    private GuiTextField search;
    private int searchW;
    private int page;
    private int results;

    private int windowX, windowY, windowW, windowH;
    private int listX, listY, listW, listH;
    private int contentX, contentY, contentW, contentH;

    private final Scroll listScroll = new Scroll();
    private final Scroll contentScroll = new Scroll();
    private Rows.Control dragging;
    private long lastFrame;

    public SettingsScreen(GuiScreen parent) {
        this.parent = parent;
        this.page = lastPage;
    }

    /** A module with its rows. */
    private static final class Page {

        final TweakModule module;
        /** The name with the side, as in the search results; the sidebar shows the short one under its section. */
        final String name;
        final String shortName;
        final Property enabled;
        final List<Rows.Row> rows = new ArrayList<>();
        final List<Rows.SettingRow> settings = new ArrayList<>();
        float switchPosition;

        Page(TweakModule module, String name, String shortName, Property enabled) {
            this.module = module;
            this.name = name;
            this.shortName = shortName;
            this.enabled = enabled;
            this.switchPosition = isOn() ? 1 : 0;
        }

        boolean isOn() {
            return enabled == null || enabled.getBoolean(false);
        }

        /** Whether the module runs now, which can differ from the switch until a restart. */
        boolean isRunning() {
            return module.isEnabled();
        }
    }

    private static final class SideEntry {

        final String group;
        /** The line is the title of the client or the server section, not of a group. */
        final boolean section;
        final int page;
        final int y, h;

        SideEntry(String group, int page, int y, int h) {
            this(group, false, page, y, h);
        }

        SideEntry(String group, boolean section, int page, int y, int h) {
            this.group = group;
            this.section = section;
            this.page = page;
            this.y = y;
            this.h = h;
        }
    }

    public static String displayName(TweakModule module) {
        String name = NAMES.get(module.getId());
        return name != null ? name : UI.humanize(module.getId());
    }

    /** The name with the side for the server modules, whose names are the same as of their client modules. */
    static String fullName(TweakModule module) {
        String name = displayName(module);
        return module.getSide() == TweakModule.ModuleSide.SERVER ? name + " (server)" : name;
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
        listW = Math.min(SIDEBAR_WIDTH, windowW / 3);
        listH = windowH - TITLE_HEIGHT - FOOTER_HEIGHT;
        contentX = listX + listW + GAP;
        contentY = listY;
        contentW = windowX + windowW - GAP - contentX;
        contentH = listH;

        String query = search != null ? search.getText() : "";
        boolean focused = search != null && search.isFocused();
        searchW = Math.min(200, windowW / 3) - 8;
        search = new GuiTextField(fontRendererObj, windowX + windowW - GAP - searchW - 4, windowY + 9, searchW, 12);
        search.setEnableBackgroundDrawing(false);
        search.setMaxStringLength(64);
        search.setText(query);
        search.setFocused(focused);

        buildPages();
        page = Math.max(0, Math.min(page, pages.size() - 1));
        buildSidebar();
        showRows();
    }

    private void buildPages() {
        pages.clear();
        int controlWidth = Math.max(80, Math.min(140, contentW / 2 - 50));
        Configuration modules = ModuleManager.getModulesConfig();
        for (TweakModule module : ModuleManager.getModules()) {
            Property enabled = modules.hasCategory("modules") ? modules.getCategory("modules")
                .get(module.getId()) : null;
            Page p = new Page(module, fullName(module), displayName(module), enabled);
            p.rows.add(new Rows.TitleRow(this, module, p.name, contentW, p.settings));
            Configuration config = ModuleManager.getConfig(module);
            List<String> categories = new ArrayList<>(config.getCategoryNames());
            // the general settings first
            if (categories.remove("general")) categories.add(0, "general");
            for (String name : categories) {
                ConfigCategory category = config.getCategory(name);
                if (category.isChild() || category.getValues()
                    .isEmpty()) continue;
                Rows.HeaderRow header = new Rows.HeaderRow(
                    this,
                    module.getId() + "." + name,
                    UI.humanize(name),
                    category.getComment());
                p.rows.add(header);
                String button = module.settingsButton(name);
                if (button != null) {
                    String category0 = name;
                    p.rows.add(new Rows.ButtonRow(button, () -> {
                        commitAll();
                        module.openSettingsButton(category0, this);
                    }));
                }
                for (Property property : category.getValues()
                    .values()) {
                    if (!module.showInSettings(name, property.getName())) continue;
                    Rows.SettingRow row = new Rows.SettingRow(this, p.name, property, controlWidth);
                    header.settings.add(row);
                    p.settings.add(row);
                    p.rows.add(row);
                }
            }
            pages.add(p);
        }
    }

    private void buildSidebar() {
        side.clear();
        int y = 2;
        y = addSection("Client", CLIENT_GROUPS, true, y);
        y = addSection("Server", SERVER_GROUPS, false, y);
        listScroll.content = y + 2;
        listScroll.view = listH;
        listScroll.clamp();
    }

    /** A section of the sidebar with the modules of a side, in groups. */
    private int addSection(String name, Map<String, List<String>> groups, boolean client, int y) {
        side.add(new SideEntry(name, true, -1, y, SECTION_ROW));
        y += SECTION_ROW;
        Set<Integer> placed = new HashSet<>();
        for (Map.Entry<String, List<String>> group : groups.entrySet()) {
            List<Integer> members = new ArrayList<>();
            for (String id : group.getValue()) {
                int index = pageOf(id);
                if (index >= 0) members.add(index);
            }
            y = addGroup(group.getKey(), members, y);
            placed.addAll(members);
        }
        List<Integer> others = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            TweakModule.ModuleSide moduleSide = pages.get(i).module.getSide();
            boolean onSide = client ? moduleSide.onClient() : moduleSide.onServer();
            if (onSide && !placed.contains(i)) others.add(i);
        }
        return addGroup("Other", others, y) + 4;
    }

    private int addGroup(String name, List<Integer> members, int y) {
        if (members.isEmpty()) return y;
        side.add(new SideEntry(name, -1, y, GROUP_ROW));
        y += GROUP_ROW;
        for (int index : members) {
            side.add(new SideEntry(null, index, y, MODULE_ROW));
            y += MODULE_ROW;
        }
        return y + 2;
    }

    private int pageOf(String id) {
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).module.getId()
                .equals(id)) return i;
        }
        return -1;
    }

    String query() {
        return search == null ? ""
            : search.getText()
                .trim();
    }

    /** The rows of the selected page, or the settings of every page that match the search. */
    private void showRows() {
        commitAll();
        rows.clear();
        results = 0;
        String query = query();
        if (query.isEmpty()) {
            if (!pages.isEmpty()) {
                Page p = pages.get(page);
                rows.add(p.rows.get(0));
                if (!p.isOn()) {
                    rows.add(
                        new Rows.NoticeRow(
                            "The module is off: its settings do nothing",
                            "Turn on",
                            () -> toggleModule(p)));
                } else if (!p.isRunning()) {
                    rows.add(new Rows.NoticeRow("The module starts after a game restart", null, null));
                }
                boolean folded = false;
                for (int i = 1; i < p.rows.size(); i++) {
                    Rows.Row row = p.rows.get(i);
                    if (row instanceof Rows.HeaderRow) {
                        folded = isFolded(((Rows.HeaderRow) row).key);
                        rows.add(row);
                    } else if (!folded) rows.add(row);
                }
            }
        } else {
            String lower = query.toLowerCase(Locale.ROOT);
            for (int i = 0; i < pages.size(); i++) {
                Page p = pages.get(i);
                // the name of a module shows all its settings
                boolean all = p.name.toLowerCase(Locale.ROOT)
                    .contains(lower);
                boolean first = true;
                for (Rows.SettingRow row : p.settings) {
                    if (!all && !row.matches(query)) continue;
                    if (first) rows.add(new Rows.ResultsHeaderRow(this, i, p.name));
                    first = false;
                    rows.add(row);
                    results++;
                }
            }
        }
        contentScroll.content = 4;
        for (Rows.Row row : rows) contentScroll.content += row.height();
        contentScroll.view = contentH;
        contentScroll.clamp();
    }

    boolean isFolded(String key) {
        return FOLDED.contains(key);
    }

    void toggleFolded(String key) {
        if (!FOLDED.remove(key)) FOLDED.add(key);
        showRows();
    }

    /** Opens a module, from the sidebar or from the search results. */
    void openPage(int index) {
        page = index;
        lastPage = index;
        search.setText("");
        search.setFocused(false);
        contentScroll.reset();
        showRows();
        showInSidebar(index);
    }

    private void showInSidebar(int index) {
        for (SideEntry entry : side) {
            if (entry.page == index) listScroll.show(entry.y - GROUP_ROW, entry.y + entry.h + 2);
        }
    }

    private void toggleModule(Page p) {
        if (p.enabled == null) return;
        beforeChange(p.enabled);
        p.enabled.set(!p.enabled.getBoolean(false));
        afterChange(p.enabled);
        showRows();
    }

    // ---- drawing

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(100, (now - lastFrame) / 1_000_000F);
        lastFrame = now;
        if (dragging != null) {
            if (Mouse.isButtonDown(0)) dragging.drag(mouseX);
            else {
                dragging.release();
                dragging = null;
            }
        }
        dragBar(listScroll, listY, listH, mouseY);
        dragBar(contentScroll, contentY, contentH, mouseY);
        listScroll.update(dt);
        contentScroll.update(dt);

        GTNHTheme theme = UI.theme();
        if (mc.theWorld != null) drawDefaultBackground();
        else {
            UI.beginShapes();
            GLDraw.verticalGradient(0, 0, width, height, theme.backgroundTop, theme.backgroundBottom);
            UI.endShapes();
        }

        UI.panel(windowX, windowY, windowW, windowH);
        drawTitle(theme);
        drawModules(mouseX, mouseY, dt, theme);
        drawContent(mouseX, mouseY, dt, theme);
        drawFooter(mouseX, mouseY, theme);

        List<String> tooltip = dragging != null ? null : tooltip(mouseX, mouseY);
        if (tooltip != null) drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
    }

    private void dragBar(Scroll scroll, int y, int h, int mouseY) {
        if (!scroll.dragging) return;
        if (Mouse.isButtonDown(0)) scroll.drag(y + 2, h - 4, mouseY);
        else scroll.dragging = false;
    }

    private void drawTitle(GTNHTheme theme) {
        UI.text("GTNHTweaks", windowX + GAP + 2, windowY + 10, theme.logo);
        UI.text(
            Tags.VERSION,
            windowX + GAP + 8 + fontRendererObj.getStringWidth("GTNHTweaks"),
            windowY + 10,
            theme.textDisabled);

        int x = search.xPosition - 4, y = search.yPosition - 4, w = searchW + 8;
        UI.inset(x, y, w, 16);
        if (search.isFocused()) UI.rect(x, y + 15, w, 1, theme.barFill);
        String query = query();
        if (search.getText()
            .isEmpty() && !search.isFocused()) {
            UI.text("Search all settings", search.xPosition, search.yPosition, theme.textDisabled);
        } else if (!query.isEmpty()) {
            String count = results + (results == 1 ? " result" : " results");
            UI.text(
                count,
                x - 6 - fontRendererObj.getStringWidth(count),
                search.yPosition,
                results == 0 ? Rows.ERROR : theme.textDim);
        }
        search.setTextColor(theme.text & 0xFFFFFF);
        search.drawTextBox();
    }

    private void drawModules(int mouseX, int mouseY, float dt, GTNHTheme theme) {
        UI.inset(listX, listY, listW, listH);
        boolean searching = !query().isEmpty();
        boolean overList = UI.inside(mouseX, mouseY, listX, listY, listW, listH);
        int barW = listScroll.active() ? Scroll.BAR_WIDTH + 3 : 0;
        int w = listW - 4 - barW;
        UI.scissor(listX + 1, listY + 1, listW - 2, listH - 2);
        for (SideEntry entry : side) {
            int y = listY + entry.y - listScroll.offset();
            if (y + entry.h < listY || y > listY + listH) continue;
            if (entry.section) {
                // the title of the client or the server section, over its groups
                UI.rect(listX + 2, y + 2, w, SECTION_ROW - 4, 0x18FFFFFF);
                UI.text(entry.group.toUpperCase(Locale.ROOT), listX + 6, y + 5, theme.logo);
                continue;
            }
            if (entry.group != null) {
                String group = entry.group.toUpperCase(Locale.ROOT);
                UI.text(group, listX + 6, y + 4, theme.textDisabled);
                int lineX = listX + 12 + fontRendererObj.getStringWidth(group);
                UI.rect(
                    lineX,
                    y + 8,
                    listX + 2 + w - 4 - lineX,
                    1,
                    theme.isBeveled() ? theme.inset.topLeft : theme.barBorder);
                continue;
            }
            Page p = pages.get(entry.page);
            boolean selected = entry.page == page && !searching;
            boolean hovered = overList && UI.inside(mouseX, mouseY, listX + 2, y, w, MODULE_ROW);
            if (selected) UI.button(listX + 2, y, w, MODULE_ROW, true, true);
            else if (hovered) UI.rect(listX + 2, y, w, MODULE_ROW, 0x14FFFFFF);
            if (selected) UI.rect(listX + 3, y + 3, 2, MODULE_ROW - 6, theme.barFill);

            boolean on = p.isOn();
            int color = !on ? theme.textDisabled : selected ? theme.hoverText : theme.text;
            int nameW = w - 40;
            String name = fontRendererObj.trimStringToWidth(p.shortName, nameW);
            UI.text(name, listX + 9, y + (MODULE_ROW - 8) / 2, color);
            if (on != p.isRunning()) {
                // waits for a restart
                UI.text("*", listX + 9 + fontRendererObj.getStringWidth(name) + 2, y + 5, Rows.WARNING);
            }
            if (p.enabled != null) {
                p.switchPosition = UI.approach(p.switchPosition, on ? 1 : 0, dt, 120);
                int sx = listX + 2 + w - 24;
                boolean switchHovered = overList && UI.inside(mouseX, mouseY, sx - 2, y, 26, MODULE_ROW);
                UI.toggle(sx, y + (MODULE_ROW - 10) / 2, p.switchPosition, switchHovered);
            }
        }
        UI.endScissor();
        listScroll.drawBar(listX + listW - Scroll.BAR_WIDTH - 2, listY + 2, listH - 4, mouseX, mouseY);
    }

    private int rowWidth() {
        return contentW - (contentScroll.active() ? Scroll.BAR_WIDTH + 4 : 0);
    }

    private void drawContent(int mouseX, int mouseY, float dt, GTNHTheme theme) {
        UI.inset(contentX, contentY, contentW, contentH);
        boolean overContent = UI.inside(mouseX, mouseY, contentX, contentY, contentW, contentH);
        int mx = overContent && dragging == null ? mouseX : -1000, my = overContent ? mouseY : -1000;
        int w = rowWidth();

        UI.scissor(contentX + 1, contentY + 1, contentW - 2, contentH - 2);
        int y = contentY + 2 - contentScroll.offset();
        for (Rows.Row row : rows) {
            int h = row.height();
            if (y + h > contentY && y < contentY + contentH) row.draw(contentX, y, w, mx, my, dt);
            y += h;
        }
        if (rows.isEmpty()) {
            String text = "Nothing found for \"" + query() + "\"";
            UI.text(
                text,
                contentX + (contentW - fontRendererObj.getStringWidth(text)) / 2,
                contentY + contentH / 2 - 8,
                theme.textDisabled);
            String hint = "Right click the search field to clear it";
            UI.text(
                hint,
                contentX + (contentW - fontRendererObj.getStringWidth(hint)) / 2,
                contentY + contentH / 2 + 4,
                theme.textDisabled);
        }
        UI.endScissor();
        // a shade under the top edge when scrolled
        if (contentScroll.value > 1) {
            UI.beginShapes();
            GLDraw.verticalGradient(contentX + 1, contentY + 1, contentW - 2, 6, 0x50000000, 0x00000000);
            UI.endShapes();
        }
        contentScroll.drawBar(contentX + contentW - Scroll.BAR_WIDTH - 2, contentY + 2, contentH - 4, mouseX, mouseY);
    }

    private void drawFooter(int mouseX, int mouseY, GTNHTheme theme) {
        int y = windowY + windowH - FOOTER_HEIGHT + 6;
        UI.button(windowX + windowW - GAP - DONE_W, y, DONE_W, 18, "Done", mouseX, mouseY, true);
        int folderX = windowX + windowW - GAP * 2 - DONE_W - FOLDER_W;
        UI.button(folderX, y, FOLDER_W, 18, "Config folder", mouseX, mouseY, true);

        List<String> restart = restartNeeded();
        int textW = windowW - DONE_W - FOLDER_W - GAP * 4;
        if (!restart.isEmpty()) {
            String text = "* Restart the game to apply " + restart.size()
                + (restart.size() == 1 ? " change" : " changes");
            UI.text(fontRendererObj.trimStringToWidth(text, textW), windowX + GAP + 2, y + 5, Rows.WARNING);
        } else {
            String text = "Changes are saved at once  ·  Ctrl+F search  ·  ↑↓ modules";
            UI.text(fontRendererObj.trimStringToWidth(text, textW), windowX + GAP + 2, y + 5, theme.textDisabled);
        }
    }

    private List<String> tooltip(int mouseX, int mouseY) {
        int footerY = windowY + windowH - FOOTER_HEIGHT + 6;
        if (UI.inside(mouseX, mouseY, windowX, footerY, windowW - DONE_W - FOLDER_W - GAP * 3, 18)) {
            List<String> restart = restartNeeded();
            if (restart.isEmpty()) return null;
            List<String> lines = new ArrayList<>();
            lines.add("§6Needs a game restart");
            for (String name : restart) lines.add("§7- " + name);
            return lines;
        }
        if (UI.inside(mouseX, mouseY, listX, listY, listW, listH)) {
            SideEntry entry = sideAt(mouseX, mouseY);
            if (entry == null || entry.group != null) return null;
            Page p = pages.get(entry.page);
            List<String> lines = new ArrayList<>();
            lines.add("§e" + p.name + (p.isOn() ? " §a(on)" : " §8(off)"));
            lines.addAll(Rows.wrap(p.module.getDescription()));
            int changed = 0;
            for (Rows.SettingRow row : p.settings) if (!row.property.isDefault()) changed++;
            if (changed > 0) lines.add("§7" + changed + " of " + p.settings.size() + " settings changed");
            if (p.isOn() != p.isRunning()) {
                lines.add("§6* Turned " + (p.isOn() ? "on" : "off") + ", restart the game to apply");
            } else lines.add("§8The switch needs a game restart");
            return lines;
        }
        if (!UI.inside(mouseX, mouseY, contentX, contentY, contentW, contentH)) return null;
        int w = rowWidth();
        int y = contentY + 2 - contentScroll.offset();
        for (Rows.Row row : rows) {
            List<String> lines = row.tooltip(contentX, y, w, mouseX, mouseY);
            if (lines != null) return lines;
            y += row.height();
        }
        return null;
    }

    private SideEntry sideAt(int mouseX, int mouseY) {
        int barW = listScroll.active() ? Scroll.BAR_WIDTH + 3 : 0;
        for (SideEntry entry : side) {
            int y = listY + entry.y - listScroll.offset();
            if (UI.inside(mouseX, mouseY, listX + 2, y, listW - 4 - barW, entry.h)) return entry;
        }
        return null;
    }

    // ---- input

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        int sx = search.xPosition - 4, sy = search.yPosition - 4;
        boolean onSearch = UI.inside(mouseX, mouseY, sx, sy, searchW + 8, 16);
        search.mouseClicked(mouseX, mouseY, button);
        if (onSearch) {
            search.setFocused(true);
            if (button == 1) {
                search.setText("");
                showRows();
            }
            return;
        }

        // footer buttons
        int y = windowY + windowH - FOOTER_HEIGHT + 6;
        if (button == 0 && UI.inside(mouseX, mouseY, windowX + windowW - GAP - DONE_W, y, DONE_W, 18)) {
            UI.playClick();
            close();
            return;
        }
        if (button == 0
            && UI.inside(mouseX, mouseY, windowX + windowW - GAP * 2 - DONE_W - FOLDER_W, y, FOLDER_W, 18)) {
            UI.playClick();
            UI.open(ModuleManager.getConfigDir());
            return;
        }

        // modules
        if (UI.inside(mouseX, mouseY, listX, listY, listW, listH)) {
            if (listScroll.press(listX + listW - Scroll.BAR_WIDTH - 2, listY + 2, listH - 4, mouseX, mouseY)) return;
            SideEntry entry = sideAt(mouseX, mouseY);
            if (entry == null || entry.group != null) return;
            Page p = pages.get(entry.page);
            int barW = listScroll.active() ? Scroll.BAR_WIDTH + 3 : 0;
            if (p.enabled != null && mouseX >= listX + listW - barW - 30) toggleModule(p);
            else openPage(entry.page);
            UI.playClick();
            return;
        }

        // scrollbar
        int barX = contentX + contentW - Scroll.BAR_WIDTH - 2;
        if (contentScroll.press(barX, contentY + 2, contentH - 4, mouseX, mouseY)) return;

        // settings: every row sees the click, so text fields lose the focus
        boolean inside = UI.inside(mouseX, mouseY, contentX, contentY, contentW, contentH);
        int w = rowWidth();
        int rowY = contentY + 2 - contentScroll.offset();
        boolean used = false;
        for (Rows.Row row : new ArrayList<>(rows)) {
            boolean hit = !used && inside;
            if (row.click(contentX, rowY, w, hit ? mouseX : -1000, hit ? mouseY : -1000, button)) used = true;
            rowY += row.height();
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
        else contentScroll.wheel(Integer.signum(wheel));
    }

    @Override
    protected void keyTyped(char c, int code) {
        if (search.isFocused()) {
            if (code == Keyboard.KEY_ESCAPE || code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) {
                if (code == Keyboard.KEY_ESCAPE && !search.getText()
                    .isEmpty()) {
                    search.setText("");
                    showRows();
                }
                search.setFocused(false);
                return;
            }
            String before = search.getText();
            search.textboxKeyTyped(c, code);
            if (!before.equals(search.getText())) {
                contentScroll.reset();
                showRows();
            }
            return;
        }
        for (Rows.Row row : rows) {
            if (row.key(c, code)) return;
        }
        if (code == Keyboard.KEY_ESCAPE) {
            if (!query().isEmpty()) {
                search.setText("");
                showRows();
            } else close();
            return;
        }
        if (code == Keyboard.KEY_F && isCtrlKeyDown()) {
            search.setFocused(true);
            return;
        }
        if (query().isEmpty() && (code == Keyboard.KEY_UP || code == Keyboard.KEY_DOWN)) {
            int next = sidebarNeighbor(code == Keyboard.KEY_DOWN ? 1 : -1);
            if (next >= 0) openPage(next);
            return;
        }
        if (code == Keyboard.KEY_PRIOR || code == Keyboard.KEY_NEXT) {
            contentScroll.wheel(code == Keyboard.KEY_PRIOR ? 6 : -6);
            return;
        }
        if (code == Keyboard.KEY_HOME) contentScroll.target = 0;
        else if (code == Keyboard.KEY_END) contentScroll.target = contentScroll.max();
        else if (c >= ' ' && c != 127 && !isCtrlKeyDown()) {
            // typing anywhere starts a search
            search.setFocused(true);
            search.textboxKeyTyped(c, code);
            contentScroll.reset();
            showRows();
        }
    }

    /** The page before or after the selected one, in the order of the sidebar. */
    private int sidebarNeighbor(int direction) {
        List<Integer> order = new ArrayList<>();
        for (SideEntry entry : side) if (entry.page >= 0) order.add(entry.page);
        int at = order.indexOf(page);
        int next = at + direction;
        return at < 0 || next < 0 || next >= order.size() ? -1 : order.get(next);
    }

    @Override
    public void updateScreen() {
        search.updateCursorCounter();
    }

    // ---- changes

    void beforeChange(Property property) {
        if (!initialValues.containsKey(property)) initialValues.put(property, valueOf(property));
    }

    void afterChange(Property property) {
        ModuleManager.saveAndReload();
    }

    void startDrag(Rows.Control control) {
        dragging = control;
    }

    boolean isDragging(Rows.Control control) {
        return dragging == control;
    }

    private static String valueOf(Property property) {
        return property.isList() ? Arrays.toString(property.getStringList()) : property.getString();
    }

    /** Names of the changed settings that only apply after a restart. */
    private List<String> restartNeeded() {
        List<String> names = new ArrayList<>();
        for (Map.Entry<Property, String> entry : initialValues.entrySet()) {
            Property property = entry.getKey();
            if (property.requiresMcRestart() && !valueOf(property).equals(entry.getValue())) {
                names.add(nameOf(property));
            }
        }
        return names;
    }

    /** "Module: setting", or the module alone for its switch. */
    private String nameOf(Property property) {
        for (Page p : pages) {
            if (p.enabled == property) return p.name + " module " + (property.getBoolean(false) ? "on" : "off");
            for (Rows.SettingRow row : p.settings) {
                if (row.property == property) return p.name + ": " + row.label;
            }
        }
        return UI.humanize(property.getName());
    }

    void openList(Property property) {
        commitAll();
        mc.displayGuiScreen(new ListEditScreen(this, property));
    }

    private void commitAll() {
        for (Page p : pages) {
            for (Rows.Row row : p.rows) row.commit();
        }
    }

    private void close() {
        mc.displayGuiScreen(parent);
    }

    @Override
    public void onGuiClosed() {
        if (dragging != null) {
            dragging.release();
            dragging = null;
        }
        commitAll();
        ModuleManager.saveAndReload();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
