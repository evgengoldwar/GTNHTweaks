package gtnhtweaks.modules.minecraft.client;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.core.ModuleManager;
import gtnhtweaks.modules.minecraft.client.PackCatalog.Kind;
import gtnhtweaks.modules.minecraft.client.PackCatalog.Pack;
import gtnhtweaks.modules.minecraft.client.PackCatalog.PackFile;

/**
 * Which packs of the resource pack folder are the files of the browser, and of what version. The ones it installed
 * are kept in {@code config/GTNHTweaks/resourcepacks.json}; the others are told by their name, or by the update block
 * of GTNHLib in their pack.mcmeta. An updated pack replaces the old file, in the selected packs and the presets too.
 */
public final class InstalledPacks {

    /** Version numbers, a GTNH version as 2.9.X among them, left out of the names compared. */
    private static final Pattern VERSION_TOKEN = Pattern.compile("(?i)v?\\.?\\d+(?:\\.(?:\\d+|x))*");
    private static final Pattern LINE_TOKEN = Pattern.compile("(?i)\\d+\\.\\d+\\.x");
    private static final Pattern DOTTED = Pattern.compile("\\d+(?:\\.\\d+)+");

    private static final class Record {

        String pack, variant, version, file;
    }

    /** The update block of a pack.mcmeta: the repository of the pack and its version. */
    private static final class Updater {

        long stamp;
        String owner = "", repo = "", version = "";
    }

    private static List<Record> records;
    /** Old versions that could not be deleted while the game used them, deleted on the next start. */
    private static List<String> obsolete;
    private static final Map<String, Updater> UPDATERS = new HashMap<>();

    private InstalledPacks() {}

