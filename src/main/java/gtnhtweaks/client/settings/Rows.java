package gtnhtweaks.client.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiTextField;
import net.minecraftforge.common.config.Property;

import org.lwjgl.input.Keyboard;

import gtnhtweaks.common.render.GTNHTheme;

/** The rows of the settings list: notices, buttons, the headers of search results and settings with their controls. */
final class Rows {

    static final int WARNING = 0xFFFFC040;
    static final int ERROR = 0xFFE0453A;
    /** The color of the restart tag and of the restart line of the tooltips. */
    static final int RESTART = 0xFFE09A30;
    private static final int HOVER = 0x14FFFFFF;
    /** Width the tooltip text is wrapped to. */
    private static final int TOOLTIP_WIDTH = 220;

    private Rows() {}

    abstract static class Row {

        abstract int height();

        abstract void draw(int x, int y, int w, int mouseX, int mouseY, float dtMs);

        /** @return true if the click was used */
        boolean click(int x, int y, int w, int mouseX, int mouseY, int button) {
            return false;
        }

        /** @return true if the key was used */
        boolean key(char c, int code) {
            return false;
        }

        /** Tooltip lines while the mouse is over the row, or null. */
        List<String> tooltip(int x, int y, int w, int mouseX, int mouseY) {
            return null;
        }

        boolean matches(String query) {
            return false;
        }

        /** Writes a pending text value to its property. */
        void commit() {}
    }

    /** A line of warning, with a button that fixes it. */
    static final class NoticeRow extends Row {

        private static final int BUTTON_WIDTH = 60;

        private final String text;
        private final String buttonLabel;
        private final Runnable action;

        NoticeRow(String text, String buttonLabel, Runnable action) {
            this.text = text;
            this.buttonLabel = buttonLabel;
            this.action = action;
        }

        @Override
        int height() {
            return 24;
        }

        @Override
        void draw(int x, int y, int w, int mouseX, int mouseY, float dtMs) {
            UI.rect(x + 4, y + 2, w - 8, 20, 0x30FFB000);
            UI.rect(x + 4, y + 2, 2, 20, WARNING);
            int textW = w - 20 - (buttonLabel != null ? BUTTON_WIDTH + 6 : 0);
            UI.text(
                UI.font()
                    .trimStringToWidth(text, textW),
                x + 11,
                y + 8,
                WARNING);
            if (buttonLabel != null) {
                UI.button(x + w - BUTTON_WIDTH - 8, y + 4, BUTTON_WIDTH, 16, buttonLabel, mouseX, mouseY, true);
            }
        }

        @Override
        boolean click(int x, int y, int w, int mouseX, int mouseY, int button) {
            if (buttonLabel == null || !UI.inside(mouseX, mouseY, x + w - BUTTON_WIDTH - 8, y + 4, BUTTON_WIDTH, 16)) {
                return false;
            }
            UI.playClick();
            action.run();
            return true;
        }

        @Override
        List<String> tooltip(int x, int y, int w, int mouseX, int mouseY) {
            if (!UI.inside(mouseX, mouseY, x, y, w - BUTTON_WIDTH - 8, height())) return null;
            return wrap(text);
        }
    }

    /** The module and the tab over the settings found by the search; a click opens the tab. */
    static final class ResultsHeaderRow extends Row {

        private final SettingsScreen screen;
        private final int page, tab;
        private final String title;

        ResultsHeaderRow(SettingsScreen screen, int page, int tab, String title) {
            this.screen = screen;
            this.page = page;
            this.tab = tab;
            this.title = title;
        }

        @Override
        int height() {
            return 20;
        }

