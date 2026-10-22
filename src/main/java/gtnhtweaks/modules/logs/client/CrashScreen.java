package gtnhtweaks.modules.logs.client;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import gtnhtweaks.client.settings.UI;
import gtnhtweaks.common.render.GLDraw;
import gtnhtweaks.common.render.GTNHTheme;
import gtnhtweaks.modules.logs.LogsModule;

/**
 * Shown after a crash or a mod loading problem: what went wrong, the problem in plain words and the whole crash report
 * to scroll through, and buttons to copy the report, open the log folder or the report, go back to the main menu
 * when the game can go on, or quit.
 */
public class CrashScreen extends GuiScreen {

    private static final int RED = 0xFFE5534B;
    private static final int ORANGE = 0xFFF2A33A;
    private static final int YELLOW = 0xFFFFD866;
    private static final int GAP = 6;
    private static final int LINE = 10;
    private static final int BUTTON_H = 18;
    private static final long COPIED_MS = 2000;

    private final CrashInfo info;

    /** Problem and crash report. */
    private final String[] tabNames = { "Problem", "Crash report" };
    private final List<List<String>> wrapped = new ArrayList<>();
    private final float[] scroll = new float[2], scrollTarget = new float[2];
    private int tab;

    private int windowX, windowY, windowW, windowH;
    private int textX, textY, textW, textH;
    private long lastFrame;
    private long copiedAt;
    private boolean draggingScrollbar;
    private final List<Button> buttons = new ArrayList<>();

    private static final class Button {

        final String label;
        final Runnable action;
        int x, y, w;

        Button(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }
    }

    public CrashScreen(CrashInfo info) {
        this.info = info;
    }

    // ---- layout

    @Override
    public void initGui() {
        mc.setIngameNotInFocus();
        windowW = width - 16;
        windowH = height - 16;
        windowX = 8;
        windowY = 8;
        int header = 46 + (info.likelyCause != null ? 11 : 0);
        textX = windowX + GAP;
        textY = windowY + header + BUTTON_H + 2;
        textW = windowW - GAP * 2;
        textH = windowH - header - BUTTON_H - 2 - BUTTON_H - GAP * 2;

        wrapped.clear();
        wrapped.add(wrap(info.problem));
        wrapped.add(wrap(info.fullReport));
        for (int i = 0; i < 2; i++) clampScroll(i);

        buttons.clear();
        boolean menu = info.canReturnToMenu();
        if (menu) buttons.add(new Button("Main menu", () -> mc.displayGuiScreen(new GuiMainMenu())));
        buttons.add(new Button("Copy report", this::copy));
        buttons.add(new Button("Open log folder", () -> UI.open(logFolder())));
        if (info.file != null) buttons.add(new Button("Open report", () -> UI.open(info.file)));
        buttons.add(new Button("Quit game", () -> mc.shutdown()));
        int x = windowX + windowW - GAP;
        int y = windowY + windowH - GAP - BUTTON_H;
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Button button = buttons.get(i);
            button.w = Math.max(70, fontRendererObj.getStringWidth(button.label) + 16);
            if (i == 0 && menu) {
                button.x = windowX + GAP;
            } else {
                x -= button.w;
                button.x = x;
                x -= 4;
            }
            button.y = y;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        int width = textW - 18;
        for (String line : text.replace("\r", "")
            .replace("\t", "    ")
            .split("\n", -1)) {
            if (line.isEmpty()) lines.add("");
            else lines.addAll(fontRendererObj.listFormattedStringToWidth(line, width));
        }
        return lines;
    }

    private int maxScroll(int i) {
        return Math.max(
            0,
            wrapped.get(i)
                .size() * LINE + 8
                - textH);
    }

    private void clampScroll(int i) {
        scrollTarget[i] = Math.max(0, Math.min(maxScroll(i), scrollTarget[i]));
        scroll[i] = Math.max(0, Math.min(maxScroll(i), scroll[i]));
    }

