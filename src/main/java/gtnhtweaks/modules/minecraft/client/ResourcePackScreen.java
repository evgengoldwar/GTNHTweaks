package gtnhtweaks.modules.minecraft.client;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.ResourcePackListEntry;
import net.minecraft.client.resources.ResourcePackListEntryDefault;
import net.minecraft.client.resources.ResourcePackListEntryFound;
import net.minecraft.client.resources.ResourcePackRepository;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.client.settings.PackBrowserScreen;
import gtnhtweaks.modules.minecraft.MinecraftModule;

/**
 * The resource pack screen of vanilla, made friendlier: the packs are dragged with the mouse by their name, from one
 * list to the other and to any place in a list, and the saved sets of packs are left of the lists, or behind a Presets
 * button when the screen has no room for them.
 * <p>
 * The lists and the entries are found by their types, not their names, so the unnamed fields of the screen are not
 * needed. Nothing is applied before Done, as in vanilla: only the lists of the screen change.
 */
public final class ResourcePackScreen {

    public static final int PRESETS_BUTTON = 0x47544E;
    public static final int BROWSE_BUTTON = 0x47544F;
    /** How far the mouse moves with the button down before the press becomes a drag. */
    private static final int DRAG_START = 4;
    /** Near this many pixels of the top or bottom of a list, a dragged pack scrolls it. */
    private static final int SCROLL_EDGE = 16;

    private static Field[] slotFields;
    private static Field[] listFields;
    private static Field entryField;
    private static Field parentField;

    /** The open screen and its lists, found on its init. */
    private static GuiScreen screen;
    private static GuiSlot availableSlot, selectedSlot;
    private static List<Object> available, selected;
    /** The entry of the default pack, taken out of the selected list while other packs are selected. */
    private static Object defaultEntry;

    /** The pack pressed on, then dragged. */
    private static ResourcePackListEntry held;
    private static int pressX, pressY, grabX, grabY;
    private static boolean dragging;
    private static long lastFrame;

    /** Order of the selected packs to put back when the screen comes back from the presets screen. */
    private static GuiScreen pendingScreen;
    private static List<String> pendingPacks;
    /** The selected packs as they are applied, the top one first, read when the screen builds its lists. */
    private static List<String> appliedPacks;
    /** Names of all the packs of the screen, selected or not. */
    private static List<String> knownPacks = Collections.emptyList();

    private ResourcePackScreen() {}

    // ---- hooks of the screen

    public static void init(GuiScreen gui, List<GuiButton> buttons) {
        held = null;
        dragging = false;
        screen = gui;
        find(gui);
        // vanilla builds the lists from the applied packs, so before they are arranged they show them
        appliedPacks = available == null ? null : selectedPacks();
        if (pendingScreen == gui && pendingPacks != null && available != null) arrange(pendingPacks);
        pendingScreen = null;
        pendingPacks = null;
        if (available == null) return;
        knownPacks = allPacks();
        syncDefault();
        if (MinecraftModule.resourcePackPresets
            && !PresetPanel.init(gui, buttons, availableSlot.left, availableSlot.top, availableSlot.bottom)) {
            buttons.add(new GuiButton(PRESETS_BUTTON, 4, 6, 70, 20, "Presets"));
        }
        if (MinecraftModule.packBrowser)
            buttons.add(new GuiButton(BROWSE_BUTTON, gui.width - 94, 6, 90, 20, "Get packs..."));
    }

    /** True if the press is taken by a pack about to be dragged, so vanilla does not handle it. */
    public static boolean mouseClicked(GuiScreen gui, int mouseX, int mouseY, int button) {
        PresetPanel.mouseClicked(gui, mouseX, mouseY, button);
        if (gui != screen || button != 0 || available == null || !MinecraftModule.dragResourcePacks) return false;
        for (int side = 0; side < 2; side++) {
            GuiSlot slot = side == 0 ? availableSlot : selectedSlot;
            List<Object> list = side == 0 ? available : selected;
            int index = rowAt(slot, list, mouseX, mouseY);
            if (index < 0) continue;
            Object entry = list.get(index);
            int rowX = rowLeft(slot);
            // the icon keeps its arrows of vanilla, and the default pack stays where it is
            if (!(entry instanceof ResourcePackListEntryFound) || mouseX - rowX <= 32) return false;
            held = (ResourcePackListEntry) entry;
            dragging = false;
            pressX = mouseX;
            pressY = mouseY;
            grabX = mouseX - rowX;
            grabY = mouseY - rowTop(slot, index);
            return true;
        }
        return false;
    }

