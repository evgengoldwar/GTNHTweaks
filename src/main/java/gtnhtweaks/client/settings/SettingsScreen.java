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
 * Settings of GTNHTweaks. At the left the modules, in groups, with their on/off switches. At the right the selected
 * module: its name, what it does and its switch, its settings in tabs, one per category of its config, and the
 * settings of the open tab, each with a line of what it does. A search goes through every setting. Changes are saved
 * and applied at once; the ones that need a restart are tagged, and counted at the bottom.
 */
public class SettingsScreen extends GuiScreen {

    private static final int MAX_WIDTH = 720;
    private static final int MAX_HEIGHT = 440;
    private static final int SIDEBAR_WIDTH = 158;
    private static final int TITLE_HEIGHT = 26;
    private static final int FOOTER_HEIGHT = 28;
    private static final int MODULE_ROW = 18;
    private static final int GROUP_ROW = 14;
    private static final int SECTION_ROW = 18;
    private static final int TAB_H = 16;
    private static final int GAP = 6;
    private static final int DONE_W = 70, FOLDER_W = 108;
    private static final int SWITCH_W = 54;
    private static final int RESET_W = 64;

    /**
     * Groups of the two sections of the sidebar, in order, with the ids of their modules. Modules in none go to
     * "Other" of the section of their side; the modules of both sides are in both sections.
     */
    private static final Map<String, List<String>> CLIENT_GROUPS = new LinkedHashMap<>();
    private static final Map<String, List<String>> SERVER_GROUPS = new LinkedHashMap<>();
    private static final String CLIENT_SECTION = "Client";
    private static final String SERVER_SECTION = "Server";

    static {
        CLIENT_GROUPS.put("Look", Arrays.asList("loadingscreen", "mainmenu", "menus"));
        CLIENT_GROUPS.put("Gameplay", Arrays.asList("minecraft", "itemphysics"));
        CLIENT_GROUPS
            .put("Mods", Arrays.asList("gregtech", "nei", "chromatictooltips", "tinkers", "extrautilities", "bees"));
        CLIENT_GROUPS.put("Tools", Arrays.asList("logs", "backups", "debug"));
        SERVER_GROUPS.put("Gameplay", Arrays.asList("minecraftServer"));
        SERVER_GROUPS.put("Tools", Arrays.asList("logs", "backupsServer"));
    }

    /** Page shown when the screen opens again, and the open tab of every module, kept while the game runs. */
    private static int lastPage;
    private static final Map<String, Integer> LAST_TAB = new HashMap<>();

    private final GuiScreen parent;
    private final List<Page> pages = new ArrayList<>();
    /** Sidebar lines: a section, a group or a page. */
    private final List<SideEntry> side = new ArrayList<>();
    private final List<Rows.Row> rows = new ArrayList<>();
    private final List<TabBox> tabBoxes = new ArrayList<>();
    private final List<String> moduleInfo = new ArrayList<>();
    private final List<String> tabInfo = new ArrayList<>();
    /** Values before the first change in this screen, to tell which changes need a restart. */
    private final Map<Property, String> initialValues = new IdentityHashMap<>();
    private final Map<Property, Rows.SettingRow> settingRows = new IdentityHashMap<>();
    private GuiTextField search;
    private int searchW;
    private int page;
    private int results;
    /** Time of the first click on Reset all; a second click soon after resets. */
    private long confirmResetUntil;

    private int windowX, windowY, windowW, windowH;
    private int listX, listY, listW, listH;
    private int contentX, contentY, contentW, contentH;
    /** Top of the scrolled settings, under the header and the tabs of the module. */
    private int rowsY, rowsH;
    private int tabsY, tabInfoY;

    private final Scroll listScroll = new Scroll();
    private final Scroll contentScroll = new Scroll();
    private Rows.Control dragging;
    private long lastFrame;

    public SettingsScreen(GuiScreen parent) {
        this.parent = parent;
        this.page = lastPage;
    }