        @Override
        void draw(int x, int y, int w, int mouseX, int mouseY, float dtMs) {
            GTNHTheme theme = UI.theme();
            boolean hovered = UI.inside(mouseX, mouseY, x + 2, y + 2, w - 4, 16);
            if (hovered) UI.rect(x + 2, y + 2, w - 4, 16, HOVER);
            UI.text(title, x + 8, y + 6, theme.logo);
            String open = "Open »";
            UI.text(
                open,
                x + w
                    - 8
                    - UI.font()
                        .getStringWidth(open),
                y + 6,
                hovered ? theme.hoverText : theme.textDisabled);
        }

        @Override
        boolean click(int x, int y, int w, int mouseX, int mouseY, int button) {
            if (button != 0 || !UI.inside(mouseX, mouseY, x + 2, y + 2, w - 4, 16)) return false;
            UI.playClick();
            screen.openPage(page, tab);
            return true;
        }
    }

    /** A button that opens a screen of the module, for settings edited there. */
    static final class ButtonRow extends Row {

        private final String label;
        private final Runnable action;

        ButtonRow(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }

        @Override
        int height() {
            return 22;
        }

        private int width() {
            return UI.font()
                .getStringWidth(label) + 20;
        }

        @Override
        void draw(int x, int y, int w, int mouseX, int mouseY, float dtMs) {
            UI.button(x + 8, y + 3, width(), 16, label, mouseX, mouseY, true);
        }

        @Override
        boolean click(int x, int y, int w, int mouseX, int mouseY, int button) {
            if (!UI.inside(mouseX, mouseY, x + 8, y + 3, width(), 16)) return false;
            UI.playClick();
            action.run();
            return true;
        }
    }

    /**
     * One setting: its name with a line of what it does under it, the control at the right and a reset button when it
     * differs from the default. A setting that needs a restart has a tag after its name. A setting of a tab whose main
     * switch is off is drawn dimmed: it does nothing until the switch is on.
     */
    static final class SettingRow extends Row {

        static final int HEIGHT = 30;
        static final int RESET_SIZE = 14;
        private static final String RESTART_TAG = "restart";

        final Property property;
        final String label;
        /** Where the setting is, as "Module › Tab", for the search results and the restart list. */
        final String where;
        final String info;
        final SettingsText.Restart restart;
        private final String file;
        private final Control control;
        private final SettingsScreen screen;
        /** The main switch of the tab, or null when the tab has none or this is it. */
        Property master;
        String masterName;

        SettingRow(SettingsScreen screen, String where, String file, Property property, SettingsText.Setting text,
            int controlWidth) {
            this.screen = screen;
            this.where = where;
            this.file = file;
            this.property = property;
            this.label = text != null ? text.name : UI.humanize(property.getName());
            String comment = property.comment == null ? "" : property.comment.trim();
            this.info = text != null ? text.info : comment;
            this.restart = text != null ? text.restart : SettingsText.Restart.DEFAULT;
            this.control = Control.of(screen, property, controlWidth, text);
        }

        @Override
        int height() {
            return HEIGHT;
        }

        private int controlX(int x, int w) {
            return x + w - control.width - RESET_SIZE - 10;
        }

        /** True while the main switch of the tab is off. */
        boolean inactive() {
            return master != null && !master.getBoolean(true);
        }

        boolean needsRestart() {
            return restartLine(restart, property) != null;
        }

