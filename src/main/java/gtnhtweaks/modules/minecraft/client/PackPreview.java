package gtnhtweaks.modules.minecraft.client;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * What a downloaded resource pack changes: its textures, sorted by mod and by kind, each with the one the game shows
 * now to compare, and the count of its sounds, languages and other files. The zip stays open while the preview is
 * looked at; its images are read on worker threads.
 */
public final class PackPreview implements Closeable {

    /** Images bigger than this are made smaller: a GUI texture is shown whole, not at its size. */
    private static final int MAX_IMAGE = 256;

    public enum Category {

        BLOCKS("Blocks"),
        ITEMS("Items"),
        GUI("GUI"),
        ENTITIES("Mobs & models"),
        OTHER("Other");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    /** A texture of the pack. */
    public static final class Texture {

        public final String domain, path;
        public final Category category;
        final String entry;

        Texture(String domain, String path, Category category, String entry) {
            this.domain = domain;
            this.path = path;
            this.category = category;
            this.entry = entry;
        }

        /** The name of the texture without its folders and extension, as {@code machine_casing}. */
        public String name() {
            String name = path.substring(path.lastIndexOf('/') + 1);
            return name.endsWith(".png") ? name.substring(0, name.length() - 4) : name;
        }
    }

    /** An image of a texture: an animated one is a strip of square frames, shown one after another. */
    public static final class Image {

        public final BufferedImage image;
        public final int frames;
        /** Ticks a frame is shown. */
        public final int frameTime;

        Image(BufferedImage image, int frames, int frameTime) {
            this.image = image;
            this.frames = frames;
            this.frameTime = frameTime;
        }
    }

    public final File file;
    public final String description;
    public final List<Texture> textures;
    /** The mods with textures in the pack, the most changed first, and how many of each. */
    public final Map<String, Integer> domains;
    public final Map<Category, Integer> categories;
    public final int sounds, languages, others;
    private final ZipFile zip;

    private PackPreview(File file, ZipFile zip, String description, List<Texture> textures, int sounds, int languages,
        int others) {
        this.file = file;
        this.zip = zip;
        this.description = description;
        this.textures = Collections.unmodifiableList(textures);
        this.sounds = sounds;
        this.languages = languages;
        this.others = others;
        Map<String, Integer> domains = new HashMap<>();
        Map<Category, Integer> categories = new EnumMap<>(Category.class);
        for (Texture texture : textures) {
            domains.merge(texture.domain, 1, Integer::sum);
            categories.merge(texture.category, 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(domains.entrySet());
        sorted.sort(
            (a, b) -> a.getValue()
                .equals(b.getValue()) ? a.getKey()
                    .compareTo(b.getKey()) : b.getValue() - a.getValue());
        Map<String, Integer> ordered = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : sorted) ordered.put(entry.getKey(), entry.getValue());
        this.domains = Collections.unmodifiableMap(ordered);
        this.categories = Collections.unmodifiableMap(categories);
    }

    /** Reads what the pack holds. */
    public static PackPreview read(File file) throws IOException {
        ZipFile zip = new ZipFile(file);
        try {
            List<Texture> textures = new ArrayList<>();
            int sounds = 0, languages = 0, others = 0;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                String lower = name.toLowerCase(Locale.ROOT);
                if (!lower.startsWith("assets/")) continue;
                int slash = name.indexOf('/', 7);
                if (slash < 0) continue;
                String domain = name.substring(7, slash)
                    .toLowerCase(Locale.ROOT);
                String path = name.substring(slash + 1);
                String lowerPath = lower.substring(slash + 1);
                if (lowerPath.startsWith("textures/") && lowerPath.endsWith(".png")) {
                    textures.add(new Texture(domain, path, category(lowerPath), name));
                } else if (lowerPath.endsWith(".png.mcmeta")) {
                    // the animation of a texture, counted with it
                } else if (lowerPath.startsWith("sounds/") || lowerPath.endsWith(".ogg")) {
                    sounds++;
                } else if (lowerPath.startsWith("lang/")) {
                    languages++;
                } else {
                    others++;
                }
            }
            textures.sort(
                (a, b) -> a.domain.equals(b.domain) ? a.path.compareToIgnoreCase(b.path)
                    : a.domain.compareTo(b.domain));
            return new PackPreview(file, zip, description(zip), textures, sounds, languages, others);
        } catch (RuntimeException e) {
            zip.close();
            throw e;
        }
    }