    private static File logFolder() {
        try {
            return LogsModule.logFolder();
        } catch (Throwable t) {
            return new File("crash-reports");
        }
    }

    private void copy() {
        setClipboardString(info.fullReport);
        copiedAt = System.currentTimeMillis();
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
        scroll[tab] = UI.approach(scroll[tab], scrollTarget[tab], dt, 120);

        GTNHTheme theme = UI.theme();
        UI.beginShapes();
        GLDraw.verticalGradient(0, 0, width, height, theme.backgroundTop, theme.backgroundBottom);
        UI.endShapes();
        UI.panel(windowX, windowY, windowW, windowH);
        UI.rect(windowX + 3, windowY + 3, windowW - 6, 2, RED);

        // header
        UI.text(info.title, windowX + GAP + 2, windowY + 10, RED);
        UI.text(
            fontRendererObj.trimStringToWidth(info.description, windowW - GAP * 3),
            windowX + GAP + 2,
            windowY + 22,
            theme.text);
        String label = "Problem: ";
        UI.text(label, windowX + GAP + 2, windowY + 33, theme.textDim);
        UI.text(info.headline, windowX + GAP + 2 + fontRendererObj.getStringWidth(label), windowY + 33, ORANGE);
        if (info.likelyCause != null) {
            label = "Most likely cause: ";
            UI.text(label, windowX + GAP + 2, windowY + 44, theme.textDim);
            UI.text(info.likelyCause, windowX + GAP + 2 + fontRendererObj.getStringWidth(label), windowY + 44, ORANGE);
        }
        String hint = info.canReturnToMenu() ? "The world was closed, you can go back to the main menu"
            : "The game stays open so the problem can be read";
        UI.text(
            hint,
            windowX + windowW - GAP - 2 - fontRendererObj.getStringWidth(hint),
            windowY + 10,
            theme.textDisabled);

        // tabs
        int tabX = textX;
        for (int i = 0; i < tabNames.length; i++) {
            int w = fontRendererObj.getStringWidth(tabNames[i]) + 20;
            boolean hovered = UI.inside(mouseX, mouseY, tabX, textY - BUTTON_H - 2, w, BUTTON_H);
            UI.button(tabX, textY - BUTTON_H - 2, w, BUTTON_H, hovered || i == tab, true);
            if (i == tab) UI.rect(tabX + 3, textY - 4, w - 6, 1, theme.barFill);
            UI.text(
                tabNames[i],
                tabX + 10,
                textY - BUTTON_H + 3,
                i == tab ? theme.hoverText : hovered ? theme.text : theme.textDim);
            tabX += w + 4;
        }
        String lines = wrapped.get(tab)
            .size() + " lines";
        UI.text(lines, textX + textW - fontRendererObj.getStringWidth(lines) - 2, textY - BUTTON_H + 3, theme.textDim);

        drawText(mouseX, mouseY, theme);

        // buttons
        for (Button button : buttons) {
            String text = button.label;
            if (text.startsWith("Copy") && System.currentTimeMillis() - copiedAt < COPIED_MS) text = "Copied!";
            UI.button(button.x, button.y, button.w, BUTTON_H, text, mouseX, mouseY, true);
        }
    }

    private void drawText(int mouseX, int mouseY, GTNHTheme theme) {
        UI.inset(textX, textY, textW, textH);
        List<String> lines = wrapped.get(tab);
        UI.scissor(textX + 1, textY + 1, textW - 2, textH - 2);
        int first = Math.max(0, (int) (scroll[tab] / LINE) - 1);
        int y = textY + 4 - Math.round(scroll[tab]) + first * LINE;
        for (int i = first; i < lines.size() && y < textY + textH; i++, y += LINE) {
            String line = lines.get(i);
            fontRendererObj.drawString(line, textX + 5, y, colorOf(line, theme), false);
        }
        UI.endScissor();

        if (maxScroll(tab) > 0) {
            int trackX = textX + textW - 8, trackY = textY + 2, trackH = textH - 4;
            int thumbH = thumbHeight(trackH);
            int thumbY = trackY + Math.round((trackH - thumbH) * scroll[tab] / maxScroll(tab));
            boolean hovered = draggingScrollbar || UI.inside(mouseX, mouseY, trackX, trackY, 6, trackH);
            UI.button(trackX, thumbY, 6, thumbH, hovered, true);
        }
    }