        @Override
        void draw(int x, int y, int w, int mouseX, int mouseY, float dtMs) {
            GTNHTheme theme = UI.theme();
            boolean hovered = UI.inside(mouseX, mouseY, x, y, w, HEIGHT);
            if (hovered) UI.rect(x + 2, y, w - 4, HEIGHT, HOVER);
            boolean changed = !property.isDefault();
            if (changed) UI.rect(x + 2, y + 4, 2, HEIGHT - 8, theme.barFill);
            boolean off = inactive();

            int labelX = x + 10;
            int labelW = controlX(x, w) - labelX - 8;
            int tagW = needsRestart() ? UI.font()
                .getStringWidth(RESTART_TAG) + 6 : 0;
            String shown = UI.font()
                .trimStringToWidth(label, labelW - (tagW > 0 ? tagW + 4 : 0));
            boolean twoLines = !info.isEmpty();
            int nameY = twoLines ? y + 6 : y + (HEIGHT - 8) / 2;
            drawHighlighted(shown, labelX, nameY, off ? theme.textDisabled : theme.text, screen.query());
            if (tagW > 0) {
                // a little tag: the change applies after a restart
                int tx = labelX + UI.font()
                    .getStringWidth(shown) + 5;
                UI.rect(tx, nameY - 1, tagW, 10, 0x30E09A30);
                UI.text(RESTART_TAG, tx + 3, nameY, RESTART);
            }
            if (twoLines) {
                String hint = UI.font()
                    .trimStringToWidth(info.replace('\n', ' '), labelW);
                if (hint.length() < info.length()) {
                    hint = UI.font()
                        .trimStringToWidth(info.replace('\n', ' '), labelW - 8) + "...";
                }
                UI.text(hint, labelX, y + 17, off ? theme.textDisabled : theme.textDim);
            }

            control.draw(controlX(x, w), y, HEIGHT, mouseX, mouseY, dtMs);

            if (changed) {
                int rx = x + w - RESET_SIZE - 6, ry = y + (HEIGHT - RESET_SIZE) / 2;
                UI.button(rx, ry, RESET_SIZE, RESET_SIZE, "↺", mouseX, mouseY, true);
            }
        }

        /** Puts the default value back; {@code apply} saves and applies it at once. */
        void reset(boolean apply) {
            if (property.isDefault()) return;
            screen.beforeChange(property);
            property.setToDefault();
            control.refresh();
            if (apply) screen.afterChange(property);
        }

        @Override
        boolean click(int x, int y, int w, int mouseX, int mouseY, int button) {
            int rx = x + w - RESET_SIZE - 6, ry = y + (HEIGHT - RESET_SIZE) / 2;
            if (!property.isDefault() && UI.inside(mouseX, mouseY, rx, ry, RESET_SIZE, RESET_SIZE)) {
                reset(true);
                UI.playClick();
                return true;
            }
            return control.click(controlX(x, w), y, HEIGHT, mouseX, mouseY, button);
        }

        @Override
        boolean key(char c, int code) {
            return control.key(c, code);
        }

        @Override
        void commit() {
            control.commit();
        }

        @Override
        List<String> tooltip(int x, int y, int w, int mouseX, int mouseY) {
            int rx = x + w - RESET_SIZE - 6, ry = y + (HEIGHT - RESET_SIZE) / 2;
            if (!property.isDefault() && UI.inside(mouseX, mouseY, rx, ry, RESET_SIZE, RESET_SIZE)) {
                List<String> lines = new ArrayList<>();
                lines.add("§eReset to default");
                lines.add("§7Default: §f" + defaultText(property));
                return lines;
            }
            int cx = controlX(x, w);
            if (UI.inside(mouseX, mouseY, cx, y, control.width, HEIGHT)) return control.tooltip();
            if (!UI.inside(mouseX, mouseY, x, y, cx - x, HEIGHT)) return null;
            return description();
        }

        /** The name, what the setting does, its default, whether it needs a restart and where it is kept. */
        List<String> description() {
            List<String> lines = new ArrayList<>();
            lines.add("§e" + label);
            if (!info.isEmpty()) lines.addAll(wrap(info));
            if (inactive()) lines.add("§8Works only while " + masterName + " is on");
            lines.add("");
            lines.add("§7Default: §f" + defaultText(property));
            String restartLine = restartLine(restart, property);
            if (restartLine != null) lines.add(restartLine);
            lines.add("§8" + file + " › " + property.getName());
            return lines;
        }

