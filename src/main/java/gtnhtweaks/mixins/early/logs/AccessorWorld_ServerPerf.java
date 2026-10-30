package gtnhtweaks.mixins.early.logs;

import java.util.Set;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The chunks around the players that get random block ticks, for the Chunk generation log. */
@Mixin(World.class)
public interface AccessorWorld_ServerPerf {

    @Accessor("activeChunkSet")
    Set<?> gtnhtweaks$getActiveChunkSet();
}
