package gtnhtweaks.client.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

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
 * Settings of GTNHTweaks: the modules on the left with their on/off switches, the settings of the selected module on
 * the right, a search over every setting. Changes are saved and applied at once; the ones that need a restart are
 * listed at the bottom.
 */
public class SettingsScreen extends GuiScreen {

    private static final int MAX_WIDTH = 500;
    private static final int MAX_HEIGHT = 330;
    private static final int SIDEBAR_WIDTH = 128;
    private static final int TITLE_HEIGHT = 24;
    private static final int FOOTER_HEIGHT = 28;
    private static final int MODULE_ROW = 20;
    private static final int GAP = 6;

    private static final Map<String, String> NAMES = new HashMap<>();

    static {
        NAMES.put("loadingscreen", "Loading screen");
        NAMES.put("mainmenu", "Main menu");
        NAMES.put("menus", "Menus");
        NAMES.put("minecraft", "Minecraft");
        NAMES.put("gregtech", "GregTech");
        NAMES.put("nei", "NEI");
        NAMES.put("tinkers", "Tinkers' Construct");
        NAMES.put("extrautilities", "Extra Utilities");
        NAMES.put("logs", "Logs");
        NAMES.put("backups", "Backups");
        NAMES.put("debug", "Debug");
    }

    /** Page shown when the screen opens again. */
    private static int lastPage;

    private final GuiScreen parent;
    private final List<Page> pages = new ArrayList<>();
    private final List<Rows.Row> rows = new ArrayList<>();
    /** Values before the first change in this screen, to tell which changes need a restart. */
    private final Map<Property, String> initialValues = new IdentityHashMap<>();
    private GuiTextField search;
    private int searchW;
    private int page;

    private int windowX, windowY, windowW, windowH;
    private int listX, listY, listW, listH;
    private int contentX, contentY, contentW, contentH;

    private float scroll, scrollTarget;
    private int contentHeight;
    private long lastFrame;
    private boolean draggingScrollbar;

    public SettingsScreen(GuiScreen parent) {
        this.parent = parent;
        this.page = lastPage;
    }

    /** A module, or the general switches of all of them. */
    private static final class Page {

        final TweakModule module;
        final String name;
        final Property enabled;
        final List<Rows.Row> rows = new ArrayList<>();
        float switchPosition;

        Page(TweakModule module, String name, Property enabled) {
            this.module = module;
            this.name = name;
            this.enabled = enabled;
            this.switchPosition = enabled != null && enabled.getBoolean(false) ? 1 : 0;
        }
    }

    public static String displayName(TweakModule module) {
        String name = NAMES.get(module.getId());
        return name != null ? name : UI.humanize(module.getId());
    }

    // ---- layout

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        windowW = Math.min(MAX_WIDTH, width - 16);
        windowH = Math.min(MAX_HEIGHT, height - 16);
        windowX = (width - windowW) / 2;
        windowY = (height - windowH) / 2;

        listX = windowX + GAP;
        listY = windowY + TITLE_HEIGHT;
        listW = SIDEBAR_WIDTH;
        listH = windowH - TITLE_HEIGHT - FOOTER_HEIGHT;
        contentX = listX + listW + GAP;
        contentY = listY;
        contentW = windowX + windowW - GAP - contentX;
        contentH = listH;

        String query = search != null ? search.getText() : "";
        searchW = Math.min(140, windowW / 3) - 8;
        search = new GuiTextField(fontRendererObj, windowX + windowW - GAP - searchW - 4, windowY + 8, searchW, 12);
        search.setEnableBackgroundDrawing(false);
        search.setMaxStringLength(64);
        search.setText(query);

