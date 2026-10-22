package gtnhtweaks.client.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiTextField;
import net.minecraftforge.common.config.Property;

import org.lwjgl.input.Keyboard;

import gtnhtweaks.common.render.GTNHTheme;

/** The rows of the settings list: page titles, category headers and settings with their controls. */
final class Rows {

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

    /** Name and description of a module. */
    static final class TitleRow extends Row {

        private final String title;
        private final List<String> lines;

        @SuppressWarnings("unchecked")
        TitleRow(String title, String description, int width) {
            this.title = title;
            if (description == null || description.isEmpty()) this.lines = new ArrayList<>();
            else this.lines = UI.font()
                .listFormattedStringToWidth(description, width - 12);
        }

        @Override
        int height() {
            return 22 + lines.size() * 10;
        }

        @Override
        void draw(int x, int y, int w, int mouseX, int mouseY, float dtMs) {
            GTNHTheme theme = UI.theme();
            UI.text(title, x + 6, y + 6, theme.logo);
            for (int i = 0; i < lines.size(); i++) UI.text(lines.get(i), x + 6, y + 18 + i * 10, theme.textDim);
        }
    }

    /** Name of a config category, with its comment as the tooltip. */
    static final class HeaderRow extends Row {

        private final String title;
        private final String comment;

        HeaderRow(String title, String comment) {
            this.title = title;
            this.comment = comment;
        }

        @Override
        int height() {
            return 18;
        }

        @Override
        void draw(int x, int y, int w, int mouseX, int mouseY, float dtMs) {
            GTNHTheme theme = UI.theme();
            UI.text(title.toUpperCase(Locale.ROOT), x + 6, y + 7, theme.textDim);
            int lineX = x + 12 + UI.font()
                .getStringWidth(title.toUpperCase(Locale.ROOT));
            UI.rect(lineX, y + 11, x + w - 6 - lineX, 1, theme.isBeveled() ? theme.inset.topLeft : theme.barBorder);
        }

        @Override
        List<String> tooltip(int x, int y, int w, int mouseX, int mouseY) {
            if (comment == null || comment.isEmpty() || !UI.inside(mouseX, mouseY, x, y, w, height())) return null;
            return wrap(comment);
        }
    }

    /** One property: label on the left, control on the right, reset button when it differs from the default. */
    static final class SettingRow extends Row {

        static final int HEIGHT = 22;
        static final int RESET_SIZE = 14;

        final Property property;
        final String label;
        private final Control control;
        private final SettingsScreen screen;

        SettingRow(SettingsScreen screen, Property property, int controlWidth) {
            this.screen = screen;
            this.property = property;
            this.label = UI.humanize(property.getName());
            this.control = Control.of(screen, property, controlWidth);
        }

        @Override
        int height() {
            return HEIGHT;
        }

        private int controlX(int x, int w) {
            return x + w - control.width - RESET_SIZE - 10;
        }

        @Override
        void draw(int x, int y, int w, int mouseX, int mouseY, float dtMs) {
            GTNHTheme theme = UI.theme();
            boolean hovered = UI.inside(mouseX, mouseY, x, y, w, HEIGHT);
            if (hovered) UI.rect(x + 2, y, w - 4, HEIGHT, 0x14FFFFFF);
            boolean changed = !property.isDefault();
            if (changed) UI.rect(x + 2, y + 3, 2, HEIGHT - 6, theme.barFill);

            String shown = UI.font()
                .trimStringToWidth(label, controlX(x, w) - x - 14);
            UI.text(shown, x + 8, y + (HEIGHT - 8) / 2, theme.text);

            control.draw(controlX(x, w), y, HEIGHT, mouseX, mouseY, dtMs);

            if (changed) {
                int rx = x + w - RESET_SIZE - 6, ry = y + (HEIGHT - RESET_SIZE) / 2;
                UI.button(rx, ry, RESET_SIZE, RESET_SIZE, "↺", mouseX, mouseY, true);
            }
        }

        @Override
        boolean click(int x, int y, int w, int mouseX, int mouseY, int button) {
            int rx = x + w - RESET_SIZE - 6, ry = y + (HEIGHT - RESET_SIZE) / 2;
            if (!property.isDefault() && UI.inside(mouseX, mouseY, rx, ry, RESET_SIZE, RESET_SIZE)) {
                screen.beforeChange(property);
                property.setToDefault();
                control.refresh();
                screen.afterChange(property);
                SettingsScreen.playClick();
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
            if (!UI.inside(mouseX, mouseY, x, y, controlX(x, w) - x, HEIGHT)) return null;
            List<String> lines = new ArrayList<>();
            lines.add("§e" + label);
            if (property.comment != null && !property.comment.isEmpty()) lines.addAll(wrap(property.comment));
            lines.add("§8" + property.getName() + ", default: " + defaultText(property));
            if (property.requiresMcRestart()) lines.add("§6Needs a game restart");
            return lines;
        }

        @Override
        boolean matches(String query) {
            String q = query.toLowerCase(Locale.ROOT);
            return label.toLowerCase(Locale.ROOT)
                .contains(q)
                || property.getName()
                    .toLowerCase(Locale.ROOT)
                    .contains(q)
                || property.comment != null && property.comment.toLowerCase(Locale.ROOT)
                    .contains(q);
        }
    }

