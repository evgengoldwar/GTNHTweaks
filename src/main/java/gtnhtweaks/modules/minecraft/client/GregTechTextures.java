package gtnhtweaks.modules.minecraft.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.IBlockWithTextures;
import gregtech.api.interfaces.IColorModulationContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.ITexturedTileEntity;
import gregtech.common.render.GTRenderedTexture;
import gregtech.common.render.GTSidedTextureRender;
import gregtech.common.render.IIconTexture;
import gtnhtweaks.modules.minecraft.client.BlockChunks.Layer;

/**
 * The textures GregTech draws its blocks with, for the cubes of a broken block. GregTech draws a block by layers: a
 * base, as the stone of an ore or the hull of a machine, in the color of its material, and overlays over it, as the ore
 * itself or the front of a machine. The plain texture of the block is only a gray placeholder.
 * <p>
 * Only loaded when GregTech is: it is compiled against its classes.
 */
final class GregTechTextures {

    private static final int MAX_DEPTH = 4;

    private GregTechTextures() {}

    /**
     * The layers of every side of a block of GregTech, as it draws them, or null for a block it does not draw itself.
     * Blocks with a tile entity can only be read while it is there: when the player breaks it, not when told by the
     * server that another one did.
     */
    static Layer[][] layers(World world, int x, int y, int z, Block block, int meta) {
        ITexture[][] bySide = new ITexture[6][];
        if (block instanceof IBlockWithTextures) {
            IBlockWithTextures textured = (IBlockWithTextures) block;
            bySide = world.getBlock(x, y, z) == block ? textured.getTextures(world, x, y, z)
                : textured.getTextures(meta);
            if (bySide == null || bySide.length < 6) return null;
        } else {
            TileEntity tile = world.getTileEntity(x, y, z);
            if (!(tile instanceof ITexturedTileEntity)) return null;
            for (int side = 0; side < 6; side++) {
                bySide[side] = ((ITexturedTileEntity) tile).getTexture(block, ForgeDirection.getOrientation(side));
            }
        }
        Layer[][] layers = new Layer[6][];
        for (int side = 0; side < 6; side++) {
            List<Layer> found = new ArrayList<>();
            if (bySide[side] != null) {
                for (ITexture texture : bySide[side]) collect(texture, side, found, 0);
            }
            if (found.isEmpty()) return null;
            layers[side] = found.toArray(new Layer[0]);
        }
        return layers;
    }

    /** The icons of a texture of GregTech with their colors, under ones first; a texture of several opened. */
    private static void collect(ITexture texture, int side, List<Layer> out, int depth) {
        if (texture == null || depth > MAX_DEPTH || !texture.isValidTexture()) return;
        if (texture instanceof IIconTexture) {
            IIcon icon = ((IIconTexture) texture).getIcon(side, null);
            if (icon != null) out.add(new Layer(icon, color(texture)));
            if (texture instanceof GTRenderedTexture) {
                // drawn over the colored base as it is, uncolored
                IIcon overlay = ((GTRenderedTexture) texture).getIcon(ForgeDirection.getOrientation(side), true, null);
                if (overlay != null) out.add(new Layer(overlay, 0xFFFFFF));
            }
            return;
        }
        ITexture[] parts = parts(texture);
        if (parts == null) return;
        // a texture with one for each side, or one made of several drawn over each other
        if (texture instanceof GTSidedTextureRender && parts.length == 6) collect(parts[side], side, out, depth + 1);
        else for (ITexture part : parts) collect(part, side, out, depth + 1);
    }

    /** The color of a texture as GregTech gives it, white if it has none. */
    private static int color(ITexture texture) {
        if (!(texture instanceof IColorModulationContainer)) return 0xFFFFFF;
        short[] rgba = ((IColorModulationContainer) texture).getRGBA();
        if (rgba == null || rgba.length < 3) return 0xFFFFFF;
        return clamp(rgba[0]) << 16 | clamp(rgba[1]) << 8 | clamp(rgba[2]);
    }

    private static int clamp(short value) {
        return Math.max(0, Math.min(255, value));
    }

    /** The textures a texture made of several holds: its field mTextures, protected in GregTech. */
    private static ITexture[] parts(ITexture texture) {
        for (Class<?> type = texture.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField("mTextures");
                field.setAccessible(true);
                Object value = field.get(texture);
                return value instanceof ITexture[] ? (ITexture[]) value : null;
            } catch (NoSuchFieldException e) {
                // in a superclass
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
        return null;
    }
}
