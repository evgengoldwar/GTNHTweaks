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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.DoubleConsumer;

import javax.imageio.ImageIO;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import gtnhtweaks.Tags;
import gtnhtweaks.modules.minecraft.MinecraftModule;

/**
 * Resource packs for Minecraft 1.7.10 on Modrinth and CurseForge: searched, their file for 1.7.10 found and downloaded
 * into the resource pack folder. Everything here runs on worker threads; the screen takes the results on its tick.
 * <p>
 * Modrinth is open. The CurseForge API wants a key: without one of the player in the config the open mirror
 * api.curse.tools is asked, which answers as the API does.
 */
public final class PackCatalog {

    public static final String GAME_VERSION = "1.7.10";
    public static final int PAGE_SIZE = 20;

    private static final String MODRINTH = "https://api.modrinth.com/v2";
    private static final String CURSEFORGE = "https://api.curseforge.com/v1";
    private static final String CURSEFORGE_MIRROR = "https://api.curse.tools/v1/cf";
    /** Minecraft on CurseForge, and its class of resource packs. */
    private static final int CF_GAME = 432, CF_RESOURCE_PACKS = 12;
    private static final int TIMEOUT_MS = 15_000;
    private static final int ICON_SIZE = 64;

    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(4, task -> {
        Thread thread = new Thread(task, "GTNHTweaks pack catalog");
        thread.setDaemon(true);
        return thread;
    });

    public enum Source {

        MODRINTH("Modrinth"),
        CURSEFORGE("CurseForge");

        public final String label;

        Source(String label) {
            this.label = label;
        }
    }

    /** A pack found by a search. */
    public static final class Pack {

        public final Source source;
        public final String id, title, author, description, iconUrl, pageUrl;
        public final long downloads;

