package gtnhtweaks.mixins.early.logs;

import java.util.List;

import net.minecraft.server.management.PlayerManager;
import net.minecraft.util.LongHashMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The chunks watched by players, for the server tick part of the Chunk generation log. */
@Mixin(PlayerManager.class)
public interface AccessorPlayerManager_ServerPerf {

    @Accessor("playerInstances")
    LongHashMap gtnhtweaks$getPlayerInstances();

    @Accessor("chunkWatcherWithPlayers")
    List<?> gtnhtweaks$getChunkWatcherWithPlayers();
}
