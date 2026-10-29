package gtnhtweaks.modules.minecraft.client;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import gtnhtweaks.Tags;

/**
 * Resource packs to get: the ones the GTNH wiki lists, from their GitHub or GitLab releases, and the ones for
 * Minecraft 1.7.10 on Modrinth. Their files are found, told apart from the ones already in the resource pack folder
 * and downloaded into it. Everything here runs on worker threads; the screen takes the results on its tick.
 */
public final class PackCatalog {

    public static final String GAME_VERSION = "1.7.10";
    public static final int PAGE_SIZE = 20;

    private static final String MODRINTH = "https://api.modrinth.com/v2";
    private static final int TIMEOUT_MS = 15_000;
    private static final int ICON_SIZE = 64;
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(4, task -> {
        Thread thread = new Thread(task, "GTNHTweaks pack catalog");
        thread.setDaemon(true);
        return thread;
    });

    public enum Source {

        GTNH("GTNH"),
        MODRINTH("Modrinth");

        public final String label;

        Source(String label) {
            this.label = label;
        }
    }

    /** Where the files of a pack of the GTNH list come from. */
    enum Kind {
        /** The releases of a GitHub repository, or its default branch when it has none. */
        GITHUB,
        GITLAB,
        MODRINTH,
        /** Only a page to download it from by hand. */
        PAGE
    }

    /** A pack of a search or of the GTNH list. */
    public static final class Pack {

        public final Source source;
        public final String id, title, author, description, iconUrl, pageUrl;
        public final long downloads;
        /** For the GTNH list: its section of the wiki, the GTNH versions it is for, and a word on how to get it. */
        public final String category, versions, note;
        final Kind kind;
        /** The repository of the pack, or the project on Modrinth or GitLab, and its release tag if it is fixed. */
        final String owner, repo, tag;
        /** The tags of its releases, for a repository that keeps other packs too; null for all. */
        final Pattern tags;
        /** Seen in the resource pack folder, so its files are looked up as soon as the list shows it. */
        public volatile boolean likelyInstalled;

        Pack(Source source, String id, String title, String author, String description, long downloads, String iconUrl,
            String pageUrl) {
            this(
                source,
                id,
                title,
                author,
                description,
                downloads,
                iconUrl,
                pageUrl,
                "",
                "",
                "",
                Kind.MODRINTH,
                null,
                id,
                null,
                null);
        }

        Pack(Source source, String id, String title, String author, String description, long downloads, String iconUrl,
            String pageUrl, String category, String versions, String note, Kind kind, String owner, String repo,
            String tag, Pattern tags) {
            this.source = source;
            this.id = id;
            this.title = title;
            this.author = author;
            this.description = description;
            this.downloads = downloads;
            this.iconUrl = iconUrl;
            this.pageUrl = pageUrl;
            this.category = category;
            this.versions = versions;
            this.note = note;
            this.kind = kind;
            this.owner = owner;
            this.repo = repo;
            this.tag = tag;
            this.tags = tags;
        }

        /** The key the installed packs are kept by. */
        public String key() {
            return source.name()
                .toLowerCase(Locale.ROOT) + ":"
                + id;
        }

        /** True if the pack can only be downloaded by hand from its page. */
        public boolean pageOnly() {
            return kind == Kind.PAGE;
        }
    }

    /** A page of found packs, and how many there are in all. */
    public static final class Page {

        public final List<Pack> packs;
        public final int total;

        Page(List<Pack> packs, int total) {
            this.packs = packs;
            this.total = total;
        }
    }

    /**
     * A file of a pack: a pack with variants, as one for vanilla GTNH and one for Modernity, has one for each. Its url
     * is null when the author does not let it be downloaded elsewhere. What of it is in the resource pack folder is
     * found on the worker that looks it up, and changed by the screen when it is downloaded.
     */
    public static final class PackFile {

        public final String url, fileName, version, label;
        final String packKey, variantKey;
        /** The version is a commit, newer when only different. */
        final boolean exact;
        /** The file of the folder this one is installed as, or null. */
        public volatile String installedAs;
        /** True if the file in the folder is an older version of this one. */
        public volatile boolean outdated;

