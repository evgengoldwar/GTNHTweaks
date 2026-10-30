package gtnhtweaks.modules.buttons.mods;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.Tags;
import gtnhtweaks.core.ModuleManager;

/**
 * Installs, updates and removes the {@link OwnMod}s in the mods folder, from their newest release on GitHub. One
 * change at a time runs on a thread of its own; the window reads {@link #states} and {@link #message}.
 * <p>
 * An update turns the old jar off by renaming it, a.jar to a.jar1 (a.jar2 if a.jar1 is taken...), and puts the new
 * one beside it. A loaded jar that cannot be renamed or deleted now (Windows) is changed by {@link PendingFiles} when
 * the game closes; the new jar waits as a .pending file till then, so the two are never enabled together.
 */
public final class OwnMods {

    private static final String API = "https://api.github.com/repos/";
    private static final int TIMEOUT_MS = 15_000;
    /** A check sooner than this after the last one keeps its results: GitHub answers 60 requests an hour. */
    private static final long CHECK_REUSE_MS = 10 * 60_000;
    private static final Pattern DISABLED = Pattern.compile("(?i).*\\.jar\\d+$");

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "GTNHTweaks mods");
        thread.setDaemon(true);
        return thread;
    });

    /** A line about the last change of a mod, red when it failed. */
    public static final class Message {

        public final String text;
        public final boolean error;

        Message(String text, boolean error) {
            this.text = text;
            this.error = error;
        }
    }

    private static final Map<OwnMod, Release> RELEASES = Collections.synchronizedMap(new EnumMap<>(OwnMod.class));
    private static final Map<OwnMod, String> ERRORS = Collections.synchronizedMap(new EnumMap<>(OwnMod.class));
    private static final Map<OwnMod, Message> MESSAGES = Collections.synchronizedMap(new EnumMap<>(OwnMod.class));
    /** Versions of the jars downloaded while the game runs, whose names may not tell it. */
    private static final Map<File, ModVersion> DOWNLOADED = Collections.synchronizedMap(new HashMap<>());

    private static volatile List<ModState> states = Collections.emptyList();
    private static volatile boolean checking;
    private static volatile long checkedAt;
    /** The mod being changed, or null. */
    private static volatile OwnMod busy;
    private static volatile String busyText = "";
    /** Download progress from 0 to 1, or -1 when the size is not known. */
    private static volatile float progress = -1;
    private static boolean helperRegistered;

    private OwnMods() {}

    // ---- state

    public static List<ModState> states() {
        return states;
    }

    public static boolean isChecking() {
        return checking;
    }

    public static long checkedAt() {
        return checkedAt;
    }

    public static OwnMod busy() {
        return busy;
    }

    public static String busyText() {
        return busyText;
    }

    public static float progress() {
        return progress;
    }

    public static Message message(OwnMod mod) {
        return MESSAGES.get(mod);
    }

    public static File modsFolder() {
        return new File(Minecraft.getMinecraft().mcDataDir, "mods");
    }

    static File queue() {
        return PendingFiles.queue(ModuleManager.getConfigDir());
    }

    /** Looks at the mods folder again and builds the states from it and from the last check of GitHub. */
    public static synchronized void rescan() {
        File folder = modsFolder();
        File[] listed = folder.listFiles();
        List<File> files = listed == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(listed));
        files.sort((a, b) -> b.getName()
            .compareToIgnoreCase(a.getName()));
        List<PendingFiles.Change> pending = PendingFiles.read(queue());
        Map<String, ModContainer> loaded = Loader.instance()
            .getIndexedModList();

        List<ModState> list = new ArrayList<>();
        for (OwnMod mod : OwnMod.values()) {
            ModContainer container = loaded.get(mod.modId);
            File source = container == null ? null : container.getSource();
            List<File> jars = new ArrayList<>();
            List<File> disabled = new ArrayList<>();
            List<String> waiting = new ArrayList<>();
            boolean pendingForMod = false;
            for (PendingFiles.Change change : pending) {
                if (!mod.ownsFile(change.file.getName()) && !change.file.equals(source)) continue;
                pendingForMod = true;
                waiting.add(describe(change));
            }
            for (File file : files) {
                if (!file.isFile() || !mod.ownsFile(file.getName()) && !file.equals(source)) continue;
                if (isPending(pending, file)) continue;
                if (file.getName()
                    .toLowerCase(Locale.ROOT)
                    .endsWith(".jar")) jars.add(file);
                else if (DISABLED.matcher(file.getName())
                    .matches()) disabled.add(file);
            }

            ModVersion installed = null;
            for (File jar : jars) {
                ModVersion version = versionOf(jar, container, source);
                if (version != null && (installed == null || version.compareTo(installed) > 0)) installed = version;
            }
            boolean sourceInMods = source != null && folder.equals(source.getParentFile());
            // a mod loaded from elsewhere, as in a development setup, is installed too
            if (jars.isEmpty() && container != null && !sourceInMods) installed = ModVersion.parse(container.getVersion());
            boolean restart = pendingForMod || sourceInMods && !jars.contains(source)
                || jars.stream()
                    .anyMatch(jar -> !jar.equals(source));
            list.add(
                new ModState(
                    mod,
                    container == null ? null : container.getVersion(),
                    installed,
                    Collections.unmodifiableList(jars),
                    Collections.unmodifiableList(disabled),
                    Collections.unmodifiableList(waiting),
                    restart,
                    RELEASES.get(mod),
                    ERRORS.get(mod)));
        }
        states = Collections.unmodifiableList(list);
    }

    private static ModVersion versionOf(File jar, ModContainer container, File source) {
        if (jar.equals(source)) {
            ModVersion version = ModVersion.parse(container.getVersion());
            if (version.isParsed()) return version;
        }
        ModVersion downloaded = DOWNLOADED.get(jar);
        if (downloaded != null) return downloaded;
        ModVersion inName = ModVersion.inFileName(jar.getName());
        return inName != null ? inName : ModVersion.parse("?");
    }

    private static boolean isPending(List<PendingFiles.Change> pending, File file) {
        for (PendingFiles.Change change : pending) {
            if (change.file.getAbsoluteFile()
                .equals(file.getAbsoluteFile())) return true;
        }
        return false;
    }

    private static String describe(PendingFiles.Change change) {
        String name = change.file.getName();
        if (PendingFiles.DELETE.equals(change.action)) return "delete " + name;
        if (PendingFiles.DISABLE.equals(change.action)) return "turn off " + name;
        if (PendingFiles.MOVE.equals(change.action) && change.target != null) {
            return "enable " + change.target.getName();
        }
        return change.action.toLowerCase(Locale.ROOT) + " " + name;
    }

    // ---- GitHub

    /** Asks GitHub for the newest releases, unless it was asked lately and {@code force} is false. */
    public static void check(boolean force) {
        if (checking || !force && checkedAt != 0 && System.currentTimeMillis() - checkedAt < CHECK_REUSE_MS) return;
        checking = true;
        WORKER.execute(() -> {
            try {
                String offline = null;
                for (OwnMod mod : OwnMod.values()) {
                    if (offline != null) {
                        ERRORS.put(mod, offline);
                        continue;
                    }
                    try {
                        Release release = fetchLatest(mod);
                        if (release != null) RELEASES.put(mod, release);
                        else RELEASES.remove(mod);
                        ERRORS.put(mod, release == null ? "No release on GitHub yet" : null);
                    } catch (IOException e) {
                        GTNHTweaks.LOG.warn("Could not check " + mod.displayName + " on GitHub: " + e);
                        ERRORS.put(mod, e.getMessage());
                        // no connection or no requests left: the next mods would fail the same way
                        if (e instanceof Offline) offline = e.getMessage();
                    }
                }
                checkedAt = System.currentTimeMillis();
            } finally {
                checking = false;
                rescan();
            }
        });
    }

    /** The highest version with a jar among the releases, prereleases only when there is nothing else. */
    private static Release fetchLatest(OwnMod mod) throws IOException {
        Release best = null, bestPre = null;
        for (JsonElement element : get(API + OwnMod.OWNER + "/" + mod.repo + "/releases?per_page=20")
            .getAsJsonArray()) {
            JsonObject release = element.getAsJsonObject();
            if (bool(release, "draft")) continue;
            ModVersion version = ModVersion.parse(string(release, "tag_name"));
            if (!version.isParsed()) continue;
            JsonObject asset = jarAsset(release);
            if (asset == null) continue;
            String date = string(release, "published_at");
            Release found = new Release(
                version,
                date.length() >= 10 ? date.substring(0, 10) : "",
                string(asset, "name"),
                string(asset, "browser_download_url"),
                asset.has("size") ? asset.get("size")
                    .getAsLong() : 0,
                string(release, "html_url"));
            if (bool(release, "prerelease")) {
                if (bestPre == null || version.compareTo(bestPre.version) > 0) bestPre = found;
            } else if (best == null || version.compareTo(best.version) > 0) best = found;
        }
        return best != null ? best : bestPre;
    }

    /** The jar to play with: not the -dev, -sources, -api or -javadoc ones. */
    private static JsonObject jarAsset(JsonObject release) {
        JsonElement assets = release.get("assets");
        if (assets == null || !assets.isJsonArray()) return null;
        for (JsonElement element : assets.getAsJsonArray()) {
            JsonObject asset = element.getAsJsonObject();
            String name = string(asset, "name").toLowerCase(Locale.ROOT);
            if (!name.endsWith(".jar")) continue;
            if (name.endsWith("-dev.jar") || name.endsWith("-sources.jar")
                || name.endsWith("-api.jar")
                || name.endsWith("-javadoc.jar")
                || name.endsWith("-deobf.jar")) continue;
            return asset;
        }
        return null;
    }

    /** No connection, or no requests left. */
    private static final class Offline extends IOException {

        Offline(String message) {
            super(message);
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection;
        int code;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty(
                "User-Agent",
                "evgengoldwar/GTNHTweaks/" + Tags.VERSION + " (github.com/evgengoldwar/GTNHTweaks)");
            code = connection.getResponseCode();
        } catch (IOException e) {
            throw new Offline("No connection to GitHub");
        }
        if (code / 100 != 2) {
            boolean limited = (code == 403 || code == 429)
                && "0".equals(connection.getHeaderField("X-RateLimit-Remaining"));
            connection.disconnect();
            if (limited) throw new Offline("GitHub answers 60 requests an hour, they are used up: try later");
            throw new IOException(code == 404 ? "Not found on GitHub" : "GitHub answered HTTP " + code);
        }
        return connection;
    }

    private static JsonElement get(String url) throws IOException {
        HttpURLConnection connection = open(url);
        try (Reader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader);
        } finally {
            connection.disconnect();
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() || !element.isJsonPrimitive() ? "" : element.getAsString();
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }

    // ---- changes

    /**
     * Downloads the newest release into the mods folder. The enabled jars of the mod, if any, are turned off first:
     * a.jar becomes a.jar1, or a.jar2 when a.jar1 is taken, and so on.
     */
    public static void install(OwnMod mod) {
        run(mod, "Downloading", () -> {
            Release release = RELEASES.get(mod);
            if (release == null) throw new IOException("The newest release is not known, check GitHub again");
            File folder = modsFolder();
            folder.mkdirs();
            File download = new File(folder, release.fileName + ".download");
            download(release, download);

            boolean waits = false;
            for (File jar : state(mod).jars) {
                File disabled = PendingFiles.disabledName(jar);
                if (!jar.renameTo(disabled)) {
                    later(new PendingFiles.Change(PendingFiles.DISABLE, jar, null));
                    waits = true;
                }
            }
            File target = new File(folder, release.fileName);
            DOWNLOADED.put(target, release.version);
            if (waits || target.exists()) {
                // the old jar is still loaded: the new one is enabled with it turned off, when the game closes
                File parked = new File(folder, release.fileName + ".pending");
                Files.move(download.toPath(), parked.toPath(), StandardCopyOption.REPLACE_EXISTING);
                later(new PendingFiles.Change(PendingFiles.MOVE, parked, target));
                return mod.displayName + " " + release.version + " is downloaded; it replaces the old jar when the game "
                    + "closes. Restart the game";
            }
            Files.move(download.toPath(), target.toPath());
            return mod.displayName + " " + release.version + " is installed. Restart the game to load it";
        });
    }

    /** Deletes the enabled jars of the mod; a loaded jar Windows keeps is deleted when the game closes. */
    public static void delete(OwnMod mod) {
        run(mod, "Deleting", () -> {
            boolean waits = false;
            for (File jar : state(mod).jars) {
                if (!jar.delete()) {
                    later(new PendingFiles.Change(PendingFiles.DELETE, jar, null));
                    waits = true;
                }
            }
            return waits ? mod.displayName + " is deleted when the game closes"
                : mod.displayName + " is deleted. Restart the game to unload it";
        });
    }

    /** Deletes the jars an update turned off. */
    public static void deleteDisabled(OwnMod mod) {
        run(mod, "Deleting", () -> {
            int deleted = 0;
            List<String> kept = new ArrayList<>();
            for (File file : state(mod).disabled) {
                if (file.delete()) deleted++;
                else kept.add(file.getName());
            }
            if (!kept.isEmpty()) throw new IOException("Could not delete " + String.join(", ", kept));
            return "Deleted " + deleted + (deleted == 1 ? " turned off jar" : " turned off jars");
        });
    }

    private interface Change {

        /** @return the message shown when it worked */
        String run() throws IOException;
    }

    private static void run(OwnMod mod, String text, Change change) {
        if (busy != null) return;
        busy = mod;
        busyText = text;
        progress = -1;
        MESSAGES.remove(mod);
        WORKER.execute(() -> {
            try {
                MESSAGES.put(mod, new Message(change.run(), false));
            } catch (IOException | RuntimeException e) {
                GTNHTweaks.LOG.warn("Could not change " + mod.displayName + " in the mods folder", e);
                String why = e.getMessage() == null ? e.toString() : e.getMessage();
                MESSAGES.put(mod, new Message(why, true));
            } finally {
                busy = null;
                rescan();
            }
        });
    }

    private static ModState state(OwnMod mod) {
        rescan();
        for (ModState state : states) if (state.mod == mod) return state;
        throw new IllegalStateException("No state of " + mod);
    }

    private static void download(Release release, File file) throws IOException {
        HttpURLConnection connection = open(release.downloadUrl);
        try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(file)) {
            long size = connection.getContentLengthLong();
            if (size <= 0) size = release.size;
            byte[] buffer = new byte[16384];
            long done = 0;
            for (int read; (read = in.read(buffer)) >= 0;) {
                out.write(buffer, 0, read);
                done += read;
                if (size > 0) progress = Math.min(1, done / (float) size);
            }
        } catch (IOException e) {
            file.delete();
            throw e;
        } finally {
            connection.disconnect();
        }
    }

    // ---- after the game

    private static void later(PendingFiles.Change change) throws IOException {
        PendingFiles.add(queue(), change);
        registerHelper();
    }

    /** When the game closes, starts a process that makes the waiting changes once the jars are let go. */
    private static synchronized void registerHelper() {
        if (helperRegistered) return;
        helperRegistered = true;
        Runtime.getRuntime()
            .addShutdownHook(new Thread(OwnMods::startHelper, "GTNHTweaks pending mod files"));
    }

    private static void startHelper() {
        File queue = queue();
        if (PendingFiles.read(queue)
            .isEmpty()) return;
        try {
            ModContainer self = Loader.instance()
                .getIndexedModList()
                .get(GTNHTweaks.MODID);
            File source = self == null ? null : self.getSource();
            if (source == null) return;
            File classPath = source;
            if (source.isFile()) {
                // a copy, so the process does not hold the jar of GTNHTweaks, which may be renamed too
                classPath = new File(System.getProperty("java.io.tmpdir"), "gtnhtweaks-pending-mod-files.jar");
                try {
                    Files.copy(source.toPath(), classPath.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    // a process of the last game still holds the copy: it is the same code
                    if (!classPath.isFile()) throw e;
                }
            }
            new ProcessBuilder(
                javaExecutable(),
                "-cp",
                classPath.getAbsolutePath(),
                PendingFiles.class.getName(),
                queue.getAbsolutePath()).start();
        } catch (IOException | RuntimeException e) {
            GTNHTweaks.LOG.warn("Could not start the process that changes the mods folder after the game", e);
        }
    }

    private static String javaExecutable() {
        File bin = new File(System.getProperty("java.home"), "bin");
        for (String name : new String[] { "javaw.exe", "java.exe", "java" }) {
            File java = new File(bin, name);
            if (java.isFile()) return java.getAbsolutePath();
        }
        return "java";
    }
}