        @Override
        boolean matches(String query) {
            String q = query.toLowerCase(Locale.ROOT);
            return label.toLowerCase(Locale.ROOT)
                .contains(q)
                || info.toLowerCase(Locale.ROOT)
                    .contains(q)
                || property.getName()
                    .toLowerCase(Locale.ROOT)
                    .contains(q)
                || property.comment != null && property.comment.toLowerCase(Locale.ROOT)
                    .contains(q);
        }
    }

    /** Draws the text with the first match of the search in the accent color. */
    static void drawHighlighted(String text, int x, int y, int color, String query) {
        int at = query.isEmpty() ? -1
            : text.toLowerCase(Locale.ROOT)
                .indexOf(query.toLowerCase(Locale.ROOT));
        if (at < 0) {
            UI.text(text, x, y, color);
            return;
        }
        int end = Math.min(text.length(), at + query.length());
        String before = text.substring(0, at), match = text.substring(at, end);
        int matchX = x + UI.font()
            .getStringWidth(before);
        int matchW = UI.font()
            .getStringWidth(match);
        UI.rect(matchX - 1, y - 1, matchW + 1, 10, 0x40FFD866);
        UI.text(before, x, y, color);
        UI.text(match, matchX, y, 0xFFFFD866);
        UI.text(text.substring(end), matchX + matchW, y, color);
    }

    static String defaultText(Property property) {
        if (property.isList()) {
            String[] values = property.getDefaults();
            return values.length == 0 ? "empty list" : String.join(", ", values);
        }
        String value = property.getDefault();
        if (property.getType() == Property.Type.BOOLEAN) return Boolean.parseBoolean(value) ? "on" : "off";
        if (property.getValidValues() != null && property.getValidValues().length > 0) return valueName(value);
        return value == null || value.isEmpty() ? "empty" : value;
    }

    /** A value of a fixed set as words: BOTTOM_RIGHT is "Bottom right". */
    static String valueName(String value) {
        if (value == null || value.isEmpty() || !value.equals(value.toUpperCase(Locale.ROOT))) return value;
        String words = value.replace('_', ' ')
            .toLowerCase(Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /** The text in gray, wrapped to the width of the tooltips. */
    static List<String> wrap(String text) {
        return wrap(text, "§7");
    }

    @SuppressWarnings("unchecked")
    static List<String> wrap(String text, String format) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n")) {
            lines.addAll(
                UI.font()
                    .listFormattedStringToWidth(format + line, TOOLTIP_WIDTH));
        }
        return lines;
    }

    /** The line of a tooltip telling that a change needs a restart, or null when it applies at once. */
    static String restartLine(SettingsText.Restart restart, Property property) {
        switch (restart) {
            case ALWAYS:
                return "§6⚠ Requires a game restart";
            case TURN_ON:
                return "§6⚠ Turning it on requires a game restart";
            case DEFAULT:
                return property.requiresMcRestart() ? "§6⚠ Requires a game restart" : null;
            default:
                return null;
        }
    }

    // ---- numbers

    /** The lower and upper limit of a number property, or null when it has none or is not a number. */
    static double[] range(Property property) {
        boolean number = property.getType() == Property.Type.INTEGER || property.getType() == Property.Type.DOUBLE;
        // getFloat keeps floats as text properties with limits
        boolean decimalText = property.getType() == Property.Type.STRING && isNumber(property.getMinValue())
            && isNumber(property.getMaxValue())
            && isNumber(property.getDefault());
        if (!number && !decimalText) return null;
        if (!isNumber(property.getMinValue()) || !isNumber(property.getMaxValue())) return null;
        double min = Double.parseDouble(property.getMinValue()), max = Double.parseDouble(property.getMaxValue());
        return min < max ? new double[] { min, max } : null;
    }

    static boolean isDecimal(Property property) {
        return property.getType() != Property.Type.INTEGER && property.getType() != Property.Type.BOOLEAN;
    }