    public static boolean actionPerformed(GuiScreen gui, GuiButton button) {
        // the vanilla Done button
        if (button.id == 1) return closeUnchanged(gui);
        if (PresetPanel.actionPerformed(gui, button)) return true;
        boolean browse = button.id == BROWSE_BUTTON;
        if (button.id != PRESETS_BUTTON && !browse || gui != screen || available == null) return false;
        // the screen builds its lists anew when it comes back: they are put back as they are now, and the packs
        // installed meanwhile come in with the others not selected
        pendingScreen = gui;
        pendingPacks = selectedPacks();
        Minecraft.getMinecraft()
            .displayGuiScreen(browse ? new PackBrowserScreen(gui) : new PackPresetsScreen(gui));
        return true;
    }

    /**
     * Done without a change of the selected packs or their order only goes back to the previous screen: vanilla would
     * reload all the resources for nothing, which takes long in a big pack.
     */
    private static boolean closeUnchanged(GuiScreen gui) {
        if (!MinecraftModule.skipReloadWithoutChanges || gui != screen || available == null || parentField == null) {
            return false;
        }
        if (appliedPacks == null || !appliedPacks.equals(selectedPacks())) return false;
        try {
            Minecraft.getMinecraft()
                .displayGuiScreen((GuiScreen) parentField.get(gui));
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            GTNHTweaks.LOG.warn("Could not find the previous screen of the resource pack screen", e);
            return false;
        }
    }

    /** Before the lists are drawn: the default pack follows the packs moved by the arrows of vanilla. */
    public static void beforeDraw(GuiScreen gui) {
        if (gui == screen) syncDefault();
    }

    /**
     * Hides the default pack while any other pack is selected: it is always under them, so its entry only takes room.
     * Done takes only the other packs, so the entry being out of the list changes nothing.
     */
    private static void syncDefault() {
        if (selected == null || defaultEntry == null) return;
        boolean anyPack = false;
        for (Object entry : selected) {
            if (entry instanceof ResourcePackListEntryFound) {
                anyPack = true;
                break;
            }
        }
        boolean hide = MinecraftModule.hideDefaultPack && anyPack;
        boolean shown = selected.contains(defaultEntry);
        if (hide && shown) selected.remove(defaultEntry);
        else if (!hide && !shown) selected.add(defaultEntry);
    }

    /** True if the key is taken by the presets. */
    public static boolean keyTyped(GuiScreen gui, char c, int code) {
        return held == null && PresetPanel.keyTyped(gui, c, code);
    }

    public static void update(GuiScreen gui) {
        PresetPanel.update(gui);
    }

    /** Draws the presets and the dragged pack; gives the tooltip to draw over the screen, or null. */
    public static List<String> draw(GuiScreen gui, int mouseX, int mouseY) {
        List<String> tooltip = PresetPanel.draw(gui, mouseX, mouseY);
        drawDrag(gui, mouseX, mouseY);
        return held == null ? tooltip : null;
    }

    private static void drawDrag(GuiScreen gui, int mouseX, int mouseY) {
        long now = Minecraft.getSystemTime();
        float frameMs = lastFrame == 0 ? 0 : Math.min(100, now - lastFrame);
        lastFrame = now;
        if (gui != screen || held == null) return;
        List<Object> from = listOf(held);
        if (from == null || !MinecraftModule.dragResourcePacks) {
            held = null;
            return;
        }
        if (!Mouse.isButtonDown(0)) {
            if (dragging) {
                drop(mouseX, mouseY);
                syncDefault();
            }
            held = null;
            dragging = false;
            return;
        }
        if (!dragging && Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) < DRAG_START) return;
        dragging = true;

        GuiSlot fromSlot = from == available ? availableSlot : selectedSlot;
        GL11.glColor4f(1, 1, 1, 1);
        // the place the pack is taken from is dimmed
        int fromTop = rowTop(fromSlot, from.indexOf(held));
        fillInList(fromSlot, fromTop, fromTop + fromSlot.slotHeight - 4, 0xA0000000);

        Target target = target(mouseX, mouseY);
        if (target != null) {
            int lineY = rowTop(target.slot, target.index) - 2;
            fillInList(target.slot, lineY - 1, lineY + 1, 0xFFFFFFFF);
        }

        int x = mouseX - grabX;
        int y = mouseY - grabY;
        int w = fromSlot.right - 6 - rowLeft(fromSlot);
        Gui.drawRect(x - 3, y - 3, x + w + 1, y + fromSlot.slotHeight - 3, 0xFFC0C0C0);
        Gui.drawRect(x - 2, y - 2, x + w, y + fromSlot.slotHeight - 4, 0xF0101010);
        GL11.glColor4f(1, 1, 1, 1);
        held.drawEntry(
            from.indexOf(held),
            x,
            y,
            fromSlot.width,
            fromSlot.slotHeight - 4,
            Tessellator.instance,
            Integer.MIN_VALUE / 2,
            Integer.MIN_VALUE / 2,
            false);
        GL11.glColor4f(1, 1, 1, 1);