    /** A module with its tabs. */
    private static final class Page {

        final TweakModule module;
        /** The name with the side, as in the search results; the sidebar shows the short one under its section. */
        final String name;
        final String shortName;
        final String info;
        final Property enabled;
        final List<Tab> tabs = new ArrayList<>();
        final List<Rows.SettingRow> settings = new ArrayList<>();
        float switchPosition;
        float headerSwitch;

        Page(TweakModule module, String name, String shortName, String info, Property enabled) {
            this.module = module;
            this.name = name;
            this.shortName = shortName;
            this.info = info;
            this.enabled = enabled;
            this.switchPosition = this.headerSwitch = isOn() ? 1 : 0;
        }

        boolean isOn() {
            return enabled == null || enabled.getBoolean(false);
        }

        /** Whether the module runs now, which can differ from the switch until a restart. */
        boolean isRunning() {
            return module.isEnabled();
        }

        int changed() {
            int count = 0;
            for (Rows.SettingRow row : settings) if (!row.property.isDefault()) count++;
            return count;
        }
    }

    /** A category of the config of a module, shown as a tab. */
    private static final class Tab {

        final String name, info;
        final List<Rows.Row> rows = new ArrayList<>();
        final List<Rows.SettingRow> settings = new ArrayList<>();

        Tab(String name, String info) {
            this.name = name;
            this.info = info;
        }

        int changed() {
            int count = 0;
            for (Rows.SettingRow row : settings) if (!row.property.isDefault()) count++;
            return count;
        }
    }

    /** Where a tab is drawn, to find it on a click. */
    private static final class TabBox {

        final int tab, x, y, w;

        TabBox(int tab, int x, int y, int w) {
            this.tab = tab;
            this.x = x;
            this.y = y;
            this.w = w;
        }
    }

    private static final class SideEntry {

        final String group;
        /** The line is the title of the client or the server section, not of a group. */
        final boolean section;
        final int page;
        final int y, h;

        SideEntry(String group, boolean section, int page, int y, int h) {
            this.group = group;
            this.section = section;
            this.page = page;
            this.y = y;
            this.h = h;
        }
    }

    public static String displayName(TweakModule module) {
        SettingsText.Module text = SettingsText.module(module.getId());
        return text != null ? text.name : UI.humanize(module.getId());
    }

    /** The name with the side for the server modules, whose names can be the same as of their client modules. */
    static String fullName(TweakModule module) {
        String name = displayName(module);
        return module.getSide() == TweakModule.ModuleSide.SERVER ? name + " (server)" : name;
    }