    private int thumbHeight(int trackH) {
        int total = wrapped.get(tab)
            .size() * LINE + 8;
        return Math.max(16, trackH * textH / Math.max(1, total));
    }

    private static int colorOf(String line, GTNHTheme theme) {
        String trimmed = line.trim();
        if (trimmed.startsWith("at ") || trimmed.startsWith("...")) return theme.textDim;
        if (trimmed.startsWith("Caused by")) return ORANGE;
        // "-- Head --" in the report; "A MIXIN COULD NOT BE APPLIED", "STACK TRACE (...)", "DETAILS" in the problem
        if (trimmed.startsWith("--") || trimmed.matches("[A-Z]{4,}( [A-Z]{2,})*( \\(.*)?")) return YELLOW;
        if (trimmed.length() > 3 && trimmed.equals(trimmed.toUpperCase(Locale.ROOT))
            && !trimmed.equals(trimmed.toLowerCase(Locale.ROOT))) return YELLOW;
        if (trimmed.contains("Exception") || trimmed.contains("Error:")) return RED;
        if (trimmed.startsWith("Mods in trace") || trimmed.startsWith("Root cause")) return ORANGE;
        return theme.text;
    }

    // ---- input

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return;
        for (Button b : buttons) {
            if (UI.inside(mouseX, mouseY, b.x, b.y, b.w, BUTTON_H)) {
                UI.playClick();
                b.action.run();
                return;
            }
        }
        int tabX = textX;
        for (int i = 0; i < tabNames.length; i++) {
            int w = fontRendererObj.getStringWidth(tabNames[i]) + 20;
            if (UI.inside(mouseX, mouseY, tabX, textY - BUTTON_H - 2, w, BUTTON_H)) {
                UI.playClick();
                tab = i;
                return;
            }
            tabX += w + 4;
        }
        if (maxScroll(tab) > 0 && UI.inside(mouseX, mouseY, textX + textW - 10, textY, 10, textH)) {
            draggingScrollbar = true;
            dragScrollbar(mouseY);
        }
    }

    private void dragScrollbar(int mouseY) {
        int trackH = textH - 4;
        int thumbH = thumbHeight(trackH);
        float t = (mouseY - textY - 2 - thumbH / 2F) / Math.max(1, trackH - thumbH);
        scrollTarget[tab] = scroll[tab] = Math.max(0, Math.min(1, t)) * maxScroll(tab);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) scrollBy(-Integer.signum(wheel) * LINE * 4);
    }

    private void scrollBy(float amount) {
        scrollTarget[tab] += amount;
        clampScroll(tab);
    }

    @Override
    protected void keyTyped(char c, int code) {
        switch (code) {
            case Keyboard.KEY_ESCAPE:
                // only back to the menu after a crash in game; a failed start has nothing to go back to
                if (info.canReturnToMenu()) mc.displayGuiScreen(new GuiMainMenu());
                return;
            case Keyboard.KEY_UP:
                scrollBy(-LINE);
                return;
            case Keyboard.KEY_DOWN:
                scrollBy(LINE);
                return;
            case Keyboard.KEY_PRIOR:
                scrollBy(-(textH - LINE * 2));
                return;
            case Keyboard.KEY_NEXT:
                scrollBy(textH - LINE * 2);
                return;
            case Keyboard.KEY_HOME:
                scrollTarget[tab] = 0;
                return;
            case Keyboard.KEY_END:
                scrollTarget[tab] = maxScroll(tab);
                return;
            case Keyboard.KEY_TAB:
                tab = (tab + 1) % tabNames.length;
                return;
            default:
                if (code == Keyboard.KEY_C && isCtrlKeyDown()) copy();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