        buildPages();
        page = Math.max(0, Math.min(page, pages.size() - 1));
        showRows();
    }

    private void buildPages() {
        pages.clear();
        int controlWidth = Math.max(70, Math.min(130, contentW / 2 - 40));
        Configuration modules = ModuleManager.getModulesConfig();
        for (TweakModule module : ModuleManager.getModules()) {
            Property enabled = modules.hasCategory("modules") ? modules.getCategory("modules")
                .get(module.getId()) : null;
            Page p = new Page(module, displayName(module), enabled);
            p.rows.add(new Rows.TitleRow(p.name, module.getDescription(), contentW));
            Configuration config = ModuleManager.getConfig(module);
            List<String> categories = new ArrayList<>(config.getCategoryNames());
            // the general settings first
            if (categories.remove("general")) categories.add(0, "general");
            for (String name : categories) {
                ConfigCategory category = config.getCategory(name);
                if (category.isChild() || category.getValues()
                    .isEmpty()) continue;
                p.rows.add(new Rows.HeaderRow(UI.humanize(name), category.getComment()));
                for (Property property : category.getValues()
                    .values()) {
                    if (!module.showInSettings(name, property.getName())) continue;
                    p.rows.add(new Rows.SettingRow(this, property, controlWidth));
                }
            }
            pages.add(p);
        }
    }

    /** The rows of the selected page, or the settings of every page that match the search. */
    private void showRows() {
        commitAll();
        rows.clear();
        String query = search.getText()
            .trim();
        if (query.isEmpty()) {
            if (!pages.isEmpty()) rows.addAll(pages.get(page).rows);
        } else {
            for (Page p : pages) {
                boolean first = true;
                for (Rows.Row row : p.rows) {
                    if (!row.matches(query)) continue;
                    if (first) rows.add(new Rows.HeaderRow(p.name, p.module.getDescription()));
                    first = false;
                    rows.add(row);
                }
            }
        }
        contentHeight = 4;
        for (Rows.Row row : rows) contentHeight += row.height();
        clampScroll();
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - contentH + 4);
    }

    private void clampScroll() {
        scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget));
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }

    // ---- drawing

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(100, (now - lastFrame) / 1_000_000F);
        lastFrame = now;
        if (draggingScrollbar) {
            if (Mouse.isButtonDown(0)) dragScrollbar(mouseY);
            else draggingScrollbar = false;
        }
        scroll = UI.approach(scroll, scrollTarget, dt, 120);

        GTNHTheme theme = UI.theme();
        if (mc.theWorld != null) drawDefaultBackground();
        else {
            UI.beginShapes();
            GLDraw.verticalGradient(0, 0, width, height, theme.backgroundTop, theme.backgroundBottom);
            UI.endShapes();
        }

        UI.panel(windowX, windowY, windowW, windowH);
        drawTitle(mouseX, mouseY, theme);
        drawModules(mouseX, mouseY, dt, theme);
        drawContent(mouseX, mouseY, dt, theme);
        drawFooter(mouseX, mouseY, theme);

        List<String> tooltip = tooltip(mouseX, mouseY);
        if (tooltip != null) drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
    }

    private void drawTitle(int mouseX, int mouseY, GTNHTheme theme) {
        UI.text("GTNHTweaks", windowX + GAP + 2, windowY + 9, theme.logo);
        UI.text(
            Tags.VERSION,
            windowX + GAP + 8 + fontRendererObj.getStringWidth("GTNHTweaks"),
            windowY + 9,
            theme.textDisabled);

        int x = search.xPosition - 4, y = search.yPosition - 4, w = searchW + 8;
        UI.inset(x, y, w, 16);
        if (search.isFocused()) UI.rect(x, y + 15, w, 1, theme.barFill);
        if (search.getText()
            .isEmpty() && !search.isFocused()) {
            UI.text("Search...", search.xPosition, search.yPosition, theme.textDisabled);
        }
        search.setTextColor(theme.text & 0xFFFFFF);
        search.drawTextBox();
    }

    private void drawModules(int mouseX, int mouseY, float dt, GTNHTheme theme) {
        UI.inset(listX, listY, listW, listH);
        boolean searching = !search.getText()
            .trim()
            .isEmpty();
        UI.scissor(listX + 1, listY + 1, listW - 2, listH - 2);
        for (int i = 0; i < pages.size(); i++) {
            Page p = pages.get(i);
            int y = listY + 2 + i * MODULE_ROW;
            boolean selected = i == page && !searching;
            boolean hovered = UI.inside(mouseX, mouseY, listX + 2, y, listW - 4, MODULE_ROW);
            if (selected) UI.button(listX + 2, y, listW - 4, MODULE_ROW, true, true);
            else if (hovered) UI.rect(listX + 2, y, listW - 4, MODULE_ROW, 0x14FFFFFF);
            if (selected) UI.rect(listX + 3, y + 3, 2, MODULE_ROW - 6, theme.barFill);

            boolean on = p.enabled == null || p.enabled.getBoolean(false);
            int color = !on ? theme.textDisabled : selected ? theme.hoverText : theme.text;
            String name = fontRendererObj.trimStringToWidth(p.name, listW - 44);
            UI.text(name, listX + 9, y + (MODULE_ROW - 8) / 2, color);
            if (p.enabled != null) {
                p.switchPosition = UI.approach(p.switchPosition, on ? 1 : 0, dt, 120);
                boolean switchHovered = UI.inside(mouseX, mouseY, listX + listW - 30, y, 28, MODULE_ROW);
                UI.toggle(listX + listW - 28, y + (MODULE_ROW - 10) / 2, p.switchPosition, switchHovered);
            }
        }
        UI.endScissor();
    }

    private void drawContent(int mouseX, int mouseY, float dt, GTNHTheme theme) {
        UI.inset(contentX, contentY, contentW, contentH);
        boolean overContent = UI.inside(mouseX, mouseY, contentX, contentY, contentW, contentH);
        int mx = overContent ? mouseX : -1000, my = overContent ? mouseY : -1000;
        int w = contentW - (maxScroll() > 0 ? 8 : 0);

        UI.scissor(contentX + 1, contentY + 1, contentW - 2, contentH - 2);
        int y = contentY + 2 - Math.round(scroll);
        for (Rows.Row row : rows) {
            int h = row.height();
            if (y + h > contentY && y < contentY + contentH) row.draw(contentX, y, w, mx, my, dt);
            y += h;
        }
        if (rows.isEmpty()) {
            String text = "Nothing found";
            UI.text(
                text,
                contentX + (contentW - fontRendererObj.getStringWidth(text)) / 2,
                contentY + contentH / 2 - 4,
                theme.textDisabled);
        }
        UI.endScissor();

        if (maxScroll() > 0) {
            int trackX = contentX + contentW - 7, trackY = contentY + 2, trackH = contentH - 4;
            int thumbH = Math.max(16, trackH * contentH / Math.max(1, contentHeight));
            int thumbY = trackY + Math.round((trackH - thumbH) * scroll / maxScroll());
            boolean hovered = draggingScrollbar || UI.inside(mouseX, mouseY, trackX, trackY, 5, trackH);
            UI.button(trackX, thumbY, 5, thumbH, hovered, true);
        }
    }

    private void drawFooter(int mouseX, int mouseY, GTNHTheme theme) {
        int y = windowY + windowH - FOOTER_HEIGHT + 6;
        int doneW = 80, folderW = 90;
        UI.button(windowX + windowW - GAP - doneW, y, doneW, 18, "Done", mouseX, mouseY, true);
        UI.button(windowX + windowW - GAP * 2 - doneW - folderW, y, folderW, 18, "Config folder", mouseX, mouseY, true);

        List<String> restart = restartNeeded();
        if (!restart.isEmpty()) {
            String text = "Restart the game to apply: " + String.join(", ", restart);
            text = fontRendererObj.trimStringToWidth(text, windowW - doneW - folderW - GAP * 4);
            UI.text(text, windowX + GAP + 2, y + 5, 0xFFFFC040);
        } else {
            UI.text("Changes are saved at once", windowX + GAP + 2, y + 5, theme.textDisabled);
        }
    }

    private List<String> tooltip(int mouseX, int mouseY) {
        if (!UI.inside(mouseX, mouseY, contentX, contentY, contentW, contentH)) {
            for (int i = 0; i < pages.size(); i++) {
                int y = listY + 2 + i * MODULE_ROW;
                if (UI.inside(mouseX, mouseY, listX + 2, y, listW - 4, MODULE_ROW) && mouseY < listY + listH) {
                    List<String> lines = new ArrayList<>();
                    lines.add("§e" + pages.get(i).name);
                    lines.addAll(Rows.wrap(pages.get(i).module.getDescription()));
                    lines.add("§6Turning a module on or off needs a game restart");
                    return lines;
                }
            }
            return null;
        }
        int w = contentW - (maxScroll() > 0 ? 8 : 0);
        int y = contentY + 2 - Math.round(scroll);
        for (Rows.Row row : rows) {
            List<String> lines = row.tooltip(contentX, y, w, mouseX, mouseY);
            if (lines != null) return lines;
            y += row.height();
        }
        return null;
    }

    // ---- input

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        search.mouseClicked(mouseX, mouseY, button);
        if (button == 1 && UI.inside(mouseX, mouseY, search.xPosition - 4, search.yPosition - 4, searchW + 8, 16)) {
            search.setText("");
            showRows();
        }

        // footer buttons
        int y = windowY + windowH - FOOTER_HEIGHT + 6;
        int doneW = 80, folderW = 90;
        if (button == 0 && UI.inside(mouseX, mouseY, windowX + windowW - GAP - doneW, y, doneW, 18)) {
            UI.playClick();
            close();
            return;
        }
        if (button == 0 && UI.inside(mouseX, mouseY, windowX + windowW - GAP * 2 - doneW - folderW, y, folderW, 18)) {
            UI.playClick();
            UI.open(ModuleManager.getConfigDir());
            return;
        }

        // modules
        for (int i = 0; i < pages.size(); i++) {
            int rowY = listY + 2 + i * MODULE_ROW;
            if (mouseY >= listY + listH || !UI.inside(mouseX, mouseY, listX + 2, rowY, listW - 4, MODULE_ROW)) continue;
            Page p = pages.get(i);
            if (p.enabled != null && mouseX >= listX + listW - 30) {
                beforeChange(p.enabled);
                p.enabled.set(!p.enabled.getBoolean(false));
                afterChange(p.enabled);
            } else {
                page = i;
                lastPage = i;
                search.setText("");
                scroll = scrollTarget = 0;
                showRows();
            }
            UI.playClick();
            return;
        }

        // scrollbar
        if (maxScroll() > 0 && UI.inside(mouseX, mouseY, contentX + contentW - 8, contentY, 8, contentH)) {
            draggingScrollbar = true;
            dragScrollbar(mouseY);
            return;
        }

        // settings: every row sees the click, so text fields lose the focus
        boolean inside = UI.inside(mouseX, mouseY, contentX, contentY, contentW, contentH);
        int w = contentW - (maxScroll() > 0 ? 8 : 0);
        int rowY = contentY + 2 - Math.round(scroll);
        for (Rows.Row row : new ArrayList<>(rows)) {
            row.click(contentX, rowY, w, inside ? mouseX : -1000, inside ? mouseY : -1000, button);
            rowY += row.height();
        }
    }

    private void dragScrollbar(int mouseY) {
        int trackH = contentH - 4;
        int thumbH = Math.max(16, trackH * contentH / Math.max(1, contentHeight));
        float t = (mouseY - contentY - 2 - thumbH / 2F) / Math.max(1, trackH - thumbH);
        scrollTarget = scroll = Math.max(0, Math.min(1, t)) * maxScroll();
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scrollTarget -= Integer.signum(wheel) * 36;
            clampScroll();
        }
    }

    @Override
    protected void keyTyped(char c, int code) {
        if (code == Keyboard.KEY_ESCAPE) {
            close();
            return;
        }
        if (search.isFocused()) {
            String before = search.getText();
            search.textboxKeyTyped(c, code);
            if (!before.equals(search.getText())) {
                scroll = scrollTarget = 0;
                showRows();
            }
            return;
        }
        for (Rows.Row row : rows) {
            if (row.key(c, code)) return;
        }
        if (code == Keyboard.KEY_F && isCtrlKeyDown()) search.setFocused(true);
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

    private static String valueOf(Property property) {
        return property.isList() ? Arrays.toString(property.getStringList()) : property.getString();
    }

    /** Names of the changed settings that only apply after a restart. */
    private List<String> restartNeeded() {
        List<String> names = new ArrayList<>();
        for (Map.Entry<Property, String> entry : initialValues.entrySet()) {
            Property property = entry.getKey();
            if (property.requiresMcRestart() && !valueOf(property).equals(entry.getValue())) {
                names.add(UI.humanize(property.getName()));
            }
        }
        return names;
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
        commitAll();
        ModuleManager.saveAndReload();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