        Pack(Source source, String id, String title, String author, String description, long downloads,
            String iconUrl, String pageUrl) {
            this.source = source;
            this.id = id;
            this.title = title;
            this.author = author;
            this.description = description;
            this.downloads = downloads;
            this.iconUrl = iconUrl;
            this.pageUrl = pageUrl;
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

    /** The file of a pack for 1.7.10; its url is null when the author does not let it be downloaded elsewhere. */
    public static final class PackFile {

        public final String url, fileName, version;

        PackFile(String url, String fileName, String version) {
            this.url = url;
            this.fileName = fileName;
            this.version = version;
        }
    }

    private PackCatalog() {}

    public static <T> Future<T> run(java.util.concurrent.Callable<T> task) {
        return WORKERS.submit(task);
    }

    // ---- search

    /** The packs for 1.7.10 matching the query, the most downloaded first without one. */
    public static Page search(Source source, String query, int offset) throws IOException {
        return source == Source.MODRINTH ? searchModrinth(query, offset) : searchCurseForge(query, offset);
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
        JsonObject root = get(url, false).getAsJsonObject();
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

    private static Page searchCurseForge(String query, int offset) throws IOException {
        String url = curseForgeBase() + "/mods/search?gameId="
            + CF_GAME
            + "&classId="
            + CF_RESOURCE_PACKS
            + "&gameVersion="
            + GAME_VERSION
            // by popularity, the most popular first
            + "&sortField=2&sortOrder=desc&pageSize="
            + PAGE_SIZE
            + "&index="
            + offset
            + (query.isEmpty() ? "" : "&searchFilter=" + encode(query));
        JsonObject root = get(url, true).getAsJsonObject();
        List<Pack> packs = new ArrayList<>();
        for (JsonElement element : array(root, "data")) {
            JsonObject mod = element.getAsJsonObject();
            JsonArray authors = array(mod, "authors");
            String author = authors.size() > 0 ? string(
                authors.get(0)
                    .getAsJsonObject(),
                "name") : "";
            JsonObject logo = object(mod, "logo");
            JsonObject links = object(mod, "links");
            packs.add(
                new Pack(
                    Source.CURSEFORGE,
                    string(mod, "id"),
                    string(mod, "name"),
                    author,
                    string(mod, "summary"),
                    number(mod, "downloadCount"),
                    logo == null ? "" : string(logo, "thumbnailUrl"),
                    links == null ? "" : string(links, "websiteUrl")));
        }
        JsonObject pagination = object(root, "pagination");
        int total = pagination == null ? packs.size() : (int) number(pagination, "totalCount");
        // CurseForge gives no more than the first 10000
        return new Page(packs, Math.min(total, 10_000));
    }

    // ---- the file for 1.7.10

    /** The newest file of the pack for 1.7.10, or null if it has none. */
    public static PackFile fileFor(Pack pack) throws IOException {
        return pack.source == Source.MODRINTH ? modrinthFile(pack) : curseForgeFile(pack);
    }

    private static PackFile modrinthFile(Pack pack) throws IOException {
        String url = MODRINTH + "/project/"
            + encode(pack.id)
            + "/version?game_versions="
            + encode("[\"" + GAME_VERSION + "\"]");
        // newest first
        for (JsonElement element : get(url, false).getAsJsonArray()) {
            JsonObject version = element.getAsJsonObject();
            JsonArray files = array(version, "files");
            JsonObject chosen = null;
            for (JsonElement file : files) {
                JsonObject candidate = file.getAsJsonObject();
                if (chosen == null || candidate.has("primary") && candidate.get("primary")
                    .getAsBoolean()) chosen = candidate;
            }
            if (chosen != null) return new PackFile(
                string(chosen, "url"),
                string(chosen, "filename"),
                string(version, "version_number"));
        }
        return null;
    }

    private static PackFile curseForgeFile(Pack pack) throws IOException {
        String url = curseForgeBase() + "/mods/" + encode(pack.id) + "/files?gameVersion=" + GAME_VERSION + "&pageSize=50";
        JsonObject newest = null;
        for (JsonElement element : array(get(url, true).getAsJsonObject(), "data")) {
            JsonObject file = element.getAsJsonObject();
            // the dates are ISO 8601, so they sort as text
            if (newest == null || string(file, "fileDate").compareTo(string(newest, "fileDate")) > 0) newest = file;
        }
        if (newest == null) return null;
        String download = string(newest, "downloadUrl");
        return new PackFile(
            download.isEmpty() ? null : download,
            string(newest, "fileName"),
            string(newest, "displayName"));
    }

    // ---- download

    /** The file a pack is saved to in the folder: its own name, without anything that could lead out of it. */
    public static File target(File folder, PackFile file) {
        String name = new File(file.fileName).getName()
            .replaceAll("[\\\\/:*?\"<>|]", "_");
        if (name.isEmpty()) name = "pack.zip";
        return new File(folder, name);
    }

    /** Downloads the file into the folder, reporting the share done from 0 to 1. Gives the saved file. */
    public static File download(PackFile file, File folder, DoubleConsumer progress) throws IOException {
        File target = target(folder, file);
        File part = new File(folder, target.getName() + ".part");
        HttpURLConnection connection = open(file.url, false);
        try {
            long length = connection.getContentLengthLong();
            long done = 0;
            try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(part)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    done += read;
                    if (length > 0) progress.accept(Math.min(1, done / (double) length));
                }
            }
        } catch (IOException e) {
            part.delete();
            throw e;
        } finally {
            connection.disconnect();
        }
        if (target.exists() && !target.delete()) throw new IOException("Could not replace " + target);
        if (!part.renameTo(target)) throw new IOException("Could not save " + target);
        return target;
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

    private static String curseForgeBase() {
        return MinecraftModule.curseForgeApiKey.isEmpty() ? CURSEFORGE_MIRROR : CURSEFORGE;
    }

    private static HttpURLConnection open(String url, boolean curseForge) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        // Modrinth asks for a user agent naming the program
        connection.setRequestProperty(
            "User-Agent",
            "evgengoldwar/GTNHTweaks/" + Tags.VERSION + " (github.com/evgengoldwar/GTNHTweaks)");
        connection.setRequestProperty("Accept", "application/json");
        if (curseForge && !MinecraftModule.curseForgeApiKey.isEmpty())
            connection.setRequestProperty("x-api-key", MinecraftModule.curseForgeApiKey);
        int code = connection.getResponseCode();
        if (code / 100 != 2) {
            connection.disconnect();
            throw new IOException("HTTP " + code + " from " + new URL(url).getHost());
        }
        return connection;
    }

    private static JsonElement get(String url, boolean curseForge) throws IOException {
        HttpURLConnection connection = open(url, curseForge);
        try (Reader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader);
        } finally {
            connection.disconnect();
        }
    }

    private static String encode(String text) {
        try {
            return URLEncoder.encode(text, "UTF-8");
        } catch (IOException e) {
            return text;
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private static long number(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? 0 : (long) element.getAsDouble();
    }

    private static JsonObject object(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    /** The packs already in the folder, by file name, to tell installed ones. */
    public static List<String> installed(File folder) {
        String[] names = folder.list();
        if (names == null) return Collections.emptyList();
        List<String> list = new ArrayList<>();
        Collections.addAll(list, names);
        return list;
    }
}