    private static boolean isNumber(String text) {
        if (text == null) return false;
        try {
            double value = Double.parseDouble(text.trim());
            return !Double.isNaN(value) && !Double.isInfinite(value);
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---- controls

    /** The editor of one property, drawn at the right side of its row. */
    abstract static class Control {

        final SettingsScreen screen;
        final Property property;
        final int width;

        Control(SettingsScreen screen, Property property, int width) {
            this.screen = screen;
            this.property = property;
            this.width = width;
        }

        static Control of(SettingsScreen screen, Property property, int width, SettingsText.Setting text) {
            if (property.isList()) return new ListControl(screen, property, width);
            if (property.getType() == Property.Type.BOOLEAN) return new ToggleControl(screen, property, width);
            String[] valid = property.getValidValues();
            if (valid != null && valid.length > 0) {
                return new CycleControl(screen, property, width, valid, text == null ? null : text.values);
            }
            double[] range = range(property);
            if (range != null && SliderControl.fits(property, range)) {
                return new SliderControl(screen, property, width, range);
            }
            return new TextControl(screen, property, width);
        }

        abstract void draw(int x, int y, int h, int mouseX, int mouseY, float dtMs);

        boolean click(int x, int y, int h, int mouseX, int mouseY, int button) {
            return false;
        }

        boolean key(char c, int code) {
            return false;
        }

        /** Follows the mouse while the button is held after {@link SettingsScreen#startDrag}. */
        void drag(int mouseX) {}

        void release() {}

        List<String> tooltip() {
            return null;
        }

        /** Shows the value of the property again, after it was reset. */
        void refresh() {}

        void commit() {}

        void set(Runnable change) {
            screen.beforeChange(property);
            change.run();
            screen.afterChange(property);
        }
    }

    /** On/off switch, sliding when clicked. */
    static final class ToggleControl extends Control {

        private float position;

        ToggleControl(SettingsScreen screen, Property property, int width) {
            super(screen, property, width);
            position = property.getBoolean(false) ? 1 : 0;
        }

        private int switchX(int x) {
            return x + width - 22;
        }

        @Override
        void draw(int x, int y, int h, int mouseX, int mouseY, float dtMs) {
            boolean on = property.getBoolean(false);
            position = UI.approach(position, on ? 1 : 0, dtMs, 120);
            boolean hovered = UI.inside(mouseX, mouseY, x, y, width, h);
            GTNHTheme theme = UI.theme();
            String state = on ? "On" : "Off";
            UI.text(
                state,
                switchX(x) - 6
                    - UI.font()
                        .getStringWidth(state),
                y + (h - 8) / 2,
                on ? UI.ON : theme.textDim);
            UI.toggle(switchX(x), y + (h - 10) / 2, position, hovered);
        }

        @Override
        boolean click(int x, int y, int h, int mouseX, int mouseY, int button) {
            if (!UI.inside(mouseX, mouseY, x, y, width, h)) return false;
            set(() -> property.set(!property.getBoolean(false)));
            UI.playClick();
            return true;
        }
    }

    /** One of a fixed set of values: click for the next one, right click or the left arrow for the previous one. */
    static final class CycleControl extends Control {

        private final String[] values;
        /** What each value does, or null. */
        private final java.util.Map<String, String> descriptions;

        CycleControl(SettingsScreen screen, Property property, int width, String[] values,
            java.util.Map<String, String> descriptions) {
            super(screen, property, width);
            this.values = values;
            this.descriptions = descriptions;
        }

        @Override
        void draw(int x, int y, int h, int mouseX, int mouseY, float dtMs) {
            int by = y + (h - 16) / 2;
            boolean hovered = UI.inside(mouseX, mouseY, x, by, width, 16);
            UI.button(x, by, width, 16, hovered, true);
            GTNHTheme theme = UI.theme();
            int color = hovered ? theme.hoverText : theme.text;
            boolean left = hovered && mouseX < x + width / 4, right = hovered && mouseX >= x + width / 4;
            UI.text("<", x + 5, by + 4, left ? theme.hoverText : theme.textDim);
            UI.text(">", x + width - 10, by + 4, right ? theme.hoverText : theme.textDim);
            String value = UI.font()
                .trimStringToWidth(valueName(property.getString()), width - 28);
            UI.text(
                value,
                x + (width - UI.font()
                    .getStringWidth(value)) / 2,
                by + 4,
                color);
            // position of the value among the others
            int index = Arrays.asList(values)
                .indexOf(property.getString());
            if (values.length > 1 && values.length <= 12 && index >= 0) {
                int dotsW = values.length * 3 - 1;
                int dx = x + (width - dotsW) / 2;
                for (int i = 0; i < values.length; i++) {
                    UI.rect(dx + i * 3, by + 13, 2, 1, i == index ? theme.barFill : 0x40FFFFFF);
                }
            }
        }

        @Override
        boolean click(int x, int y, int h, int mouseX, int mouseY, int button) {
            if (!UI.inside(mouseX, mouseY, x, y, width, h)) return false;
            int index = Arrays.asList(values)
                .indexOf(property.getString());
            boolean back = button == 1 || mouseX < x + width / 4;
            int next = ((index < 0 ? 0 : index) + (back ? values.length - 1 : 1)) % values.length;
            set(() -> property.set(values[next]));
            UI.playClick();
            return true;
        }

        @Override
        List<String> tooltip() {
            List<String> lines = new ArrayList<>();
            for (String value : values) {
                boolean current = value.equals(property.getString());
                String description = descriptions == null ? null : descriptions.get(value);
                lines.add(
                    (current ? "§e» " : "§7   ") + valueName(value)
                        + (description == null ? "" : (current ? " §7- " : " §8- ") + description));
            }
            lines.add("");
            lines.add("§8Click: next  ·  Right click: previous");
            return lines;
        }
    }

    /** A number between two limits: dragged, or typed after a right click. */
    static final class SliderControl extends Control {

        private final double min, max, step;
        private final boolean decimal;
        private final int decimals;
        private final TextControl text;
        private boolean typing;
        private int lastX;

        SliderControl(SettingsScreen screen, Property property, int width, double[] range) {
            super(screen, property, width);
            this.min = range[0];
            this.max = range[1];
            this.decimal = isDecimal(property);
            this.step = decimal ? niceStep((max - min) / 100) : 1;
            this.decimals = decimal ? Math.max(0, (int) Math.ceil(-Math.log10(step) - 1e-9)) : 0;
            this.text = new TextControl(screen, property, width);
        }

        /** Whether the range is small enough to be dragged through. */
        static boolean fits(Property property, double[] range) {
            double span = range[1] - range[0];
            return isDecimal(property) ? span <= 100_000 : span <= 1000;
        }

        /** The smallest 1, 2 or 5 times a power of ten that is at least {@code raw}. */
        private static double niceStep(double raw) {
            double power = Math.pow(10, Math.floor(Math.log10(raw)));
            for (double factor : new double[] { 1, 2, 5, 10 }) {
                if (factor * power >= raw - 1e-12) return factor * power;
            }
            return 10 * power;
        }

        private double value() {
            try {
                return Double.parseDouble(
                    property.getString()
                        .trim());
            } catch (RuntimeException e) {
                return min;
            }
        }

        private String format(double value) {
            return decimal ? String.format(Locale.ROOT, "%." + decimals + "f", value)
                : Long.toString(Math.round(value));
        }

        @Override
        void draw(int x, int y, int h, int mouseX, int mouseY, float dtMs) {
            lastX = x;
            if (typing) {
                text.draw(x, y, h, mouseX, mouseY, dtMs);
                return;
            }
            GTNHTheme theme = UI.theme();
            double value = Math.max(min, Math.min(max, value()));
            float t = (float) ((value - min) / (max - min));
            int by = y + (h - 16) / 2;
            boolean hovered = screen.isDragging(this) || UI.inside(mouseX, mouseY, x, by, width, 16);
            UI.inset(x, by, width, 16);
            int fill = Math.round((width - 2) * t);
            UI.rect(x + 1, by + 1, fill, 14, (theme.barFill & 0x00FFFFFF) | (hovered ? 0x90000000 : 0x60000000));
            int knob = x + 1 + Math.max(0, Math.min(width - 6, fill - 2));
            UI.button(knob, by + 1, 4, 14, hovered, true);
            String shown = format(value);
            UI.text(
                shown,
                x + (width - UI.font()
                    .getStringWidth(shown)) / 2,
                by + 4,
                hovered ? theme.hoverText : theme.text);
        }

        @Override
        boolean click(int x, int y, int h, int mouseX, int mouseY, int button) {
            if (typing) {
                boolean used = text.click(x, y, h, mouseX, mouseY, button);
                if (!used) typing = false;
                return used;
            }
            int by = y + (h - 16) / 2;
            if (!UI.inside(mouseX, mouseY, x, by, width, 16)) return false;
            if (button == 1) {
                typing = true;
                text.refresh();
                text.focus();
                return true;
            }
            if (button != 0) return false;
            screen.beforeChange(property);
            screen.startDrag(this);
            drag(mouseX);
            return true;
        }

        @Override
        void drag(int mouseX) {
            double t = Math.max(0, Math.min(1, (mouseX - lastX - 1) / (double) (width - 2)));
            double value = min + (max - min) * t;
            value = Math.round(value / step) * step;
            value = Math.max(min, Math.min(max, value));
            String formatted = format(value);
            // the default as it is written, so the setting does not count as changed
            try {
                if (Math.abs(Double.parseDouble(property.getDefault()) - value) < step / 2) {
                    formatted = property.getDefault();
                }
            } catch (RuntimeException ignored) {}
            if (!formatted.equals(property.getString())) property.set(formatted);
        }

        @Override
        void release() {
            screen.afterChange(property);
        }

        @Override
        boolean key(char c, int code) {
            if (!typing) return false;
            boolean used = text.key(c, code);
            if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER || code == Keyboard.KEY_ESCAPE) {
                typing = false;
            }
            return used;
        }

        @Override
        List<String> tooltip() {
            List<String> lines = new ArrayList<>();
            lines.add("§7From " + format(min) + " to " + format(max));
            lines.add("");
            lines.add("§8Drag to change  ·  Right click to type a value");
            return lines;
        }

        @Override
        void refresh() {
            text.refresh();
            typing = false;
        }

        @Override
        void commit() {
            if (typing) text.commit();
        }
    }

    /** Text, whole or decimal number. Invalid values get a red border and are not saved. */
    static final class TextControl extends Control {

        private final GuiTextField field;
        private boolean valid = true;

        TextControl(SettingsScreen screen, Property property, int width) {
            super(screen, property, width);
            field = new GuiTextField(UI.font(), 0, 0, width - 8, 12);
            field.setEnableBackgroundDrawing(false);
            field.setMaxStringLength(1024);
            refresh();
        }

        void focus() {
            field.setFocused(true);
            field.setCursorPositionEnd();
            field.setSelectionPos(0);
        }

        @Override
        void refresh() {
            field.setText(property.getString());
            field.setCursorPositionZero();
            valid = true;
        }

        @Override
        void draw(int x, int y, int h, int mouseX, int mouseY, float dtMs) {
            int by = y + (h - 16) / 2;
            UI.inset(x, by, width, 16);
            GTNHTheme theme = UI.theme();
            if (!valid) UI.rect(x, by + 15, width, 1, ERROR);
            else if (field.isFocused()) UI.rect(x, by + 15, width, 1, theme.barFill);
            field.xPosition = x + 4;
            field.yPosition = by + 4;
            field.setTextColor(valid ? theme.text & 0xFFFFFF : 0xFF8080);
            field.drawTextBox();
        }

        @Override
        boolean click(int x, int y, int h, int mouseX, int mouseY, int button) {
            int by = y + (h - 16) / 2;
            boolean inside = UI.inside(mouseX, mouseY, x, by, width, 16);
            if (!inside && field.isFocused()) commit();
            field.setFocused(inside);
            if (inside) {
                if (button == 1) {
                    field.setText("");
                    valid = parse("") != null;
                }
                field.mouseClicked(mouseX, mouseY, button);
            }
            return inside;
        }

        @Override
        boolean key(char c, int code) {
            if (!field.isFocused()) return false;
            if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) {
                commit();
                field.setFocused(false);
                return true;
            }
            if (code == Keyboard.KEY_ESCAPE) {
                refresh();
                field.setFocused(false);
                return true;
            }
            field.textboxKeyTyped(c, code);
            valid = parse(field.getText()) != null;
            return true;
        }

        @Override
        List<String> tooltip() {
            if (valid) return null;
            List<String> lines = new ArrayList<>();
            lines.add("§cNot a valid value: it is not saved");
            double[] range = range(property);
            if (range != null) lines.add("§7From " + Rows.trim(range[0]) + " to " + Rows.trim(range[1]));
            return lines;
        }

        /** The value to store, or null if the text is not valid for the property. */
        private String parse(String text) {
            String value = text.trim();
            try {
                if (property.getType() == Property.Type.INTEGER) {
                    int number = Integer.parseInt(value);
                    if (number < bound(property.getMinValue(), Integer.MIN_VALUE)
                        || number > bound(property.getMaxValue(), Integer.MAX_VALUE)) return null;
                    return Integer.toString(number);
                }
                if (property.getType() == Property.Type.DOUBLE || range(property) != null) {
                    double number = Double.parseDouble(value);
                    if (Double.isNaN(number) || number < bound(property.getMinValue(), -Double.MAX_VALUE)
                        || number > bound(property.getMaxValue(), Double.MAX_VALUE)) return null;
                    return property.getType() == Property.Type.DOUBLE ? Double.toString(number) : value;
                }
            } catch (RuntimeException e) {
                return null;
            }
            return text;
        }

        /** A limit of the property, or {@code fallback} when it has none. */
        private static double bound(String limit, double fallback) {
            try {
                return limit == null ? fallback : Double.parseDouble(limit);
            } catch (RuntimeException e) {
                return fallback;
            }
        }

        @Override
        void commit() {
            String value = parse(field.getText());
            if (value == null || value.equals(property.getString())) return;
            set(() -> property.set(value));
        }
    }

    /** A list of strings, edited in its own screen. */
    static final class ListControl extends Control {

        ListControl(SettingsScreen screen, Property property, int width) {
            super(screen, property, width);
        }

        @Override
        void draw(int x, int y, int h, int mouseX, int mouseY, float dtMs) {
            int count = property.getStringList().length;
            String label = "Edit list (" + count + ")";
            UI.button(x, y + (h - 16) / 2, width, 16, label, mouseX, mouseY, true);
        }

        @Override
        boolean click(int x, int y, int h, int mouseX, int mouseY, int button) {
            if (!UI.inside(mouseX, mouseY, x, y + (h - 16) / 2, width, 16)) return false;
            UI.playClick();
            screen.openList(property);
            return true;
        }

        @Override
        List<String> tooltip() {
            String[] values = property.getStringList();
            if (values.length == 0) return null;
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < Math.min(10, values.length); i++) lines.add("§7" + values[i]);
            if (values.length > 10) lines.add("§8and " + (values.length - 10) + " more");
            return lines;
        }
    }

    /** "2.0" -> "2", "0.5" -> "0.5". */
    static String trim(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1e15 ? Long.toString((long) value)
            : Double.toString(value);
    }
}
