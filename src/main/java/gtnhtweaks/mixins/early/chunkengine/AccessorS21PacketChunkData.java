package gtnhtweaks.mixins.early.chunkengine;

import net.minecraft.network.play.server.S21PacketChunkData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The chunk of a chunk packet, also on a dedicated server, where its getters are left out. */
@Mixin(S21PacketChunkData.class)
public interface AccessorS21PacketChunkData {

    @Accessor("field_149284_a")
    int gtnhtweaks$getChunkX();

    @Accessor("field_149282_b")
    int gtnhtweaks$getChunkZ();
}
