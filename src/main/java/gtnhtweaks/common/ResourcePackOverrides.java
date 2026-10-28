package gtnhtweaks.common;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.util.ResourceLocation;

import gtnhtweaks.GTNHTweaks;

/**
 * Tells whether an enabled resource pack replaces a vanilla texture. Mod resources and the default pack do not count:
 * only the packs selected in the resource pack screen. The answers are cached until the resources are reloaded.
 */
public final class ResourcePackOverrides {

    private static final Map<ResourceLocation, Boolean> CACHE = new HashMap<>();
    private static final Map<ResourceLocation, Boolean> GRAY = new HashMap<>();
    /** The light gray of the vanilla inventory textures. */
    private static final int VANILLA_GRAY = 0xC6C6C6;
    private static boolean listenerRegistered;

    private ResourcePackOverrides() {}

    /** True if a selected resource pack contains a texture at {@code path} in the minecraft namespace. */
    public static boolean isOverridden(ResourceLocation location) {
        registerListener();
        Boolean overridden = CACHE.get(location);
        if (overridden == null) {
            overridden = lookup(location);
            CACHE.put(location, overridden);
        }
        return overridden;
    }

    /** True if any of the textures is replaced by a selected resource pack. */
    public static boolean isAnyOverridden(ResourceLocation... locations) {
        for (ResourceLocation location : locations) {
            if (isOverridden(location)) return true;
        }
        return false;
    }

    /**
     * True if the texture as the resources now give it is still mostly the light gray of the vanilla inventories: a
     * pack that only draws more on it, like a slot for the other hand, and not one with colors of its own.
     */
    public static boolean keepsVanillaGray(ResourceLocation location) {
        registerListener();
        Boolean gray = GRAY.get(location);
        if (gray == null) {
            gray = readGray(location);
            GRAY.put(location, gray);
        }
        return gray;
    }

    private static boolean readGray(ResourceLocation location) {
        try (InputStream in = Minecraft.getMinecraft()
            .getResourceManager()
            .getResource(location)
            .getInputStream()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) return false;
            int opaque = 0, gray = 0;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if (argb >>> 24 < 128) continue;
                opaque++;
                if ((argb & 0xFFFFFF) == VANILLA_GRAY) gray++;
            }
            // the gray panel is the most of an inventory texture, with the slots, borders and the player box
            return opaque > 0 && gray * 4 >= opaque;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean lookup(ResourceLocation location) {
        try {
            ResourcePackRepository repository = Minecraft.getMinecraft()
                .getResourcePackRepository();
            for (Object element : repository.getRepositoryEntries()) {
                ResourcePackRepository.Entry entry = (ResourcePackRepository.Entry) element;
                if (entry.getResourcePack()
                    .resourceExists(location)) return true;
            }
        } catch (RuntimeException e) {
            GTNHTweaks.LOG.warn("Could not check the resource packs for " + location, e);
        }
        return false;
    }

    private static void registerListener() {
        if (listenerRegistered) return;
        listenerRegistered = true;
        IResourceManager manager = Minecraft.getMinecraft()
            .getResourceManager();
        if (manager instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) manager).registerReloadListener(new IResourceManagerReloadListener() {

                @Override
                public void onResourceManagerReload(IResourceManager resourceManager) {
                    CACHE.clear();
                    GRAY.clear();
                }
            });
        }
    }
}
