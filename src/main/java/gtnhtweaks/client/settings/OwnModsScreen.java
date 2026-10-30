package gtnhtweaks.client.settings;

import static org.lwjgl.opengl.GL11.*;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.common.render.GLDraw;
import gtnhtweaks.common.render.GTNHTheme;
import gtnhtweaks.modules.buttons.mods.ModState;
import gtnhtweaks.modules.buttons.mods.OwnMod;
import gtnhtweaks.modules.buttons.mods.OwnMods;

/**
 * The mods of the author of GTNHTweaks, GTNHTweaks first: opened by the square with an arrow in the main menu.
 * <p>
 * At the left the mods with the installed version and the newest one on GitHub; at the right what the selected mod
 * does, its versions and files, and the buttons to install, update or delete it, or delete the jars an update turned
 * off. Laid out like {@link ModListScreen}.
 */
public class OwnModsScreen extends GuiScreen {

    private static final int MAX_WIDTH = 700;
    private static final int MAX_HEIGHT = 400;
    private static final int GAP = 6;
    private static final int TITLE_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 30;
    private static final int ROW = 30;
    private static final int ICON = 22;
    /** Room between GTNHTweaks and the other mods. */
    private static final int SPLIT = 6;
    /** How long a delete button waits for the second click. */
    private static final long CONFIRM_MS = 3000;
    private static final int CHECK_W = 80;
    private static final int DONE_W = 60;

    private static final int GREEN = UI.ON;
    private static final int YELLOW = Rows.WARNING;
    private static final int ORANGE = Rows.RESTART;
    private static final int RED = Rows.ERROR;

    /** A line of the details: a label, text with color codes, a link, or a gap. */
    private static final class Line {

        final String text;
        final int kind;
        final int color;

        Line(String text, int kind, int color) {
            this.text = text;
            this.kind = kind;
            this.color = color;
        }
    }

    private static final int LABEL = 0;
    private static final int TEXT = 1;
    private static final int LINK = 2;
    private static final int GAP_LINE = 3;

    /** The mod selected last, kept while the game runs. */
    private static OwnMod lastSelected = OwnMod.GTNHTWEAKS;

    private final GuiScreen parent;
    private OwnMod selected = lastSelected;
    private final List<Line> lines = new ArrayList<>();
    private final Scroll listScroll = new Scroll();
    private final Scroll detailScroll = new Scroll();
    /** The delete button waiting for its second click, and since when. */
    private String confirming;
    private long confirmSince;
    private long lastFrame;
    private int headerH;

    private int windowX, windowY, windowW, windowH;
    private int listX, listY, listW, listH;
    private int detailX, detailY, detailW, detailH;

    public OwnModsScreen(GuiScreen parent) {
        this.parent = parent;
        OwnMods.rescan();
        OwnMods.check(false);
    }

    // ---- layout

    @Override
    public void initGui() {
        windowW = Math.min(MAX_WIDTH, width - 12);
        windowH = Math.min(MAX_HEIGHT, height - 12);
        windowX = (width - windowW) / 2;
        windowY = (height - windowH) / 2;
        listX = windowX + GAP;
        listY = windowY + TITLE_HEIGHT;
        listW = Math.min(220, windowW * 2 / 5);
        listH = windowH - TITLE_HEIGHT - FOOTER_HEIGHT;
        detailX = listX + listW + GAP;
        detailY = listY;
        detailW = windowX + windowW - GAP - detailX;
        detailH = listH;
        listScroll.content = OwnMod.values().length * ROW + SPLIT + 4;
        listScroll.view = listH - 4;
        listScroll.clamp();
    }

    private int rowY(int index) {
        return listY + 2 + index * ROW + (index > 0 ? SPLIT : 0) - listScroll.offset();
    }

    private int footerY() {
        return listY + listH + (FOOTER_HEIGHT - 4 - 20) / 2;
    }

    private ModState state(OwnMod mod) {
        for (ModState state : OwnMods.states()) if (state.mod == mod) return state;
        return null;
    }