    /** The name of a pack file without its version and anything but letters, to know the same pack in another one. */
    static String normalize(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".zip")) name = name.substring(0, name.length() - 4);
        return VERSION_TOKEN.matcher(name)
            .replaceAll("")
            .replaceAll("[^a-z]+", "");
    }

    // ---- telling installed packs

    static synchronized void detect(Pack pack, List<PackFile> files, File folder) {
        load();
        List<String> zips = zips(folder);
        for (PackFile file : files) {
            String found = null, version = null;
            Record record = record(file, files.size() == 1);
            String target = PackCatalog.target(folder, file)
                .getName();
            if (record != null) {
                found = record.file;
                version = record.version;
            } else if (zips.contains(target)) {
                found = target;
                version = file.version;
            }
            if (found == null && pack.kind == Kind.GITHUB) {
                for (String zip : zips) {
                    Updater updater = updater(folder, zip);
                    if (updater == null || !updater.owner.equalsIgnoreCase(pack.owner)
                        || !updater.repo.equalsIgnoreCase(pack.repo)) continue;
                    if (files.size() > 1 && !normalize(zip).equals(file.variantKey)) continue;
                    found = zip;
                    version = updater.version;
                    break;
                }
            }
            if (found == null && !file.exact && !file.variantKey.isEmpty()) {
                for (String zip : zips) {
                    if (!normalize(zip).equals(file.variantKey)) continue;
                    found = zip;
                    version = versionInName(zip);
                    break;
                }
            }
            file.installedAs = found;
            file.outdated = found != null && version != null
                && !version.isEmpty()
                && (file.exact ? !file.version.equalsIgnoreCase(version)
                    : PackCatalog.compareVersions(file.version, version) > 0);
        }
    }

    /** The record of the file whose installed file is still there; for a pack of one file, any of the pack. */
    private static Record record(PackFile file, boolean single) {
        Record any = null;
        int count = 0;
        for (Record record : records) {
            if (!record.pack.equals(file.packKey)) continue;
            if (record.variant.equals(file.variantKey)) return record;
            any = record;
            count++;
        }
        return single && count == 1 ? any : null;
    }

    private static String versionInName(String name) {
        String rest = LINE_TOKEN.matcher(name)
            .replaceAll("");
        Matcher matcher = DOTTED.matcher(rest);
        String version = null;
        while (matcher.find()) version = matcher.group();
        return version;
    }

    /** Marks the packs that seem to be in the folder: their files are then looked up at once. */
    static synchronized void markLikely(List<Pack> packs, File folder) {
        load();
        List<String> zips = zips(folder);
        List<String> names = new ArrayList<>();
        for (String zip : zips) names.add(normalize(zip));
        for (Pack pack : packs) {
            boolean likely = false;
            for (Record record : records) likely |= record.pack.equals(pack.key());
            if (!likely && pack.kind == Kind.GITHUB) {
                for (String zip : zips) {
                    Updater updater = updater(folder, zip);
                    likely |= updater != null && updater.owner.equalsIgnoreCase(pack.owner)
                        && updater.repo.equalsIgnoreCase(pack.repo);
                }
            }
            String title = normalize(pack.title), repo = pack.repo == null ? "" : normalize(pack.repo);
            for (String name : names) {
                likely |= title.length() >= 4 && name.startsWith(title) || repo.length() >= 4 && name.startsWith(repo);
            }
            pack.likelyInstalled = likely;
        }
    }

    /** The zip files of the folder; the records of the ones gone are dropped. */
    private static List<String> zips(File folder) {
        String[] names = folder.list();
        List<String> zips = new ArrayList<>();
        if (names == null) return zips;
        for (String name : names) {
            if (name.toLowerCase(Locale.ROOT)
                .endsWith(".zip") && new File(folder, name).isFile()) zips.add(name);
        }
        boolean changed = false;
        for (Iterator<Record> it = records.iterator(); it.hasNext();) {
            if (!zips.contains(it.next().file)) {
                it.remove();
                changed = true;
            }
        }
        if (changed) save();
        return zips;
    }

    private static Updater updater(File folder, String zip) {
        File file = new File(folder, zip);
        long stamp = file.lastModified() ^ file.length();
        Updater updater = UPDATERS.get(zip);
        if (updater != null && updater.stamp == stamp) return updater.repo.isEmpty() ? null : updater;
        updater = new Updater();
        updater.stamp = stamp;
        UPDATERS.put(zip, updater);
        try (ZipFile pack = new ZipFile(file)) {
            ZipEntry entry = pack.getEntry("pack.mcmeta");
            if (entry == null) return null;
            try (InputStream in = pack.getInputStream(entry);
                Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonElement root = new JsonParser().parse(reader);
                JsonObject block = root.isJsonObject()
                    ? PackCatalog.object(root.getAsJsonObject(), "gtnh_resource_pack_updater")
                    : null;
                if (block == null) return null;
                JsonObject source = PackCatalog.object(block, "source");
                updater.owner = PackCatalog.string(source, "owner");
                updater.repo = PackCatalog.string(source, "repo");
                updater.version = PackCatalog.string(block, "pack_version");
            }
        } catch (Exception e) {
            // not a pack, or a pack.mcmeta that cannot be read: told by its name only
            return null;
        }
        return updater.repo.isEmpty() ? null : updater;
    }

    // ---- installing

    /** Keeps the downloaded file as the installed one of its pack and variant. */
    public static synchronized void installed(PackFile file, String fileName) {
        load();
        for (Iterator<Record> it = records.iterator(); it.hasNext();) {
            Record record = it.next();
            if (record.pack.equals(file.packKey)
                && (record.variant.equals(file.variantKey) || record.file.equals(file.installedAs)
                    || record.file.equals(fileName)))
                it.remove();
        }
        Record record = new Record();
        record.pack = file.packKey;
        record.variant = file.variantKey;
        record.version = file.version;
        record.file = fileName;
        records.add(record);
        save();
    }

    /**
     * The new version of a pack takes the place of the old file: the old one is deleted, or on the next start when
     * the game holds it, and the new one is selected where the old one was, in the presets too.
     */
    public static void replace(File folder, String oldName, String newName) {
        File old = new File(folder, oldName);
        if (old.isFile() && !old.delete()) {
            synchronized (InstalledPacks.class) {
                load();
                if (!obsolete.contains(oldName)) obsolete.add(oldName);
                save();
            }
        }
        GameSettings settings = Minecraft.getMinecraft().gameSettings;
        @SuppressWarnings("unchecked")
        List<String> selected = settings.resourcePacks;
        int index = selected.indexOf(oldName);
        if (index >= 0) {
            if (selected.contains(newName)) selected.remove(index);
            else selected.set(index, newName);
            settings.saveOptions();
        }
        ResourcePackScreen.renamePending(oldName, newName);
        PackPresets.rename(oldName, newName);
    }

    /** At the start, before the packs are used: deletes the old versions that were in use when they were updated. */
    public static synchronized void deleteObsolete() {
        load();
        if (obsolete.isEmpty()) return;
        File folder = new File(Minecraft.getMinecraft().mcDataDir, "resourcepacks");
        for (Iterator<String> it = obsolete.iterator(); it.hasNext();) {
            File file = new File(folder, it.next());
            if (!file.exists() || file.delete()) it.remove();
        }
        save();
    }

    // ---- the file

    private static File file() {
        return new File(ModuleManager.getConfigDir(), "resourcepacks.json");
    }

    private static void load() {
        if (records != null) return;
        records = new ArrayList<>();
        obsolete = new ArrayList<>();
        File file = file();
        if (!file.isFile()) return;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(reader)
                .getAsJsonObject();
            for (JsonElement element : PackCatalog.array(root, "installed")) {
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                Record record = new Record();
                record.pack = PackCatalog.string(object, "pack");
                record.variant = PackCatalog.string(object, "variant");
                record.version = PackCatalog.string(object, "version");
                record.file = PackCatalog.string(object, "file");
                if (!record.pack.isEmpty() && !record.file.isEmpty()) records.add(record);
            }
            for (JsonElement element : PackCatalog.array(root, "obsolete")) obsolete.add(element.getAsString());
        } catch (Exception e) {
            GTNHTweaks.LOG.warn("Could not read the installed resource packs of " + file, e);
        }
    }

    private static void save() {
        JsonArray installed = new JsonArray();
        for (Record record : records) {
            JsonObject object = new JsonObject();
            object.addProperty("pack", record.pack);
            object.addProperty("variant", record.variant);
            object.addProperty("version", record.version);
            object.addProperty("file", record.file);
            installed.add(object);
        }
        JsonArray old = new JsonArray();
        for (String name : obsolete) old.add(new JsonPrimitive(name));
        JsonObject root = new JsonObject();
        root.add("installed", installed);
        root.add("obsolete", old);
        File file = file();
        file.getParentFile()
            .mkdirs();
        try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting()
                .create()
                .toJson(root, writer);
        } catch (Exception e) {
            GTNHTweaks.LOG.warn("Could not save the installed resource packs to " + file, e);
        }
    }
}