    private static String sideText(TweakModule.ModuleSide moduleSide) {
        return moduleSide == TweakModule.ModuleSide.CLIENT ? "client"
            : moduleSide == TweakModule.ModuleSide.SERVER ? "server" : "client and server";
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
        settingRows.clear();
        int controlWidth = Math.max(80, Math.min(130, contentW / 2 - 70));
        Configuration modules = ModuleManager.getModulesConfig();
        for (TweakModule module : ModuleManager.getModules()) {
            String id = module.getId();
            Property enabled = modules.hasCategory("modules") ? modules.getCategory("modules")
                .get(id) : null;
            SettingsText.Module text = SettingsText.module(id);
            String info = text != null ? text.info : module.getDescription();
            Page p = new Page(module, fullName(module), displayName(module), info == null ? "" : info, enabled);
            Configuration config = ModuleManager.getConfig(module);
            List<String> categories = new ArrayList<>(config.getCategoryNames());
            // the general settings first, then as listed
            if (categories.remove("general")) categories.add(0, "general");
            for (String name : SettingsText.orderTabs(id, categories)) {
                ConfigCategory category = config.getCategory(name);
                if (category.isChild() || category.getValues()
                    .isEmpty()) continue;
                SettingsText.Tab tabText = SettingsText.tab(id, name);
                String comment = category.getComment();
                Tab tab = new Tab(
                    tabText != null ? tabText.name : UI.humanize(name),
                    tabText != null ? tabText.info : comment == null ? "" : comment.trim());
                String button = module.settingsButton(name);
                if (button != null) {
                    String category0 = name;
                    tab.rows.add(new Rows.ButtonRow(button, () -> {
                        commitAll();
                        module.openSettingsButton(category0, this);
                    }));
                }
                Map<String, Property> values = category.getValues();
                List<String> keys = SettingsText.orderSettings(id, name, new ArrayList<>(values.keySet()));
                String file = id + ".cfg › " + name;
                Rows.SettingRow master = null;
                for (String key : keys) {
                    Property property = values.get(key);
                    if (!module.showInSettings(name, key)) continue;
                    // a key no module reads any more, left in an old file, has no comment: Forge sets it when read
                    if (property.comment == null && SettingsText.setting(id, name, key) == null) continue;
                    Rows.SettingRow row = new Rows.SettingRow(
                        this,
                        p.name + " › " + tab.name,
                        file,
                        property,
                        SettingsText.setting(id, name, key),
                        controlWidth);
                    if ("enabled".equals(key) && property.getType() == Property.Type.BOOLEAN) master = row;
                    tab.settings.add(row);
                    tab.rows.add(row);
                    p.settings.add(row);
                    settingRows.put(property, row);
                }
                // the main switch of the tab rules its other settings
                if (master != null && tab.settings.size() > 1) {
                    for (Rows.SettingRow row : tab.settings) {
                        if (row == master) continue;
                        row.master = master.property;
                        row.masterName = master.label;
                    }
                }
                if (!tab.settings.isEmpty() || !tab.rows.isEmpty()) p.tabs.add(tab);
            }
            pages.add(p);
        }
    }