    static String defaultText(Property property) {
        if (property.isList()) return Arrays.toString(property.getDefaults());
        String value = property.getDefault();
        return value == null || value.isEmpty() ? "empty" : value;
    }

    @SuppressWarnings("unchecked")
    static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n")) {
            lines.addAll(
                UI.font()
                    .listFormattedStringToWidth("§7" + line, 240));
        }
        return lines;
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

        static Control of(SettingsScreen screen, Property property, int width) {
            if (property.isList()) return new ListControl(screen, property, width);
            if (property.getType() == Property.Type.BOOLEAN) return new ToggleControl(screen, property, width);
            String[] valid = property.getValidValues();
            if (valid != null && valid.length > 0) return new CycleControl(screen, property, width, valid);
            return new TextControl(screen, property, width);
        }

        abstract void draw(int x, int y, int h, int mouseX, int mouseY, float dtMs);

        boolean click(int x, int y, int h, int mouseX, int mouseY, int button) {
            return false;
        }

        boolean key(char c, int code) {
            return false;
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
            boolean hovered = UI.inside(mouseX, mouseY, switchX(x) - 2, y, 26, h);
            GTNHTheme theme = UI.theme();
            String state = on ? "On" : "Off";
            UI.text(
                state,
                switchX(x) - 6 - UI.font()
                    .getStringWidth(state),
                y + (h - 8) / 2,
                on ? theme.text : theme.textDim);
            UI.toggle(switchX(x), y + (h - 10) / 2, position, hovered);
        }

        @Override
        boolean click(int x, int y, int h, int mouseX, int mouseY, int button) {
            if (!UI.inside(mouseX, mouseY, x, y, width, h)) return false;
            set(() -> property.set(!property.getBoolean(false)));
            SettingsScreen.playClick();
            return true;
        }
    }

    /** One of a fixed set of values: click for the next one, right click for the previous one. */
    static final class CycleControl extends Control {

        private final String[] values;

        CycleControl(SettingsScreen screen, Property property, int width, String[] values) {
            super(screen, property, width);
            this.values = values;
        }

        @Override
        void draw(int x, int y, int h, int mouseX, int mouseY, float dtMs) {
            int by = y + (h - 16) / 2;
            boolean hovered = UI.inside(mouseX, mouseY, x, by, width, 16);
            UI.button(x, by, width, 16, hovered, true);
            GTNHTheme theme = UI.theme();
            int color = hovered ? theme.hoverText : theme.text;
            UI.text("<", x + 5, by + 4, theme.textDim);
            UI.text(">", x + width - 10, by + 4, theme.textDim);
            String value = UI.font()
                .trimStringToWidth(property.getString(), width - 28);
            UI.text(
                value,
                x + (width - UI.font()
                    .getStringWidth(value)) / 2,
                by + 4,
                color);
        }

        @Override
        boolean click(int x, int y, int h, int mouseX, int mouseY, int button) {
            if (!UI.inside(mouseX, mouseY, x, y, width, h)) return false;
            int index = Arrays.asList(values)
                .indexOf(property.getString());
            boolean back = button == 1 || mouseX < x + width / 4;
            int next = ((index < 0 ? 0 : index) + (back ? values.length - 1 : 1)) % values.length;
            set(() -> property.set(values[next]));
            SettingsScreen.playClick();
            return true;
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
            if (!valid) UI.rect(x, by + 15, width, 1, 0xFFE0453A);
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
            if (inside) field.mouseClicked(mouseX, mouseY, button);
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
            field.textboxKeyTyped(c, code);
            valid = parse(field.getText()) != null;
            return true;
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
                if (property.getType() == Property.Type.DOUBLE) {
                    double number = Double.parseDouble(value);
                    if (number < bound(property.getMinValue(), -Double.MAX_VALUE)
                        || number > bound(property.getMaxValue(), Double.MAX_VALUE)) return null;
                    return Double.toString(number);
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
            SettingsScreen.playClick();
            screen.openList(property);
            return true;
        }
    }
}
