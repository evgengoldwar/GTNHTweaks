package gtnhtweaks.common;

import java.util.HashMap;
import java.util.Map;

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
                }
            });
        }
    }
}