    private static Category category(String path) {
        String rest = path.substring("textures/".length());
        if (rest.startsWith("blocks/")) return Category.BLOCKS;
        if (rest.startsWith("items/")) return Category.ITEMS;
        if (rest.startsWith("gui/") || rest.startsWith("font/")) return Category.GUI;
        if (rest.startsWith("entity/") || rest.startsWith("entities/")
            || rest.startsWith("models/")
            || rest.startsWith("armor/")
            || rest.startsWith("mobs/")) return Category.ENTITIES;
        return Category.OTHER;
    }

    private static String description(ZipFile zip) {
        ZipEntry entry = zip.getEntry("pack.mcmeta");
        if (entry == null) return "";
        try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
            JsonElement root = new JsonParser().parse(reader);
            JsonObject pack = root.isJsonObject() ? PackCatalog.object(root.getAsJsonObject(), "pack") : null;
            return PackCatalog.string(pack, "description");
        } catch (Exception e) {
            return "";
        }
    }

    // ---- images

    /** The texture as the pack has it. */
    public Image after(Texture texture) {
        try {
            ZipEntry entry = zip.getEntry(texture.entry);
            if (entry == null) return null;
            BufferedImage image;
            try (InputStream in = zip.getInputStream(entry)) {
                image = ImageIO.read(in);
            }
            ZipEntry meta = zip.getEntry(texture.entry + ".mcmeta");
            int frameTime = 1;
            if (meta != null) {
                try (InputStream in = zip.getInputStream(meta)) {
                    frameTime = frameTime(in);
                }
            }
            return image(image, frameTime);
        } catch (Exception e) {
            return null;
        }
    }

    /** The texture as the game shows it now, from its resources and the selected packs; null if it has none. */
    public static Image before(Texture texture) {
        ResourceLocation location = new ResourceLocation(texture.domain, texture.path);
        try {
            IResource resource = Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(location);
            BufferedImage image;
            try (InputStream in = resource.getInputStream()) {
                image = ImageIO.read(in);
            }
            int frameTime = 1;
            try {
                IResource meta = Minecraft.getMinecraft()
                    .getResourceManager()
                    .getResource(new ResourceLocation(texture.domain, texture.path + ".mcmeta"));
                try (InputStream in = meta.getInputStream()) {
                    frameTime = frameTime(in);
                }
            } catch (IOException ignored) {
                // not animated, or with the default speed
            }
            return image(image, frameTime);
        } catch (Exception e) {
            return null;
        }
    }

    private static int frameTime(InputStream in) {
        try {
            JsonElement root = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8));
            JsonObject animation = root.isJsonObject() ? PackCatalog.object(root.getAsJsonObject(), "animation")
                : null;
            long time = animation == null ? 1 : PackCatalog.number(animation, "frametime");
            return (int) Math.max(1, Math.min(200, time));
        } catch (Exception e) {
            return 1;
        }
    }

    private static Image image(BufferedImage image, int frameTime) {
        if (image == null) return null;
        int w = image.getWidth(), h = image.getHeight();
        // a strip of square frames is an animation
        int frames = h > w && h % w == 0 ? h / w : 1;
        if (frames > 1) {
            // only the frames are made smaller, kept square
            int size = Math.min(w, MAX_IMAGE / 2);
            // and a strip kept short enough for any graphics card
            int shown = Math.min(frames, Math.max(1, 4096 / size));
            if (size != w || shown != frames) {
                image = scale(image.getSubimage(0, 0, w, w * shown), size, size * shown);
                frames = shown;
            }
            return new Image(image, frames, frameTime);
        }
        float scale = Math.min(1, MAX_IMAGE / (float) Math.max(w, h));
        if (scale < 1) image = scale(image, Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)));
        return new Image(image, 1, frameTime);
    }

    private static BufferedImage scale(BufferedImage image, int w, int h) {
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(image, 0, 0, w, h, null);
        g.dispose();
        return scaled;
    }

    @Override
    public void close() {
        try {
            zip.close();
        } catch (IOException ignored) {
            // read only
        }
    }
}
