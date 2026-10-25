package gtnhtweaks.modules.loadingscreen.render;

import static org.lwjgl.opengl.GL11.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import net.minecraft.launchwrapper.Launch;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL12;

import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.modules.loadingscreen.LoadingScreenModule;
import gtnhtweaks.modules.loadingscreen.LoadingTimer;

/**
 * Block textures of the loading screen wall. Resource packs and the block atlas do not exist yet while the game starts,
 * so the textures are read straight from the jars: a set of vanilla blocks from the Minecraft jar, and a few random
 * opaque block textures of every jar in the mods folder. Reading happens on a background thread; the textures are
 * uploaded to GL by whichever thread draws them.
 */
final class WallTextures {

    private static final String[] VANILLA = { "stone", "cobblestone", "cobblestone_mossy", "stonebrick",
        "stonebrick_mossy", "stonebrick_cracked", "stonebrick_carved", "brick", "planks_oak", "planks_spruce",
        "planks_birch", "planks_jungle", "planks_acacia", "planks_big_oak", "log_oak", "log_spruce", "log_birch",
        "log_jungle", "dirt", "gravel", "sand", "clay", "coal_ore", "iron_ore", "gold_ore", "diamond_ore",
        "redstone_ore", "lapis_ore", "emerald_ore", "quartz_ore", "iron_block", "gold_block", "diamond_block",
        "emerald_block", "lapis_block", "redstone_block", "coal_block", "obsidian", "bookshelf", "crafting_table_front",
        "furnace_front_off", "sandstone_normal", "sandstone_carved", "netherrack", "nether_brick", "end_stone",
        "quartz_block_chiseled", "glowstone", "pumpkin_face_off", "melon_side", "tnt_side", "noteblock",
        "hay_block_side", "hardened_clay", "hardened_clay_stained_orange", "hardened_clay_stained_cyan",
        "wool_colored_red", "wool_colored_blue", "wool_colored_lime" };

    /** Parts of texture paths that are almost never a full opaque block face. */
    private static final String[] SKIPPED = { "overlay", "ctm", "glass", "leaves", "leaf", "fluid", "_still", "_flow",
        "crop", "sapling", "flower", "torch", "rail", "door", "pane", "fence", "wire", "cable", "pipe", "frame",
        "destroy", "fire", "portal", "mask", "blank", "empty", "transparent", "particle" };

    private static final int PER_MOD = 3;
    private static final int MAX_MOD_TEXTURES = 240;

    private static final class Entry {

        final int[] pixels;
        final int size;
        int texture = -1;

        Entry(int[] pixels, int size) {
            this.pixels = pixels;
            this.size = size;
        }
    }

    private static final List<Entry> vanilla = new ArrayList<>();
    private static final List<Entry> mods = new ArrayList<>();
    private static Thread loader;

    private WallTextures() {}

    /** Starts reading the textures, once. */
    static synchronized void start() {
        if (loader != null) return;
        loader = new Thread(WallTextures::load, "GTNHTweaks loading screen blocks");
        loader.setDaemon(true);
        loader.setPriority(Thread.MIN_PRIORITY);
        loader.start();
    }

    /** A random texture id for a new block, a configured share of them from mods, or -1 if none is read yet. */
    static synchronized int pick(Random random) {
        int vanillaCount = vanilla.size(), modCount = mods.size();
        if (vanillaCount + modCount == 0) return -1;
        // Even ids are vanilla textures, odd ones mod textures
        if (modCount > 0 && (vanillaCount == 0 || random.nextFloat() < LoadingScreenModule.wallModBlockShare))
            return random.nextInt(modCount) * 2 + 1;
        return random.nextInt(vanillaCount) * 2;
    }

    /**
     * Binds the texture of a {@link #pick} id, uploading it first if needed.
     *
     * @return false if the id is unknown
     */
    static synchronized boolean bind(int id) {
        Entry entry = entry(id);
        if (entry == null) return false;
        if (entry.texture < 0) entry.texture = upload(entry);
        glBindTexture(GL_TEXTURE_2D, entry.texture);
        return true;
    }

    private static Entry entry(int id) {
        if (id < 0) return null;
        List<Entry> list = id % 2 == 0 ? vanilla : mods;
        return id / 2 < list.size() ? list.get(id / 2) : null;
    }

    /** Frees the GL textures and keeps the pixels, so they can be uploaded again in another context. */
    static synchronized void dispose() {
        for (List<Entry> list : Arrays.asList(vanilla, mods)) {
            for (Entry entry : list) {
                if (entry.texture >= 0) glDeleteTextures(entry.texture);
                entry.texture = -1;
            }
        }
    }

    /** Frees the GL textures and forgets the pixels; nothing draws the wall anymore. */
    static synchronized void clear() {
        dispose();
        vanilla.clear();
        mods.clear();
    }

