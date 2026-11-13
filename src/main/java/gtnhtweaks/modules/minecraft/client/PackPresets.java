package gtnhtweaks.modules.minecraft.client;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.core.ModuleManager;

/**
 * The saved sets of selected resource packs, in {@code config/GTNHTweaks/resourcepack_presets.json}. A preset keeps
 * the file names of its packs, the top one of the selected list first.
 */
public final class PackPresets {

    public static final int COUNT = 10;

    public static final class Preset {

        public String name;
        public final List<String> packs = new ArrayList<>();

        public Preset(String name, List<String> packs) {
            this.name = name;
            this.packs.addAll(packs);
        }
    }

    private static Preset[] presets;

    private PackPresets() {}

    /** The preset in the slot, 0 to 9, or null if the slot is empty. */
    public static Preset get(int slot) {
        load();
        return presets[slot];
    }

    /** Puts a preset in the slot, or empties it with null, and saves the file. */
    public static void set(int slot, Preset preset) {
        load();
        presets[slot] = preset;
        save();
    }

    /** A pack replaced by its new version is replaced in the presets too. */
    public static void rename(String from, String to) {
        load();
        boolean changed = false;
        for (Preset preset : presets) {
            if (preset == null) continue;
            int index = preset.packs.indexOf(from);
            if (index < 0) continue;
            if (preset.packs.contains(to)) preset.packs.remove(index);
            else preset.packs.set(index, to);
            changed = true;
        }
        if (changed) save();
    }

    private static File file() {
        return new File(ModuleManager.getConfigDir(), "resourcepack_presets.json");
    }

    private static void load() {
        if (presets != null) return;
        presets = new Preset[COUNT];
        File file = file();
        if (!file.isFile()) return;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonArray slots = new JsonParser().parse(reader)
                .getAsJsonObject()
                .getAsJsonArray("presets");
            for (int i = 0; i < COUNT && slots != null && i < slots.size(); i++) {
                JsonElement element = slots.get(i);
                if (element == null || !element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                List<String> packs = new ArrayList<>();
                JsonArray names = object.getAsJsonArray("packs");
                if (names != null) {
                    for (JsonElement name : names) packs.add(name.getAsString());
                }
                String name = object.has("name") ? object.get("name")
                    .getAsString() : "Preset " + (i + 1);
                presets[i] = new Preset(name, packs);
            }
        } catch (Exception e) {
            GTNHTweaks.LOG.warn("Could not read the resource pack presets of " + file, e);
        }
    }

    private static void save() {
        JsonArray slots = new JsonArray();
        for (Preset preset : presets) {
            if (preset == null) {
                slots.add(JsonNull.INSTANCE);
                continue;
            }
            JsonObject object = new JsonObject();
            object.addProperty("name", preset.name);
            JsonArray packs = new JsonArray();
            for (String pack : preset.packs) packs.add(new JsonPrimitive(pack));
            object.add("packs", packs);
            slots.add(object);
        }
        JsonObject root = new JsonObject();
        root.add("presets", slots);
        File file = file();
        file.getParentFile()
            .mkdirs();
        try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting()
                .create()
                .toJson(root, writer);
        } catch (Exception e) {
            GTNHTweaks.LOG.warn("Could not save the resource pack presets to " + file, e);
        }
    }
}