        PackFile(Pack pack, String url, String fileName, String version, String label, boolean exact) {
            this.url = url;
            this.fileName = fileName;
            this.version = version;
            this.label = label;
            this.packKey = pack.key();
            this.variantKey = InstalledPacks.normalize(fileName);
            this.exact = exact;
        }

        /** The size of the file in bytes, 0 when not known before it is downloaded. */
        public long size;

        PackFile sized(long size) {
            this.size = size;
            return this;
        }
    }

    /** How far a download is: the bytes done, and of how many, 0 when the size is not known. */
    public interface Progress {

        void update(long done, long total);
    }

    /** The files of a pack to download, and a word on them, as the release being for another GTNH version. */
    public static final class Release {

        public final List<PackFile> files;
        public final String note;

        Release(List<PackFile> files, String note) {
            this.files = files;
            this.note = note;
        }
    }

    private PackCatalog() {}

    public static <T> Future<T> run(Callable<T> task) {
        return WORKERS.submit(task);
    }

    /** The GTNH version line that is played, as {@code 2.8.X}, or null outside GTNH. */
    public static String playerLine() {
        return GtnhPacks.playerLine();
    }

    // ---- search

    /** The packs matching the query: of the GTNH list all at once, of Modrinth the most downloaded first. */
    public static Page search(Source source, String query, int offset, File folder) throws IOException {
        if (source == Source.GTNH) return GtnhPacks.search(query, folder);
        return searchModrinth(query, offset);
    }

    private static Page searchModrinth(String query, int offset) throws IOException {
        String facets = "[[\"project_type:resourcepack\"],[\"versions:" + GAME_VERSION + "\"]]";
        String url = MODRINTH + "/search?limit="
            + PAGE_SIZE
            + "&offset="
            + offset
            + "&index="
            + (query.isEmpty() ? "downloads" : "relevance")
            + "&facets="
            + encode(facets)
            + (query.isEmpty() ? "" : "&query=" + encode(query));
        JsonObject root = get(url).getAsJsonObject();
        List<Pack> packs = new ArrayList<>();
        for (JsonElement element : array(root, "hits")) {
            JsonObject hit = element.getAsJsonObject();
            String slug = string(hit, "slug");
            String id = string(hit, "project_id");
            packs.add(
                new Pack(
                    Source.MODRINTH,
                    id,
                    string(hit, "title"),
                    string(hit, "author"),
                    string(hit, "description"),
                    number(hit, "downloads"),
                    string(hit, "icon_url"),
                    "https://modrinth.com/resourcepack/" + (slug.isEmpty() ? id : slug)));
        }
        return new Page(packs, (int) number(root, "total_hits"));
    }

    // ---- the files

    /** The newest files of the pack, with what of them is in the folder already. */
    public static Release files(Pack pack, File folder) throws IOException {
        Release release;
        switch (pack.kind) {
            case GITHUB:
                release = GtnhPacks.github(pack);
                break;
            case GITLAB:
                release = GtnhPacks.gitlab(pack);
                break;
            case MODRINTH:
                PackFile file = modrinthFile(pack);
                release = new Release(
                    file == null ? Collections.<PackFile>emptyList() : Collections.singletonList(file),
                    file == null ? "No file for " + GAME_VERSION : "");
                break;
            default:
                release = new Release(Collections.<PackFile>emptyList(), "");
        }
        InstalledPacks.detect(pack, release.files, folder);
        return release;
    }

    private static PackFile modrinthFile(Pack pack) throws IOException {
        String url = MODRINTH + "/project/"
            + encode(pack.repo)
            + "/version?game_versions="
            + encode("[\"" + GAME_VERSION + "\"]");
        // newest first
        for (JsonElement element : get(url).getAsJsonArray()) {
            JsonObject version = element.getAsJsonObject();
            JsonArray files = array(version, "files");
            JsonObject chosen = null;
            for (JsonElement file : files) {
                JsonObject candidate = file.getAsJsonObject();
                if (chosen == null || candidate.has("primary") && candidate.get("primary")
                    .getAsBoolean()) chosen = candidate;
            }
            if (chosen != null) return new PackFile(
                pack,
                string(chosen, "url"),
                string(chosen, "filename"),
                string(version, "version_number"),
                pack.title,
                false).sized(number(chosen, "size"));
        }
        return null;
    }