    private static void load() {
        Random random = new Random();
        List<String> names = new ArrayList<>(Arrays.asList(VANILLA));
        Collections.shuffle(names, random);
        for (String name : names) {
            try (InputStream in = WallTextures.class
                .getResourceAsStream("/assets/minecraft/textures/blocks/" + name + ".png")) {
                Entry entry = in == null ? null : read(in, random, true);
                if (entry != null) add(vanilla, entry);
            } catch (Throwable t) {
                // a missing or broken vanilla texture just leaves a gap in the set
            }
        }
        if (!LoadingScreenModule.wallModBlocks) return;

        List<File> jars = modJars();
        Collections.shuffle(jars, random);
        for (File jar : jars) {
            if (count(mods) >= MAX_MOD_TEXTURES || LoadingTimer.isFinished()) break;
            try {
                readMod(jar, random);
                Thread.sleep(20); // the game is loading meanwhile, stay out of its way
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                GTNHTweaks.LOG.debug("Could not read block textures from " + jar, t);
            }
        }
    }

    private static List<File> modJars() {
        File home = Launch.minecraftHome != null ? Launch.minecraftHome : new File(".");
        File modsDir = new File(home, "mods");
        List<File> jars = new ArrayList<>();
        for (File dir : new File[] { modsDir, new File(modsDir, "1.7.10") }) {
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File file : files) {
                String name = file.getName()
                    .toLowerCase(Locale.ROOT);
                if (file.isFile() && (name.endsWith(".jar") || name.endsWith(".zip"))) jars.add(file);
            }
        }
        return jars;
    }

    private static void readMod(File jar, Random random) throws IOException {
        try (ZipFile zip = new ZipFile(jar)) {
            List<ZipEntry> candidates = new ArrayList<>();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (isCandidate(entry.getName())) candidates.add(entry);
            }
            Collections.shuffle(candidates, random);
            int accepted = 0;
            for (int i = 0; i < candidates.size() && i < PER_MOD * 5 && accepted < PER_MOD; i++) {
                try (InputStream in = zip.getInputStream(candidates.get(i))) {
                    Entry entry = read(in, random, false);
                    if (entry != null) {
                        add(mods, entry);
                        accepted++;
                    }
                } catch (Throwable t) {
                    // not a readable image, try the next one
                }
            }
        }
    }

    private static boolean isCandidate(String path) {
        String name = path.toLowerCase(Locale.ROOT);
        if (!name.startsWith("assets/") || !name.endsWith(".png") || name.startsWith("assets/minecraft/")) return false;
        int textures = name.indexOf("/textures/blocks/");
        if (textures < 0 || name.indexOf('/', "assets/".length()) != textures) return false;
        for (String skipped : SKIPPED) {
            if (name.contains(skipped)) return false;
        }
        return true;
    }

    /**
     * Reads a block texture, the first frame of an animated one, or returns null if it does not look like a full
     * opaque block: wrong size, holes, nearly black, or (sometimes) plain gray.
     */
    private static Entry read(InputStream in, Random random, boolean trusted) throws IOException {
        BufferedImage image = ImageIO.read(in);
        if (image == null) return null;
        int size = image.getWidth();
        if (size < 8 || size > 64 || image.getHeight() < size || image.getHeight() % size != 0) return null;
        int[] pixels = image.getRGB(0, 0, size, size, null, 0, size);
        if (!trusted) {
            int holes = 0;
            float brightness = 0, saturation = 0;
            for (int argb : pixels) {
                if ((argb >>> 24) < 200) holes++;
                int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
                int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
                brightness += max / 255F;
                saturation += max == 0 ? 0 : (max - min) / (float) max;
            }
            brightness /= pixels.length;
            saturation /= pixels.length;
            if (holes > pixels.length * 3 / 100 || brightness < 0.1F) return null;
            // Many mods (GregTech above all) keep gray textures that are tinted in game; let only some of them in
            if (saturation < 0.08F && random.nextFloat() < 0.6F) return null;
        }
        for (int i = 0; i < pixels.length; i++) pixels[i] |= 0xFF000000;
        return new Entry(pixels, size);
    }

    private static synchronized void add(List<Entry> list, Entry entry) {
        list.add(entry);
    }

    private static synchronized int count(List<Entry> list) {
        return list.size();
    }

    private static int upload(Entry entry) {
        IntBuffer pixels = BufferUtils.createIntBuffer(entry.pixels.length);
        pixels.put(entry.pixels);
        pixels.flip();

        glPushClientAttrib(GL_CLIENT_PIXEL_STORE_BIT);
        try {
            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            int texture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            glTexImage2D(
                GL_TEXTURE_2D,
                0,
                GL_RGBA,
                entry.size,
                entry.size,
                0,
                GL12.GL_BGRA,
                GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
                pixels);
            return texture;
        } finally {
            glPopClientAttrib();
        }
    }
}
