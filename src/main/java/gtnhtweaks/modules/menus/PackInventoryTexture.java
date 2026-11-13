package gtnhtweaks.modules.menus;

import java.awt.image.BufferedImage;
import java.io.InputStream;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;

import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.common.ResourcePackOverrides;

/**
 * The inventory texture of a resource pack for the inventory of Hodgepodge, which moves the crafting grid 2 pixels
 * right and 4 up and draws a texture of its own for it: a pack that replaces the vanilla inventory texture and not
 * that one would be left out. The texture of the pack is taken with its crafting part moved the same way, as
 * Hodgepodge makes its own from the vanilla one.
 */
public final class PackInventoryTexture {

    public static final ResourceLocation HODGEPODGE = new ResourceLocation(
        "hodgepodge",
        "textures/gui/inventory_new.png");
    public static final ResourceLocation VANILLA = new ResourceLocation("textures/gui/container/inventory.png");

    /** The crafting part of the inventory texture, in pixels of a 256 wide texture, and how far it is moved. */
    private static final int CRAFT_X0 = 86, CRAFT_X1 = 164, CRAFT_Y0 = 20, CRAFT_Y1 = 64;
    private static final int MOVE_X = 2, MOVE_Y = -4;

    private static ResourceLocation built;
    private static boolean tried;
    private static boolean listening;

    private PackInventoryTexture() {}

    /** The texture to draw in place of the given one, or the given one. */
    public static ResourceLocation replace(ResourceLocation texture) {
        if (!HODGEPODGE.equals(texture) || !ResourcePackOverrides.isOverridden(VANILLA)
            || ResourcePackOverrides.isOverridden(HODGEPODGE)) return texture;
        listen();
        if (!tried) {
            tried = true;
            built = build();
        }
        return built != null ? built : texture;
    }

    private static ResourceLocation build() {
        Minecraft mc = Minecraft.getMinecraft();
        try (InputStream in = mc.getResourceManager()
            .getResource(VANILLA)
            .getInputStream()) {
            BufferedImage pack = ImageIO.read(in);
            if (pack == null || pack.getWidth() < 256 || pack.getWidth() % 256 != 0) return null;
            DynamicTexture texture = new DynamicTexture(moveCrafting(pack, pack.getWidth() / 256));
            return mc.getTextureManager()
                .getDynamicTextureLocation("gtnhtweaks_inventory", texture);
        } catch (Exception e) {
            GTNHTweaks.LOG.warn("Could not adapt the inventory texture of the resource pack to Hodgepodge", e);
            return null;
        }
    }

    /**
     * The texture with its crafting part moved as Hodgepodge moves it. The strips it leaves at its left and bottom
     * take the panel next to them. Made from the vanilla texture, this is the texture of Hodgepodge to the pixel.
     */
    static BufferedImage moveCrafting(BufferedImage source, int scale) {
        int width = source.getWidth(), height = source.getHeight();
        BufferedImage moved = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) moved.setRGB(x, y, source.getRGB(x, y));
        int x0 = CRAFT_X0 * scale, x1 = CRAFT_X1 * scale, y0 = CRAFT_Y0 * scale, y1 = CRAFT_Y1 * scale;
        int dx = MOVE_X * scale, dy = MOVE_Y * scale;
        for (int y = y0; y < y1; y++) for (int x = x0; x < x0 + dx; x++) moved.setRGB(x, y, source.getRGB(x - dx, y));
        for (int y = y1 + dy; y < y1; y++)
            for (int x = x0; x < x1 + dx; x++) moved.setRGB(x, y, source.getRGB(x, y - dy));
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) moved.setRGB(x + dx, y + dy, source.getRGB(x, y));
        return moved;
    }

    private static void listen() {
        if (listening) return;
        listening = true;
        IResourceManager manager = Minecraft.getMinecraft()
            .getResourceManager();
        if (manager instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) manager).registerReloadListener(new IResourceManagerReloadListener() {

                @Override
                public void onResourceManagerReload(IResourceManager resourceManager) {
                    // the packs may have changed: made again when next drawn
                    if (built != null) Minecraft.getMinecraft()
                        .getTextureManager()
                        .deleteTexture(built);
                    built = null;
                    tried = false;
                }
            });
        }
    }
}
