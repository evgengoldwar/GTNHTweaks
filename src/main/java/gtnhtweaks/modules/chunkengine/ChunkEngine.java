package gtnhtweaks.modules.chunkengine;

import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

/**
 * The state the chunk engine mixins share on the server thread, and what each part saved, for the Chunk generation
 * log.
 */
public final class ChunkEngine {

    /** Populations running on the server thread, nested when a population makes another chunk. */
    private static int populating;

    // what was saved, counted for the summary period and the whole session
    public static final Counter HELD_BACK = new Counter("chunks held back until their neighbors were populated");
    public static final Counter SENT_ON_TIMEOUT = new Counter("chunks sent before their neighbors, after the wait");
    public static final Counter DROPPED_COPIES = new Counter("copies of a chunk not sent to a player without it");
    public static final Counter SKIPPED_UPDATES = new Counter("block update rounds skipped for chunks nobody had");
    public static final Counter QUIET_BLOCKS = new Counter("neighbor notifications skipped in population");
    public static final Counter SKIPPED_LIGHT = new Counter("light updates skipped: the light did not change");
    public static final Counter THREAD_TERRAIN = new Counter("new chunks whose terrain was made on a thread");
    public static final Counter WASTED_TERRAIN = new Counter("terrain made on a thread but not needed");
    public static final Counter VERIFIED_TERRAIN = new Counter("terrain made on a thread checked against the usual");
    private static final Counter[] COUNTERS = { HELD_BACK, SENT_ON_TIMEOUT, DROPPED_COPIES, SKIPPED_UPDATES,
        QUIET_BLOCKS, SKIPPED_LIGHT, THREAD_TERRAIN, VERIFIED_TERRAIN, WASTED_TERRAIN };

    private ChunkEngine() {}

    // ---- population

    public static void beginPopulation() {
        populating++;
    }

    public static void endPopulation() {
        if (populating > 0) populating--;
    }

    /** At the start of a server tick: no population runs, also when one ended by an exception. */
    public static void tickStarts() {
        populating = 0;
    }

    /** The server thread populates a chunk and its blocks are set quietly. */
    public static boolean quiet() {
        return populating > 0 && ChunkEngineModule.quietPopulation;
    }

    // ---- sending chunks once

    /**
     * A chunk is done when it is populated and so are its neighbors to the west, the north and the north-west, whose
     * population writes into it. A neighbor that is not loaded does not hold it back.
     */
    public static boolean neighborsPopulated(Chunk chunk) {
        if (!chunk.isTerrainPopulated) return false;
        WorldServer world = (WorldServer) chunk.worldObj;
        int x = chunk.xPosition, z = chunk.zPosition;
        return populatedOrAbsent(world, x - 1, z) && populatedOrAbsent(world, x, z - 1)
            && populatedOrAbsent(world, x - 1, z - 1);
    }

    private static boolean populatedOrAbsent(WorldServer world, int x, int z) {
        Chunk chunk = (Chunk) world.theChunkProviderServer.loadedChunkHashMap
            .getValueByKey(ChunkCoordIntPair.chunkXZ2Int(x, z));
        return chunk == null || chunk.isTerrainPopulated;
    }

    /** The player is still to get the chunk: it waits in the list of chunks to send to the player. */
    public static boolean waitsFor(EntityPlayerMP player, int chunkX, int chunkZ) {
        List<?> pending = player.loadedChunks;
        try {
            // a for loop over get() would be slow on the linked list of vanilla
            for (Object entry : pending) {
                ChunkCoordIntPair pair = (ChunkCoordIntPair) entry;
                if (pair != null && pair.chunkXPos == chunkX && pair.chunkZPos == chunkZ) return true;
            }
        } catch (ConcurrentModificationException e) {
            // a packet sent from another thread while the server changes the list: sent as usual
        }
        return false;
    }

    // ---- light

    private static final ClassValue<Boolean> PLAIN_LIGHT = new ClassValue<Boolean>() {

        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("getLightValue", IBlockAccess.class, int.class, int.class, int.class)
                    .getDeclaringClass() == Block.class
                    && type.getMethod("getLightOpacity", IBlockAccess.class, int.class, int.class, int.class)
                        .getDeclaringClass() == Block.class;
            } catch (NoSuchMethodException | SecurityException e) {
                return false;
            }
        }
    };

    /**
     * The new block gives and lets through the same light as the old one, so a light update finds nothing to change.
     * Only for blocks whose light does not depend on their place or metadata, that is that keep the light methods of
     * Forge as Block has them.
     */
    public static boolean sameLight(Block before, Block after) {
        if (before == null || after == null) return false;
        if (before == after) return PLAIN_LIGHT.get(after.getClass());
        return before.getLightOpacity() == after.getLightOpacity() && before.getLightValue() == after.getLightValue()
            && PLAIN_LIGHT.get(before.getClass())
            && PLAIN_LIGHT.get(after.getClass());
    }

    // ---- counting

    /** A count of the summary period and of the whole session. */
    public static final class Counter {

        final String what;
        final AtomicLong period = new AtomicLong(), session = new AtomicLong();

        Counter(String what) {
            this.what = what;
        }

        public void add() {
            period.incrementAndGet();
            session.incrementAndGet();
        }

        public void add(long count) {
            period.addAndGet(count);
            session.addAndGet(count);
        }
    }

    /** What the chunk engine saved, for the summary of the period (0) or of the session (1); nothing if it did not run. */
    public static void summary(StringBuilder out, int slot) {
        StringBuilder lines = new StringBuilder();
        for (Counter counter : COUNTERS) {
            long count = (slot == 0 ? counter.period : counter.session).get();
            if (count == 0) continue;
            lines.append(String.format(Locale.ROOT, "  %,d %s%n", count, counter.what));
        }
        String terrain = TerrainWorkers.status();
        if (lines.length() == 0 && terrain.isEmpty()) return;
        out.append("Chunk engine (GTNHTweaks):\n")
            .append(lines);
        if (!terrain.isEmpty()) out.append(terrain);
    }

    public static void resetPeriod() {
        for (Counter counter : COUNTERS) counter.period.set(0);
    }

    public static void resetSession() {
        for (Counter counter : COUNTERS) counter.session.set(0);
    }
}