        if (target != null) scroll(target.slot, mouseY, frameMs);
    }

    /** The mouse the lists of the screen see: held still where it was pressed while a pack is held. */
    public static int listMouseX(GuiSlot slot, int mouseX) {
        return held != null && isList(slot) ? pressX : mouseX;
    }

    public static int listMouseY(GuiSlot slot, int mouseY) {
        return held != null && isList(slot) ? pressY : mouseY;
    }

    // ---- presets

    /** Names of the selected packs of the screen, the top one first, as the presets keep them. */
    public static List<String> currentPacks() {
        return pendingPacks == null ? Collections.<String>emptyList() : new ArrayList<>(pendingPacks);
    }

    /** True if the pack is in the resource pack folder. */
    public static boolean isInstalled(String pack) {
        return knownPacks.contains(pack);
    }

    /** Selects the packs on the screen when it comes back, the top one first. Missing packs are skipped. */
    public static void choose(List<String> packs) {
        pendingPacks = new ArrayList<>(packs);
    }

    // ---- the lists

    private static void find(GuiScreen gui) {
        availableSlot = selectedSlot = null;
        available = selected = null;
        try {
            if (slotFields == null) findFields();
            GuiSlot first = (GuiSlot) slotFields[0].get(gui);
            GuiSlot second = (GuiSlot) slotFields[1].get(gui);
            List<Object> one = cast(listFields[0].get(gui));
            List<Object> other = cast(listFields[1].get(gui));
            if (first == null || second == null || one == null || other == null) return;
            // the selected list ends with the default pack; the available list is on the left
            boolean oneSelected = hasDefault(one);
            selected = oneSelected ? one : other;
            available = oneSelected ? other : one;
            availableSlot = first.left <= second.left ? first : second;
            selectedSlot = availableSlot == first ? second : first;
            defaultEntry = null;
            for (Object entry : selected) {
                if (entry instanceof ResourcePackListEntryDefault) defaultEntry = entry;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            GTNHTweaks.LOG.warn("Could not find the lists of the resource pack screen", e);
            available = selected = null;
        }
    }

    private static void findFields() {
        List<Field> slots = new ArrayList<>();
        List<Field> lists = new ArrayList<>();
        List<Field> parents = new ArrayList<>();
        for (Field field : GuiScreenResourcePacks.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (GuiSlot.class.isAssignableFrom(field.getType())) slots.add(field);
            else if (List.class.isAssignableFrom(field.getType())) lists.add(field);
            else if (field.getType() == GuiScreen.class) parents.add(field);
            else continue;
            field.setAccessible(true);
        }
        if (slots.size() != 2 || lists.size() != 2) {
            throw new IllegalStateException(slots.size() + " lists and " + lists.size() + " entry lists");
        }
        slotFields = slots.toArray(new Field[0]);
        listFields = lists.toArray(new Field[0]);
        // the screen to go back to; without it Done always applies the packs, as in vanilla
        parentField = parents.size() == 1 ? parents.get(0) : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> cast(Object list) {
        return (List<Object>) list;
    }

    private static boolean hasDefault(List<Object> list) {
        for (Object entry : list) {
            if (entry instanceof ResourcePackListEntryDefault) return true;
        }
        return false;
    }

    private static boolean isList(GuiSlot slot) {
        return slot != null && (slot == availableSlot || slot == selectedSlot);
    }

    private static List<Object> listOf(Object entry) {
        if (available == null) return null;
        if (available.contains(entry)) return available;
        return selected.contains(entry) ? selected : null;
    }

    /** Name of the pack of an entry, its file name, or null for the default pack. */
    private static String name(Object entry) {
        if (!(entry instanceof ResourcePackListEntryFound)) return null;
        try {
            if (entryField == null) {
                for (Field field : ResourcePackListEntryFound.class.getDeclaredFields()) {
                    if (field.getType() == ResourcePackRepository.Entry.class) {
                        field.setAccessible(true);
                        entryField = field;
                    }
                }
            }
            ResourcePackRepository.Entry pack = (ResourcePackRepository.Entry) entryField.get(entry);
            return pack == null ? null : pack.getResourcePackName();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    static List<String> selectedPacks() {
        List<String> names = new ArrayList<>();
        for (Object entry : selected) {
            String name = name(entry);
            if (name != null) names.add(name);
        }
        return names;
    }

    private static List<String> allPacks() {
        List<String> names = selectedPacks();
        for (Object entry : available) {
            String name = name(entry);
            if (name != null) names.add(name);
        }
        return names;
    }

    /** Selects the named packs in their order; the others go to the available list, keeping their order. */
    static void arrange(List<String> packs) {
        Map<String, Object> entries = new LinkedHashMap<>();
        List<Object> fixed = new ArrayList<>();
        for (Object entry : available) {
            String name = name(entry);
            if (name != null) entries.put(name, entry);
            else fixed.add(entry);
        }
        List<Object> defaults = new ArrayList<>();
        for (Object entry : selected) {
            String name = name(entry);
            if (name != null) entries.put(name, entry);
            else defaults.add(entry);
        }
        selected.clear();
        for (String name : packs) {
            Object entry = entries.remove(name);
            if (entry != null) selected.add(entry);
        }
        selected.addAll(defaults);
        available.clear();
        available.addAll(fixed);
        available.addAll(entries.values());
        syncDefault();
    }

    // ---- dragging

    /** Where a dragged pack goes: a list and the index it takes in it. */
    private static final class Target {

        final GuiSlot slot;
        final List<Object> list;
        final int index;

        Target(GuiSlot slot, List<Object> list, int index) {
            this.slot = slot;
            this.list = list;
            this.index = index;
        }
    }

    private static Target target(int mouseX, int mouseY) {
        GuiSlot slot;
        List<Object> list;
        if (mouseX >= availableSlot.left && mouseX < availableSlot.right) {
            slot = availableSlot;
            list = available;
        } else if (mouseX >= selectedSlot.left && mouseX < selectedSlot.right) {
            slot = selectedSlot;
            list = selected;
        } else {
            return null;
        }
        // the default pack stays at the bottom of the selected list
        int last = list.size();
        while (last > 0 && !(list.get(last - 1) instanceof ResourcePackListEntryFound)) last--;
        int y = Math.max(slot.top, Math.min(slot.bottom, mouseY));
        int index = Math.floorDiv(y - rowTop(slot, 0) + slot.slotHeight / 2, slot.slotHeight);
        return new Target(slot, list, Math.max(0, Math.min(last, index)));
    }

    private static void drop(int mouseX, int mouseY) {
        Target target = target(mouseX, mouseY);
        List<Object> from = listOf(held);
        if (target == null || from == null) return;
        int index = target.index;
        int at = from.indexOf(held);
        if (target.list == from && at < index) index--;
        if (target.list == from && at == index) return;
        from.remove(at);
        target.list.add(Math.min(index, target.list.size()), held);
    }

    /** Scrolls the list when the dragged pack is near its top or bottom, faster nearer the edge. */
    private static void scroll(GuiSlot slot, int mouseY, float frameMs) {
        int depth = 0;
        if (mouseY < slot.top + SCROLL_EDGE) depth = -Math.min(SCROLL_EDGE, slot.top + SCROLL_EDGE - mouseY);
        else if (mouseY > slot.bottom - SCROLL_EDGE) depth = Math.min(SCROLL_EDGE, mouseY - slot.bottom + SCROLL_EDGE);
        if (depth == 0) return;
        PackListSlot access = (PackListSlot) slot;
        // the list keeps its scroll in bounds itself when it is drawn
        access.gtnhtweaks$setScrolled(access.gtnhtweaks$getScrolled() + depth * frameMs * 0.02F);
    }

    // ---- geometry of the rows, as GuiSlot draws them

    /** The lists of the resource packs are as wide as the rows: these start at their left. */
    private static int rowLeft(GuiSlot slot) {
        return slot.left + 2;
    }

    private static int rowTop(GuiSlot slot, int index) {
        PackListSlot access = (PackListSlot) slot;
        return slot.top + 4
            - (int) access.gtnhtweaks$getScrolled()
            + index * slot.slotHeight
            + access.gtnhtweaks$getHeaderPadding();
    }

    private static int rowAt(GuiSlot slot, List<Object> list, int mouseX, int mouseY) {
        if (mouseX < rowLeft(slot) || mouseX >= slot.right - 6 || mouseY < slot.top || mouseY > slot.bottom) return -1;
        int offset = mouseY - rowTop(slot, 0);
        if (offset < 0) return -1;
        int index = offset / slot.slotHeight;
        return index < list.size() ? index : -1;
    }

    /** Fills a band across a list, cut to the part of the list that is shown. */
    private static void fillInList(GuiSlot slot, int top, int bottom, int color) {
        top = Math.max(top, slot.top);
        bottom = Math.min(bottom, slot.bottom);
        if (bottom <= top) return;
        Gui.drawRect(rowLeft(slot) - 2, top, slot.right - 6, bottom, color);
    }
}