    // ---- versions

    /** Compares versions by their numbers, 1.10 after 1.9; versions without numbers are only equal or not. */
    public static int compareVersions(String a, String b) {
        List<Long> x = numbers(a), y = numbers(b);
        if (x.isEmpty() || y.isEmpty()) return a.equalsIgnoreCase(b) ? 0 : 1;
        for (int i = 0; i < Math.max(x.size(), y.size()); i++) {
            long p = i < x.size() ? x.get(i) : 0, q = i < y.size() ? y.get(i) : 0;
            if (p != q) return Long.compare(p, q);
        }
        return 0;
    }

    private static List<Long> numbers(String version) {
        List<Long> numbers = new ArrayList<>();
        Matcher matcher = DIGITS.matcher(version);
        while (matcher.find()) {
            String digits = matcher.group();
            numbers.add(Long.parseLong(digits.length() > 18 ? digits.substring(0, 18) : digits));
        }
        return numbers;
    }

    // ---- download

    /** The file a pack is saved to in the folder: its own name, without anything that could lead out of it. */
    public static File target(File folder, PackFile file) {
        String name = new File(file.fileName).getName()
            .replaceAll("[\\\\/:*?\"<>|]", "_");
        if (name.isEmpty()) name = "pack.zip";
        if (!name.toLowerCase(Locale.ROOT)
            .endsWith(".zip")) name += ".zip";
        return new File(folder, name);
    }

    /** A download stopped by its Cancel button. */
    public static final class CancelledException extends IOException {

        CancelledException() {
            super("Cancelled");
        }
    }

    /**
     * Downloads the file into the folder, reporting its progress, until it is done or {@code cancelled} says so. A pack
     * inside a folder of the zip, as in the archives of GitHub, is moved to its root. Gives the saved file.
     */
    public static File download(PackFile file, File folder, Progress progress, BooleanSupplier cancelled)
        throws IOException {
        File part = new File(folder, target(folder, file).getName() + ".part");
        fetch(file, part, progress, cancelled);
        return place(part, folder, file);
    }

    /**
     * The folder the packs downloaded only to be looked at are kept in until they are installed. It is emptied when
     * the browser closes and at the start of the game, so a pack looked at and not installed takes no room.
     */
    public static File previewFolder() {
        return new File(new File(Minecraft.getMinecraft().mcDataDir, "gtnhtweaks"), "pack-preview");
    }

    /** Downloads the file into the preview folder, to look at it before it is installed. */
    public static File downloadPreview(PackFile file, Progress progress, BooleanSupplier cancelled) throws IOException {
        File folder = previewFolder();
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Could not make " + folder);
        String name = Integer.toHexString(file.url.hashCode()) + "-" + target(folder, file).getName();
        File part = new File(folder, name + ".part");
        fetch(file, part, progress, cancelled);
        File zip = new File(folder, name);
        if (zip.exists() && !zip.delete() || !part.renameTo(zip)) {
            part.delete();
            throw new IOException("Could not save " + zip);
        }
        return zip;
    }

    /** Installs a pack downloaded to be looked at: it is moved into the folder, under the name of its file. */
    public static File installPreview(File preview, File folder, PackFile file) throws IOException {
        return place(preview, folder, file);
    }

    /** Deletes the packs downloaded to be looked at; the ones still read are left for the next time. */
    public static void clearPreviews() {
        File[] files = previewFolder().listFiles();
        if (files != null) for (File file : files) file.delete();
    }

