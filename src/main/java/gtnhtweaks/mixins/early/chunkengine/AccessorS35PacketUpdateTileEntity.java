package gtnhtweaks.mixins.early.chunkengine;

import net.minecraft.network.play.server.S35PacketUpdateTileEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where the tile entity of a tile entity packet is, also on a dedicated server, where its getters are left out. */
@Mixin(S35PacketUpdateTileEntity.class)
public interface AccessorS35PacketUpdateTileEntity {

    @Accessor("field_148863_a")
    int gtnhtweaks$getX();

    @Accessor("field_148862_c")
    int gtnhtweaks$getZ();
}