    private void buildSidebar() {
        side.clear();
        int y = 2;
        y = addSection(CLIENT_SECTION, CLIENT_GROUPS, true, y);
        y = addSection(SERVER_SECTION, SERVER_GROUPS, false, y);
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
        side.add(new SideEntry(name, false, -1, y, GROUP_ROW));
        y += GROUP_ROW;
        for (int index : members) {
            side.add(new SideEntry(null, false, index, y, MODULE_ROW));
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

    private boolean searching() {
        return !query().isEmpty();
    }

    private int tabOf(Page p) {
        Integer tab = LAST_TAB.get(p.module.getId());
        return tab == null ? 0 : Math.max(0, Math.min(tab, p.tabs.size() - 1));
    }

    /** Places the header of the module, its tabs and the line of the open tab; the settings go under them. */
    @SuppressWarnings("unchecked")
    private void layoutContent() {
        tabBoxes.clear();
        moduleInfo.clear();
        tabInfo.clear();
        if (searching() || pages.isEmpty()) {
            rowsY = contentY;
            rowsH = contentH;
            return;
        }
        Page p = pages.get(page);
        int textW = contentW - 20 - SWITCH_W - RESET_W - 12;
        if (!p.info.isEmpty()) {
            List<String> lines = fontRendererObj.listFormattedStringToWidth(p.info, textW);
            moduleInfo.addAll(lines.subList(0, Math.min(2, lines.size())));
        }
        int y = contentY + 8 + 12 + moduleInfo.size() * 10 + 6;
        tabsY = y;
        if (p.tabs.size() > 1) {
            int x = contentX + 8;
            for (int i = 0; i < p.tabs.size(); i++) {
                int w = fontRendererObj.getStringWidth(p.tabs.get(i).name) + 16;
                if (x + w > contentX + contentW - 8 && x > contentX + 8) {
                    x = contentX + 8;
                    y += TAB_H + 3;
                }
                tabBoxes.add(new TabBox(i, x, y, w));
                x += w + 3;
            }
            y += TAB_H + 6;
        }
        tabInfoY = y;
        if (!p.tabs.isEmpty()) {
            String info = p.tabs.get(tabOf(p)).info;
            if (!info.isEmpty()) {
                List<String> lines = fontRendererObj.listFormattedStringToWidth(info, contentW - 20);
                tabInfo.addAll(lines.subList(0, Math.min(3, lines.size())));
                y += tabInfo.size() * 10 + 4;
            }
        }
        rowsY = y + 2;
        rowsH = contentY + contentH - rowsY;
    }

    /** The settings of the open tab, or the settings of every module that match the search. */
    private void showRows() {
        commitAll();
        rows.clear();
        results = 0;
        layoutContent();
        String query = query();
        if (query.isEmpty()) {
            if (!pages.isEmpty()) {
                Page p = pages.get(page);
                if (!p.isOn()) {
                    rows.add(
                        new Rows.NoticeRow(
                            "This module is off: its settings do nothing",
                            "Turn on",
                            () -> toggleModule(p)));
                } else if (!p.isRunning()) {
                    rows.add(new Rows.NoticeRow("The module starts after a game restart", null, null));
                }
                if (!p.tabs.isEmpty()) rows.addAll(p.tabs.get(tabOf(p)).rows);
            }
        } else {
            String lower = query.toLowerCase(Locale.ROOT);
            for (int i = 0; i < pages.size(); i++) {
                Page p = pages.get(i);
                // the name of a module shows all its settings, the name of a tab all of the tab
                boolean all = p.name.toLowerCase(Locale.ROOT)
                    .contains(lower);
                for (int t = 0; t < p.tabs.size(); t++) {
                    Tab tab = p.tabs.get(t);
                    boolean wholeTab = all || tab.name.toLowerCase(Locale.ROOT)
                        .contains(lower);
                    boolean first = true;
                    for (Rows.SettingRow row : tab.settings) {
                        if (!wholeTab && !row.matches(query)) continue;
                        if (first) {
                            String title = p.tabs.size() > 1 ? p.name + "  ›  " + tab.name : p.name;
                            rows.add(new Rows.ResultsHeaderRow(this, i, t, title));
                        }
                        first = false;
                        rows.add(row);
                        results++;
                    }
                }
            }
        }
        contentScroll.content = 4;
        for (Rows.Row row : rows) contentScroll.content += row.height();
        contentScroll.view = rowsH;
        contentScroll.clamp();
    }

    /** Opens a module, from the sidebar, at its tab of last time. */
    void openPage(int index) {
        openPage(index, -1);
    }

    /** Opens a tab of a module, or the tab of last time for -1, from the sidebar or the search results. */
    void openPage(int index, int tab) {
        page = index;
        lastPage = index;
        if (tab >= 0) LAST_TAB.put(pages.get(index).module.getId(), tab);
        search.setText("");
        search.setFocused(false);
        contentScroll.reset();
        confirmResetUntil = 0;
        showRows();
        showInSidebar(index);
    }

    private void openTab(int tab) {
        Page p = pages.get(page);
        if (tab < 0 || tab >= p.tabs.size() || tab == tabOf(p)) return;
        LAST_TAB.put(p.module.getId(), tab);
        contentScroll.reset();
        showRows();
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
        dragBar(contentScroll, rowsY, rowsH, mouseY);
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
        UI.inset(contentX, contentY, contentW, contentH);
        if (!searching() && !pages.isEmpty()) drawHeader(mouseX, mouseY, dt, theme);
        drawRows(mouseX, mouseY, dt, theme);
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
            UI.text("Search all settings...", search.xPosition, search.yPosition, theme.textDisabled);
        } else if (!query.isEmpty()) {
            String count = results + (results == 1 ? " setting found" : " settings found");
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
        boolean searching = searching();
        boolean overList = UI.inside(mouseX, mouseY, listX, listY, listW, listH);
        int barW = listScroll.active() ? Scroll.BAR_WIDTH + 3 : 0;
        int w = listW - 4 - barW;
        UI.scissor(listX + 1, listY + 1, listW - 2, listH - 2);
        for (SideEntry entry : side) {
            int y = listY + entry.y - listScroll.offset();
            if (y + entry.h < listY || y > listY + listH) continue;
            if (entry.section) {
                // the title of a section, over its groups
                UI.rect(listX + 2, y + 2, w, SECTION_ROW - 4, 0x18FFFFFF);
                UI.text(entry.group.toUpperCase(Locale.ROOT), listX + 6, y + 5, theme.logo);
                continue;
            }
            if (entry.group != null) {
                String group = entry.group;
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
            String name = fontRendererObj.trimStringToWidth(p.shortName, w - 40);
            UI.text(name, listX + 9, y + (MODULE_ROW - 8) / 2, color);
            if (on != p.isRunning()) {
                // waits for a restart
                UI.text("⚠", listX + 9 + fontRendererObj.getStringWidth(name) + 3, y + 5, Rows.RESTART);
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

    private int switchX() {
        return contentX + contentW - 8 - SWITCH_W;
    }

    private int resetX() {
        return switchX() - 6 - RESET_W;
    }

    private boolean confirmingReset() {
        return System.currentTimeMillis() < confirmResetUntil;
    }

    /** The name of the module, what it does, its switch, Reset all and the tabs. */
    private void drawHeader(int mouseX, int mouseY, float dt, GTNHTheme theme) {
        Page p = pages.get(page);
        int x = contentX + 10, y = contentY + 8;
        UI.text(p.shortName, x, y, theme.logo);
        UI.text(
            sideText(p.module.getSide()),
            x + fontRendererObj.getStringWidth(p.shortName) + 6,
            y,
            theme.textDisabled);
        for (int i = 0; i < moduleInfo.size(); i++) UI.text(moduleInfo.get(i), x, y + 13 + i * 10, theme.textDim);

        // the switch of the module, with its state in words
        if (p.enabled != null) {
            boolean on = p.isOn();
            p.headerSwitch = UI.approach(p.headerSwitch, on ? 1 : 0, dt, 120);
            int sx = switchX();
            boolean hovered = UI.inside(mouseX, mouseY, sx, contentY + 4, SWITCH_W, 18);
            String state = on ? "On" : "Off";
            UI.text(
                state,
                sx + SWITCH_W - 24 - 6 - fontRendererObj.getStringWidth(state),
                contentY + 9,
                on ? UI.ON : theme.textDim);
            UI.toggle(sx + SWITCH_W - 22, contentY + 8, p.headerSwitch, hovered);
        }
        int changed = p.changed();
        if (changed > 0 || confirmingReset()) {
            String label = confirmingReset() ? "Sure?" : "Reset all";
            UI.button(resetX(), contentY + 5, RESET_W, 16, label, mouseX, mouseY, true);
        }

        // the tabs, the open one lit and underlined, a dot on the ones with changed settings
        int open = tabOf(p);
        for (TabBox box : tabBoxes) {
            Tab tab = p.tabs.get(box.tab);
            boolean on = box.tab == open;
            boolean hovered = !on && UI.inside(mouseX, mouseY, box.x, box.y, box.w, TAB_H);
            UI.button(box.x, box.y, box.w, TAB_H, on || hovered, true);
            if (on) UI.rect(box.x + 3, box.y + TAB_H - 3, box.w - 6, 1, theme.barFill);
            UI.text(tab.name, box.x + 8, box.y + 4, on ? theme.hoverText : hovered ? theme.text : theme.textDim);
            if (tab.changed() > 0) UI.rect(box.x + box.w - 5, box.y + 3, 2, 2, theme.barFill);
        }
        for (int i = 0; i < tabInfo.size(); i++) UI.text(tabInfo.get(i), x, tabInfoY + i * 10, theme.textDim);
        UI.rect(contentX + 6, rowsY - 2, contentW - 12, 1, theme.isBeveled() ? theme.inset.topLeft : theme.barBorder);
    }

    private int rowWidth() {
        return contentW - (contentScroll.active() ? Scroll.BAR_WIDTH + 4 : 0);
    }

    private void drawRows(int mouseX, int mouseY, float dt, GTNHTheme theme) {
        boolean overRows = UI.inside(mouseX, mouseY, contentX, rowsY, contentW, rowsH);
        int mx = overRows && dragging == null ? mouseX : -1000, my = overRows ? mouseY : -1000;
        int w = rowWidth();

        UI.scissor(contentX + 1, rowsY, contentW - 2, rowsH - 1);
        int y = rowsY + 2 - contentScroll.offset();
        for (Rows.Row row : rows) {
            int h = row.height();
            if (y + h > rowsY && y < rowsY + rowsH) row.draw(contentX, y, w, mx, my, dt);
            y += h;
        }
        if (rows.isEmpty()) {
            String text = searching() ? "Nothing found for \"" + query() + "\"" : "This module has no settings";
            UI.text(
                text,
                contentX + (contentW - fontRendererObj.getStringWidth(text)) / 2,
                rowsY + rowsH / 2 - 8,
                theme.textDisabled);
            if (searching()) {
                String hint = "Right click the search field to clear it";
                UI.text(
                    hint,
                    contentX + (contentW - fontRendererObj.getStringWidth(hint)) / 2,
                    rowsY + rowsH / 2 + 4,
                    theme.textDisabled);
            }
        }
        UI.endScissor();
        // a shade under the top edge when scrolled
        if (contentScroll.value > 1) {
            UI.beginShapes();
            GLDraw.verticalGradient(contentX + 1, rowsY, contentW - 2, 6, 0x50000000, 0x00000000);
            UI.endShapes();
        }
        contentScroll.drawBar(contentX + contentW - Scroll.BAR_WIDTH - 2, rowsY + 2, rowsH - 4, mouseX, mouseY);
    }

    private void drawFooter(int mouseX, int mouseY, GTNHTheme theme) {
        int y = windowY + windowH - FOOTER_HEIGHT + 6;
        UI.button(windowX + windowW - GAP - DONE_W, y, DONE_W, 18, "Done", mouseX, mouseY, true);
        int folderX = windowX + windowW - GAP * 2 - DONE_W - FOLDER_W;
        UI.button(folderX, y, FOLDER_W, 18, "Open config folder", mouseX, mouseY, true);

        List<String> restart = restartNeeded();
        int textW = windowW - DONE_W - FOLDER_W - GAP * 4;
        if (!restart.isEmpty()) {
            String text = "⚠ Restart the game to apply " + restart.size()
                + (restart.size() == 1 ? " change" : " changes");
            UI.text(fontRendererObj.trimStringToWidth(text, textW), windowX + GAP + 2, y + 5, Rows.RESTART);
        } else {
            String text = "Changes are saved at once  ·  Ctrl+F: search  ·  Tab: next tab";
            UI.text(fontRendererObj.trimStringToWidth(text, textW), windowX + GAP + 2, y + 5, theme.textDisabled);
        }
    }

    // ---- tooltips

    private List<String> tooltip(int mouseX, int mouseY) {
        int footerY = windowY + windowH - FOOTER_HEIGHT + 6;
        if (UI.inside(mouseX, mouseY, windowX, footerY, windowW - DONE_W - FOLDER_W - GAP * 3, 18)) {
            List<String> restart = restartNeeded();
            if (restart.isEmpty()) return null;
            List<String> lines = new ArrayList<>();
            lines.add("§6⚠ Requires a game restart");
            lines.add("§7These changes apply after you restart the game:");
            for (String name : restart) lines.add("§7- " + name);
            return lines;
        }
        if (UI.inside(mouseX, mouseY, listX, listY, listW, listH)) {
            SideEntry entry = sideAt(mouseX, mouseY);
            if (entry == null) return null;
            if (entry.section) {
                List<String> lines = new ArrayList<>();
                lines.add("§e" + entry.group);
                lines.addAll(
                    Rows.wrap(
                        CLIENT_SECTION.equals(entry.group) ? "Modules that run in your Minecraft."
                            : "Modules that run where the world runs: on a server, or in your singleplayer world."));
                return lines;
            }
            if (entry.group != null) return null;
            return moduleTooltip(pages.get(entry.page));
        }
        if (UI.inside(mouseX, mouseY, contentX, contentY, contentW, rowsY - contentY) && !searching()
            && !pages.isEmpty()) {
            return headerTooltip(pages.get(page), mouseX, mouseY);
        }
        if (!UI.inside(mouseX, mouseY, contentX, rowsY, contentW, rowsH)) return null;
        int w = rowWidth();
        int y = rowsY + 2 - contentScroll.offset();
        for (Rows.Row row : rows) {
            List<String> lines = row.tooltip(contentX, y, w, mouseX, mouseY);
            if (lines != null) return lines;
            y += row.height();
        }
        return null;
    }

    /** What the module does, where it runs, how much of it is changed and what its switch needs. */
    private List<String> moduleTooltip(Page p) {
        List<String> lines = new ArrayList<>();
        lines.add("§e" + p.name + (p.isOn() ? "  §a● On" : "  §8● Off"));
        if (!p.info.isEmpty()) lines.addAll(Rows.wrap(p.info));
        lines.add("");
        lines.add("§7Runs in: §f" + sideText(p.module.getSide()));
        int changed = p.changed();
        if (changed > 0) lines.add("§7Changed: §f" + changed + " of " + p.settings.size() + " settings");
        if (p.isOn() != p.isRunning()) {
            lines.add("§6⚠ Turned " + (p.isOn() ? "on" : "off") + ": restart the game to apply");
        } else if (p.enabled != null) lines.add("§6⚠ Turning the module on or off requires a game restart");
        return lines;
    }

    private List<String> headerTooltip(Page p, int mouseX, int mouseY) {
        if (p.enabled != null && UI.inside(mouseX, mouseY, switchX(), contentY + 4, SWITCH_W, 18)) {
            return moduleTooltip(p);
        }
        if ((p.changed() > 0 || confirmingReset()) && UI.inside(mouseX, mouseY, resetX(), contentY + 5, RESET_W, 16)) {
            List<String> lines = new ArrayList<>();
            lines.add("§eReset all");
            lines.add("§7Puts every setting of " + p.shortName + " back to its default:");
            for (Rows.SettingRow row : p.settings) if (!row.property.isDefault()) lines.add("§8- " + row.label);
            lines.add("");
            lines.add(confirmingReset() ? "§6Click again to reset" : "§8Click twice to reset");
            return lines;
        }
        for (TabBox box : tabBoxes) {
            if (!UI.inside(mouseX, mouseY, box.x, box.y, box.w, TAB_H)) continue;
            Tab tab = p.tabs.get(box.tab);
            List<String> lines = new ArrayList<>();
            lines.add("§e" + tab.name);
            if (!tab.info.isEmpty()) lines.addAll(Rows.wrap(tab.info));
            int changed = tab.changed();
            lines.add("");
            lines.add(
                "§7" + tab.settings.size()
                    + (tab.settings.size() == 1 ? " setting" : " settings")
                    + (changed > 0 ? ", " + changed + " changed" : ""));
            return lines;
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
            if (entry == null || entry.page < 0) return;
            Page p = pages.get(entry.page);
            int barW = listScroll.active() ? Scroll.BAR_WIDTH + 3 : 0;
            if (p.enabled != null && mouseX >= listX + listW - barW - 30) toggleModule(p);
            else openPage(entry.page);
            UI.playClick();
            return;
        }

        // the header: switch, Reset all and tabs
        if (!searching() && !pages.isEmpty()
            && UI.inside(mouseX, mouseY, contentX, contentY, contentW, rowsY - contentY)) {
            if (button == 0) headerClicked(pages.get(page), mouseX, mouseY);
            return;
        }

        // scrollbar
        int barX = contentX + contentW - Scroll.BAR_WIDTH - 2;
        if (contentScroll.press(barX, rowsY + 2, rowsH - 4, mouseX, mouseY)) return;

        // settings: every row sees the click, so text fields lose the focus
        boolean inside = UI.inside(mouseX, mouseY, contentX, rowsY, contentW, rowsH);
        int w = rowWidth();
        int rowY = rowsY + 2 - contentScroll.offset();
        boolean used = false;
        for (Rows.Row row : new ArrayList<>(rows)) {
            boolean hit = !used && inside;
            if (row.click(contentX, rowY, w, hit ? mouseX : -1000, hit ? mouseY : -1000, button)) used = true;
            rowY += row.height();
        }
    }

    private void headerClicked(Page p, int mouseX, int mouseY) {
        if (p.enabled != null && UI.inside(mouseX, mouseY, switchX(), contentY + 4, SWITCH_W, 18)) {
            UI.playClick();
            toggleModule(p);
            return;
        }
        if (p.changed() > 0 && UI.inside(mouseX, mouseY, resetX(), contentY + 5, RESET_W, 16)) {
            UI.playClick();
            if (!confirmingReset()) {
                confirmResetUntil = System.currentTimeMillis() + 3000;
                return;
            }
            confirmResetUntil = 0;
            for (Rows.SettingRow row : p.settings) row.reset(false);
            afterChange(null);
            return;
        }
        for (TabBox box : tabBoxes) {
            if (!UI.inside(mouseX, mouseY, box.x, box.y, box.w, TAB_H)) continue;
            if (box.tab != tabOf(p)) UI.playClick();
            openTab(box.tab);
            return;
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
            if (searching()) {
                search.setText("");
                showRows();
            } else close();
            return;
        }
        if (code == Keyboard.KEY_F && isCtrlKeyDown()) {
            search.setFocused(true);
            return;
        }
        if (code == Keyboard.KEY_TAB && !searching() && !pages.isEmpty()) {
            // the next tab, or the previous one with Shift
            Page p = pages.get(page);
            if (p.tabs.size() > 1) {
                int step = isShiftKeyDown() ? p.tabs.size() - 1 : 1;
                openTab((tabOf(p) + step) % p.tabs.size());
            }
            return;
        }
        if (!searching() && (code == Keyboard.KEY_UP || code == Keyboard.KEY_DOWN)) {
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
            String now = valueOf(property);
            if (now.equals(entry.getValue())) continue;
            Rows.SettingRow row = settingRows.get(property);
            SettingsText.Restart restart = row == null ? SettingsText.Restart.DEFAULT : row.restart;
            boolean needed;
            switch (restart) {
                case NONE:
                    needed = false;
                    break;
                case ALWAYS:
                    needed = true;
                    break;
                case TURN_ON:
                    needed = "true".equals(now) && "false".equals(entry.getValue());
                    break;
                default:
                    needed = property.requiresMcRestart();
            }
            if (needed) names.add(nameOf(property));
        }
        return names;
    }

    /** "Module › Tab: setting", or the module alone for its switch. */
    private String nameOf(Property property) {
        for (Page p : pages) {
            if (p.enabled == property) return p.name + " turned " + (property.getBoolean(false) ? "on" : "off");
        }
        Rows.SettingRow row = settingRows.get(property);
        return row != null ? row.where + ": " + row.label : UI.humanize(property.getName());
    }

    void openList(Property property) {
        commitAll();
        mc.displayGuiScreen(new ListEditScreen(this, property));
    }

    private void commitAll() {
        for (Page p : pages) {
            for (Tab tab : p.tabs) {
                for (Rows.Row row : tab.rows) row.commit();
            }
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