    private static void fetch(PackFile file, File part, Progress progress, BooleanSupplier cancelled)
        throws IOException {
        HttpURLConnection connection = open(file.url, false);
        try {
            long length = connection.getContentLengthLong();
            long total = length > 0 ? length : file.size;
            long done = 0;
            try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(part)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    if (cancelled.getAsBoolean()) throw new CancelledException();
                    out.write(buffer, 0, read);
                    done += read;
                    progress.update(done, total);
                }
            }
            if (cancelled.getAsBoolean()) throw new CancelledException();
            moveToRoot(part);
        } catch (IOException e) {
            part.delete();
            throw e;
        } finally {
            connection.disconnect();
        }
    }

    /** Moves the downloaded file into the folder under the name of the pack file. */
    private static File place(File downloaded, File folder, PackFile file) throws IOException {
        File target = target(folder, file);
        // a pack in use is locked on Windows: the new version then goes beside it
        for (int n = 2; target.exists() && !target.delete(); n++) {
            String name = target(folder, file).getName();
            target = new File(folder, name.substring(0, name.length() - 4) + " (" + n + ").zip");
        }
        try {
            Files.move(downloaded.toPath(), target.toPath());
        } catch (IOException e) {
            downloaded.delete();
            throw new IOException("Could not save " + target, e);
        }
        return target;
    }

    private static void moveToRoot(File zip) throws IOException {
        String mcmeta = "pack.mcmeta";
        File moved = new File(zip.getPath() + ".tmp");
        try (ZipFile in = new ZipFile(zip)) {
            if (in.getEntry(mcmeta) != null) return;
            String prefix = null;
            Enumeration<? extends ZipEntry> entries = in.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement()
                    .getName();
                if (name.endsWith("/" + mcmeta) && (prefix == null || name.length() < prefix.length() + 11))
                    prefix = name.substring(0, name.length() - mcmeta.length());
            }
            if (prefix == null) throw new IOException("The downloaded file has no pack.mcmeta: not a resource pack");
            try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(moved))) {
                byte[] buffer = new byte[64 * 1024];
                entries = in.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.startsWith(prefix) || name.length() == prefix.length()) continue;
                    out.putNextEntry(new ZipEntry(name.substring(prefix.length())));
                    if (!entry.isDirectory()) {
                        try (InputStream data = in.getInputStream(entry)) {
                            int read;
                            while ((read = data.read(buffer)) > 0) out.write(buffer, 0, read);
                        }
                    }
                    out.closeEntry();
                }
            }
        } catch (IOException e) {
            moved.delete();
            throw e;
        }
        if (!zip.delete() || !moved.renameTo(zip)) throw new IOException("Could not save " + zip);
    }

    // ---- icons

    /** The icon of a pack, made small; null if it has none or it is in a format Java cannot read, as WebP. */
    public static BufferedImage icon(Pack pack) {
        if (pack.iconUrl == null || pack.iconUrl.isEmpty()) return null;
        try {
            HttpURLConnection connection = open(pack.iconUrl, false);
            try (InputStream in = connection.getInputStream()) {
                BufferedImage image = ImageIO.read(in);
                return image == null ? null : shrink(image);
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static BufferedImage shrink(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        float scale = Math.min(1, ICON_SIZE / (float) Math.max(w, h));
        int sw = Math.max(1, Math.round(w * scale)), sh = Math.max(1, Math.round(h * scale));
        BufferedImage small = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, sw, sh, null);
        g.dispose();
        return small;
    }

    // ---- http

    private static HttpURLConnection open(String url, boolean json) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        // Modrinth and GitHub ask for a user agent naming the program
        connection.setRequestProperty(
            "User-Agent",
            "evgengoldwar/GTNHTweaks/" + Tags.VERSION + " (github.com/evgengoldwar/GTNHTweaks)");
        if (json) connection.setRequestProperty("Accept", "application/json");
        int code = connection.getResponseCode();
        if (code / 100 != 2) {
            String host = new URL(url).getHost();
            boolean limited = (code == 403 || code == 429)
                && "0".equals(connection.getHeaderField("X-RateLimit-Remaining"));
            connection.disconnect();
            if (limited) throw new IOException(
                "GitHub answers only 60 requests an hour without a login, and they are used up: try again later");
            throw new IOException(code == 404 ? "Not found on " + host : "HTTP " + code + " from " + host);
        }
        return connection;
    }

    static JsonElement get(String url) throws IOException {
        HttpURLConnection connection = open(url, true);
        try (Reader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader);
        } finally {
            connection.disconnect();
        }
    }

    static String encode(String text) {
        try {
            return URLEncoder.encode(text, "UTF-8")
                .replace("+", "%20");
        } catch (IOException e) {
            return text;
        }
    }

    static String string(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element == null || element.isJsonNull() || !element.isJsonPrimitive() ? "" : element.getAsString();
    }

    static boolean bool(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }

    static long number(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? 0 : (long) element.getAsDouble();
    }

    static JsonObject object(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    static JsonArray array(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }
}