    // ---- drawing

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(100, (now - lastFrame) / 1_000_000F);
        lastFrame = now;
        dragBar(listScroll, listY, listH, mouseY);
        dragBar(detailScroll, detailY, detailH, mouseY);
        listScroll.update(dt);
        detailScroll.update(dt);
        if (confirming != null && System.currentTimeMillis() - confirmSince > CONFIRM_MS) confirming = null;

        GTNHTheme theme = UI.theme();
        if (mc.theWorld != null) drawDefaultBackground();
        else {
            UI.beginShapes();
            GLDraw.verticalGradient(0, 0, width, height, theme.backgroundTop, theme.backgroundBottom);
            UI.endShapes();
        }
        UI.panel(windowX, windowY, windowW, windowH);
        drawTitle(mouseX, mouseY, theme);
        drawList(mouseX, mouseY, theme);
        ModState state = state(selected);
        if (state != null) {
            buildLines(state);
            drawDetails(state, mouseX, mouseY, theme);
        }
        drawFooter(state, mouseX, mouseY);
    }

    private void dragBar(Scroll scroll, int y, int h, int mouseY) {
        if (!scroll.dragging) return;
        if (Mouse.isButtonDown(0)) scroll.drag(y + 2, h - 4, mouseY);
        else scroll.dragging = false;
    }

    private void drawTitle(int mouseX, int mouseY, GTNHTheme theme) {
        String title = "Mods by EvgenWarGold";
        UI.text(title, windowX + GAP + 2, windowY + 10, theme.logo);
        int checkX = windowX + windowW - GAP - CHECK_W;
        String checked;
        if (OwnMods.isChecking()) checked = "Checking GitHub...";
        else if (OwnMods.checkedAt() == 0) checked = "Not checked yet";
        else {
            long minutes = (System.currentTimeMillis() - OwnMods.checkedAt()) / 60_000;
            checked = "Checked " + (minutes == 0 ? "just now" : minutes + " min ago");
        }
        UI.text(checked, checkX - 8 - fontRendererObj.getStringWidth(checked), windowY + 10, theme.textDisabled);
        UI.button(checkX, windowY + 6, CHECK_W, 16, "Check again", mouseX, mouseY, !OwnMods.isChecking());
    }

    private void drawList(int mouseX, int mouseY, GTNHTheme theme) {
        UI.inset(listX, listY, listW, listH);
        boolean over = UI.inside(mouseX, mouseY, listX, listY, listW, listH);
        int barW = listScroll.active() ? Scroll.BAR_WIDTH + 3 : 0;
        int w = listW - 4 - barW;
        UI.scissor(listX + 1, listY + 1, listW - 2, listH - 2);
        OwnMod[] mods = OwnMod.values();
        for (int i = 0; i < mods.length; i++) {
            int y = rowY(i);
            if (y > listY + listH || y + ROW < listY) continue;
            OwnMod mod = mods[i];
            ModState state = state(mod);
            boolean isSelected = mod == selected;
            boolean hovered = over && UI.inside(mouseX, mouseY, listX + 2, y, w, ROW);
            if (isSelected) UI.button(listX + 2, y, w, ROW, true, true);
            else if (hovered) UI.rect(listX + 2, y, w, ROW, 0x14FFFFFF);
            if (isSelected) UI.rect(listX + 3, y + 4, 2, ROW - 8, theme.barFill);
            // GTNHTweaks itself stands apart, above the others
            if (i == 0) UI.rect(listX + 8, y + ROW + SPLIT / 2, w - 12, 1, theme.textDisabled & 0x60FFFFFF);

            int iconX = listX + 9;
            badge(mod, iconX, y + (ROW - ICON) / 2, ICON);
            int textX = iconX + ICON + 6;
            int right = listX + 2 + w - 5;
            String tag = state == null ? "" : tag(state);
            if (!tag.isEmpty()) {
                right -= fontRendererObj.getStringWidth(tag);
                UI.text(tag, right, y + 5, tagColor(state));
                right -= 4;
            }
            UI.text(
                fontRendererObj.trimStringToWidth(mod.displayName, right - textX),
                textX,
                y + 5,
                isSelected ? theme.hoverText : theme.text);
            if (state != null) UI.text(
                fontRendererObj.trimStringToWidth(versions(state), listX + 2 + w - 5 - textX),
                textX,
                y + 16,
                theme.textDisabled);

            if (OwnMods.busy() == mod) progressBar(textX, y + ROW - 4, listX + 2 + w - 5 - textX, theme);
        }
        UI.endScissor();
        listScroll.drawBar(listX + listW - Scroll.BAR_WIDTH - 2, listY + 2, listH - 4, mouseX, mouseY);
    }

    /** The word at the right of a row: what the mod needs, if anything. */
    private static String tag(ModState state) {
        if (OwnMods.busy() == state.mod) return OwnMods.busyText() + "...";
        if (state.restart) return "Restart";
        switch (state.status()) {
            case OUTDATED:
                return "Update";
            case NOT_INSTALLED:
                return state.latest != null ? "Install" : "";
            default:
                return "";
        }
    }

    private static int tagColor(ModState state) {
        if (OwnMods.busy() == state.mod) return UI.theme().textDim;
        if (state.restart) return ORANGE;
        return state.status() == ModState.Status.OUTDATED ? YELLOW : UI.theme().textDim;
    }

    /** The installed version and the newest one, in color: "0.0.2 → 0.0.3". */
    private static String versions(ModState state) {
        String latest = state.latest == null ? null : state.latest.version.toString();
        switch (state.status()) {
            case NOT_INSTALLED:
                return "§7Not installed" + (latest == null ? "" : " §8· §7" + latest + " on GitHub");
            case OUTDATED:
                return "§e" + state.installed + " §7→ §a" + latest;
            case UP_TO_DATE:
                return "§a" + state.installed + " §8· §7the newest";
            default:
                return "§f" + state.installed;
        }
    }

    /** A thin bar of the download, or a moving stripe while its size is not known. */
    private void progressBar(int x, int y, int w, GTNHTheme theme) {
        UI.rect(x, y, w, 2, theme.barBackground);
        float progress = OwnMods.progress();
        if (progress >= 0) UI.rect(x, y, Math.round(w * progress), 2, theme.barFill);
        else {
            int stripe = w / 4;
            int at = (int) (System.currentTimeMillis() / 4 % (w + stripe)) - stripe;
            int from = Math.max(x, x + at), to = Math.min(x + w, x + at + stripe);
            if (to > from) UI.rect(from, y, to - from, 2, theme.barFill);
        }
    }

    /** A colored square with the first letter of the mod, its hue from the mod id, so every mod keeps its own. */
    private void badge(OwnMod mod, int x, int y, int size) {
        float hue = (mod.modId.hashCode() & 0xFFFF) / 65535F;
        int color = Color.HSBtoRGB(hue, 0.45F, 0.55F) | 0xFF000000;
        UI.rect(x, y, size, size, color);
        UI.rect(x, y + size - 2, size, 2, 0x40000000);
        String letter = mod.displayName.substring(0, 1)
            .toUpperCase(Locale.ROOT);
        float scale = size >= 32 ? 2 : 1;
        glPushMatrix();
        glTranslatef(x + size / 2F, y + size / 2F, 0);
        glScalef(scale, scale, 1);
        fontRendererObj.drawStringWithShadow(letter, -fontRendererObj.getStringWidth(letter) / 2, -4, 0xFFFFFF);
        glPopMatrix();
    }

    /** The details of the selected mod, wrapped to the width of the panel. */
    private void buildLines(ModState state) {
        lines.clear();
        GTNHTheme theme = UI.theme();
        int textW = detailW - 16 - Scroll.BAR_WIDTH;
        headerH = 54;

        OwnMods.Message message = OwnMods.message(state.mod);
        if (message != null) {
            for (Object wrapped : fontRendererObj.listFormattedStringToWidth(message.text, textW)) {
                lines.add(new Line((String) wrapped, TEXT, message.error ? RED : GREEN));
            }
            lines.add(new Line("", GAP_LINE, 0));
        }

        lines.add(new Line("Installed", LABEL, 0));
        if (state.installed == null) lines.add(new Line("§7Not installed", TEXT, theme.text));
        else {
            int color = state.status() == ModState.Status.OUTDATED ? YELLOW
                : state.status() == ModState.Status.UP_TO_DATE ? GREEN : theme.text;
            String files = state.jars.isEmpty() ? ""
                : " §8· §7" + String.join(", ", fileNames(state.jars));
            wrap(state.installed + files, textW, color);
        }
        if (state.restart) {
            String now = state.loadedVersion == null ? "The game runs without it now"
                : "The game runs " + state.loadedVersion + " now";
            wrap(now + ": restart it to load the mods folder as it is", textW, ORANGE);
        }
        if (state.jars.size() > 1) wrap("More than one jar is enabled: an update turns off the old ones", textW, RED);
        lines.add(new Line("", GAP_LINE, 0));

        lines.add(new Line("On GitHub", LABEL, 0));
        if (state.latest != null) {
            String size = state.latest.size > 0 ? " §8· §7" + Math.max(1, state.latest.size / 1024) + " KB" : "";
            String date = state.latest.date.isEmpty() ? "" : " §8· §7" + state.latest.date;
            wrap("§a" + state.latest.version + date + " §8· §7" + state.latest.fileName + size, textW, theme.text);
        } else if (OwnMods.isChecking()) lines.add(new Line("Checking...", TEXT, theme.textDim));
        else if (state.error != null) wrap(state.error, textW, RED);
        else lines.add(new Line("Not checked yet", TEXT, theme.textDim));
        lines.add(new Line("", GAP_LINE, 0));

        lines.add(new Line("About", LABEL, 0));
        wrap(state.mod.description, textW, theme.text);
        lines.add(new Line("", GAP_LINE, 0));

        if (!state.disabled.isEmpty()) {
            lines.add(new Line("Turned off by updates", LABEL, 0));
            for (File file : state.disabled) wrap(file.getName(), textW, theme.textDim);
            lines.add(new Line("", GAP_LINE, 0));
        }
        if (!state.pending.isEmpty()) {
            lines.add(new Line("When the game closes", LABEL, 0));
            for (String change : state.pending) wrap(change, textW, ORANGE);
            lines.add(new Line("", GAP_LINE, 0));
        }

        int height = headerH;
        for (Line line : lines) height += line.kind == GAP_LINE ? 5 : 10;
        detailScroll.content = height + 6;
        detailScroll.view = detailH - 4;
        detailScroll.clamp();
    }

    private void wrap(String text, int width, int color) {
        for (Object wrapped : fontRendererObj.listFormattedStringToWidth(text, width)) {
            lines.add(new Line((String) wrapped, TEXT, color));
        }
    }

    private static List<String> fileNames(List<File> files) {
        List<String> names = new ArrayList<>();
        for (File file : files) names.add(file.getName());
        return names;
    }

    private void drawDetails(ModState state, int mouseX, int mouseY, GTNHTheme theme) {
        UI.inset(detailX, detailY, detailW, detailH);
        boolean over = UI.inside(mouseX, mouseY, detailX, detailY, detailW, detailH);
        UI.scissor(detailX + 1, detailY + 1, detailW - 2, detailH - 2);
        int x = detailX + 8;
        int y = detailY + 6 - detailScroll.offset();

        badge(state.mod, x, y + 2, 40);
        int nameX = x + 50;
        glPushMatrix();
        glTranslatef(nameX, y + 4, 0);
        glScalef(1.5F, 1.5F, 1);
        UI.text(state.mod.displayName, 0, 0, theme.hoverText);
        glPopMatrix();
        UI.text(status(state), nameX, y + 20, theme.text);
        String link = state.mod.repoUrl()
            .replace("https://", "");
        boolean linkHovered = over && UI.inside(mouseX, mouseY, nameX, y + 30, fontRendererObj.getStringWidth(link), 10);
        UI.text(link, nameX, y + 31, linkHovered ? theme.hoverText : theme.barFill);
        if (linkHovered) UI.rect(nameX, y + 40, fontRendererObj.getStringWidth(link), 1, theme.hoverText);
        UI.rect(
            x,
            y + headerH - 6,
            detailW - 16 - Scroll.BAR_WIDTH,
            1,
            theme.isBeveled() ? theme.inset.topLeft : theme.barBorder);
        if (OwnMods.busy() == state.mod) progressBar(x, y + headerH - 6, detailW - 16 - Scroll.BAR_WIDTH, theme);

        y += headerH;
        for (Line line : lines) {
            if (line.kind == GAP_LINE) {
                y += 5;
                continue;
            }
            if (y + 10 >= detailY && y <= detailY + detailH) {
                if (line.kind == LABEL) UI.text(line.text.toUpperCase(Locale.ROOT), x, y, theme.textDisabled);
                else UI.text(line.text, x, y, line.color);
            }
            y += 10;
        }
        UI.endScissor();
        detailScroll.drawBar(detailX + detailW - Scroll.BAR_WIDTH - 2, detailY + 2, detailH - 4, mouseX, mouseY);
    }

    /** One line under the name: what state the mod is in, in color. */
    private static String status(ModState state) {
        if (OwnMods.busy() == state.mod) return "§7" + OwnMods.busyText() + "...";
        switch (state.status()) {
            case NOT_INSTALLED:
                return state.latest == null ? "§7Not installed" : "§7Not installed §8· §fready to install";
            case OUTDATED:
                return "§eUpdate available: §f" + state.installed + " §7→ §a" + state.latest.version;
            case UP_TO_DATE:
                return "§aUp to date";
            default:
                return "§fInstalled §8· §7the newest version is not known";
        }
    }

    // ---- buttons

    /** The three buttons under the details share their width. */
    private int actionW() {
        return (detailW - 2 * GAP) / 3;
    }

    private int actionX(int index) {
        return detailX + index * (actionW() + GAP);
    }

    private int folderW() {
        return listW - DONE_W - GAP;
    }

    private int doneX() {
        return listX + folderW() + GAP;
    }

    /** Install for a mod not installed, Update for an old one; nothing to do for the others. */
    private static String primaryLabel(ModState state) {
        if (state == null) return "Install";
        switch (state.status()) {
            case NOT_INSTALLED:
                return "Install";
            case UP_TO_DATE:
                return "Up to date";
            default:
                return "Update";
        }
    }

    private static boolean primaryEnabled(ModState state) {
        if (state == null || OwnMods.busy() != null || state.latest == null) return false;
        ModState.Status status = state.status();
        return status == ModState.Status.NOT_INSTALLED || status == ModState.Status.OUTDATED;
    }

    private void drawFooter(ModState state, int mouseX, int mouseY) {
        int y = footerY();
        boolean idle = OwnMods.busy() == null;
        int w = actionW();
        UI.button(listX, y, folderW(), 20, fit("Mods folder", folderW()), mouseX, mouseY, true);
        UI.button(doneX(), y, DONE_W, 20, "Done", mouseX, mouseY, true);
        UI.button(actionX(0), y, w, 20, fit(primaryLabel(state), w), mouseX, mouseY, primaryEnabled(state));
        boolean canDelete = idle && state != null && !state.jars.isEmpty();
        String delete = "delete".equals(confirming) ? "Sure? Click again" : "Delete";
        UI.button(actionX(1), y, w, 20, fit(delete, w), mouseX, mouseY, canDelete);
        int off = state == null ? 0 : state.disabled.size();
        String deleteOff = "deleteOff".equals(confirming) ? "Sure? Click again"
            : "Delete turned off" + (off > 0 ? " (" + off + ")" : "");
        UI.button(actionX(2), y, w, 20, fit(deleteOff, w), mouseX, mouseY, idle && off > 0);
    }

    /** The label cut to the width of its button. */
    private String fit(String label, int buttonW) {
        return fontRendererObj.trimStringToWidth(label, buttonW - 6);
    }

    // ---- input

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (button != 0) return;
        if (listScroll.press(listX + listW - Scroll.BAR_WIDTH - 2, listY + 2, listH - 4, mouseX, mouseY)) return;
        if (detailScroll.press(detailX + detailW - Scroll.BAR_WIDTH - 2, detailY + 2, detailH - 4, mouseX, mouseY)) {
            return;
        }
        if (UI.inside(mouseX, mouseY, windowX + windowW - GAP - CHECK_W, windowY + 6, CHECK_W, 16)) {
            if (OwnMods.isChecking()) return;
            UI.playClick();
            OwnMods.check(true);
            return;
        }
        if (UI.inside(mouseX, mouseY, listX, listY, listW, listH)) {
            OwnMod[] mods = OwnMod.values();
            for (int i = 0; i < mods.length; i++) {
                if (UI.inside(mouseX, mouseY, listX + 2, rowY(i), listW - 4, ROW)) {
                    UI.playClick();
                    select(mods[i]);
                    return;
                }
            }
            return;
        }
        ModState state = state(selected);
        if (state != null && UI.inside(mouseX, mouseY, detailX, detailY, detailW, detailH)) {
            int y = detailY + 6 - detailScroll.offset() + 30;
            String link = state.mod.repoUrl()
                .replace("https://", "");
            if (UI.inside(mouseX, mouseY, detailX + 58, y, fontRendererObj.getStringWidth(link), 10)) {
                UI.playClick();
                openUrl(state.mod.repoUrl());
            }
            return;
        }

        int y = footerY(), w = actionW();
        if (UI.inside(mouseX, mouseY, listX, y, folderW(), 20)) {
            UI.playClick();
            File folder = OwnMods.modsFolder();
            folder.mkdirs();
            UI.open(folder);
        } else if (UI.inside(mouseX, mouseY, doneX(), y, DONE_W, 20)) {
            UI.playClick();
            mc.displayGuiScreen(parent);
        } else if (UI.inside(mouseX, mouseY, actionX(0), y, w, 20)) {
            if (!primaryEnabled(state)) return;
            UI.playClick();
            OwnMods.install(state.mod);
        } else if (UI.inside(mouseX, mouseY, actionX(1), y, w, 20)) {
            if (OwnMods.busy() != null || state == null || state.jars.isEmpty()) return;
            UI.playClick();
            if (confirm("delete")) OwnMods.delete(state.mod);
        } else if (UI.inside(mouseX, mouseY, actionX(2), y, w, 20)) {
            if (OwnMods.busy() != null || state == null || state.disabled.isEmpty()) return;
            UI.playClick();
            if (confirm("deleteOff")) OwnMods.deleteDisabled(state.mod);
        }
    }

    /** True on the second click of the same delete button within a few seconds; the first one asks "Sure?". */
    private boolean confirm(String action) {
        if (action.equals(confirming)) {
            confirming = null;
            return true;
        }
        confirming = action;
        confirmSince = System.currentTimeMillis();
        return false;
    }

    private void select(OwnMod mod) {
        if (mod != selected) {
            detailScroll.reset();
            confirming = null;
        }
        selected = lastSelected = mod;
        int index = mod.ordinal();
        int top = index * ROW + (index > 0 ? SPLIT : 0);
        listScroll.show(top, top + ROW + 4);
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
        if (code == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (code == Keyboard.KEY_UP || code == Keyboard.KEY_DOWN) {
            int index = selected.ordinal() + (code == Keyboard.KEY_UP ? -1 : 1);
            OwnMod[] mods = OwnMod.values();
            if (index >= 0 && index < mods.length) select(mods[index]);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return parent == null || parent.doesGuiPauseGame();
    }
}
