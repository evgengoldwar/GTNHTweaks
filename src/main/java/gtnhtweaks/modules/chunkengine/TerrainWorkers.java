package gtnhtweaks.modules.chunkengine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.gen.ChunkProviderEnd;
import net.minecraft.world.gen.ChunkProviderHell;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.modules.logs.ChunkGenLog;

/**
 * Makes the terrain of new chunks ahead, on threads of its own, near the players: the chunks that are neither loaded
 * nor saved, nearest first. When the server then needs such a chunk, it takes the one made, registers its structures
 * as for a chunk read from disk, and only populates it.
 * <p>
 * Each thread has a chunk generator of its own, made on the server thread like the one of the world, with a biome
 * source of its own where the generator keeps one. Only the generators known to keep their state in themselves are
 * used: Realistic World Gen, the Nether and the End of vanilla, and the ones added in the config. Structures are not
 * started on the threads: they load and save world data. The first chunks of a dimension are also made the usual way
 * and compared block by block; a difference, an error or a thread that hangs turns the threads off for the dimension.
 */
public final class TerrainWorkers {

    private static final String RWG = "rwg.world.ChunkGeneratorRealistic";
    /** Chunks made ahead and not taken yet, per dimension, at most. */
    private static final int MAX_PREPARED = 512;
    /** Positions looked at per player and tick, and of them looked up on disk. */
    private static final int SCAN_PER_TICK = 4096, DISK_CHECKS_PER_TICK = 256;
    /** How long the server waits for a chunk a thread is making, in milliseconds. */
    private static final long WAIT_MS = 2000;

    private static final Map<World, State> STATES = new ConcurrentHashMap<>();
    private static ThreadPoolExecutor executor;
    private static int threads;

    private TerrainWorkers() {}

    /** A thread making terrain. */
    static final class TerrainThread extends Thread {

        TerrainThread(Runnable task, int number) {
            super(task, "GTNHTweaks terrain " + number);
            setDaemon(true);
            setPriority(Thread.NORM_PRIORITY - 1);
        }
    }

    /** True on a thread making terrain: the structures are left to the server thread. */
    public static boolean onTerrainThread() {
        return Thread.currentThread() instanceof TerrainThread;
    }

    // ---- the server thread

    /** Once per tick of each world: drops what is no longer needed and starts the terrain of the next chunks. */
    public static void tick(WorldServer world) {
        if (!ChunkEngineModule.parallelTerrain || world.playerEntities.isEmpty()) return;
        State state = STATES.get(world);
        if (state == null) {
            state = new State(world);
            STATES.put(world, state);
        }
        if (state.disabled != null) return;
        int radius = MinecraftServer.getServer()
            .getConfigurationManager()
            .getViewDistance() + 1;
        if (world.getTotalWorldTime() % 20 == 0) state.prune(radius + 2);
        for (Object entity : world.playerEntities) {
            if (entity instanceof EntityPlayerMP) state.scan((EntityPlayerMP) entity, radius);
        }
    }

    /**
     * The chunk made ahead at the position, or null to make it the usual way. Waits for a thread that is making it.
     */
    public static Chunk take(WorldServer world, int x, int z) {
        State state = STATES.get(world);
        if (state == null || state.disabled != null) return null;
        Job job = state.jobs.remove(ChunkCoordIntPair.chunkXZ2Int(x, z));
        if (job == null) return null;
        if (job.status.compareAndSet(Job.QUEUED, Job.CANCELLED)) {
            state.inFlight.decrementAndGet();
            return null;
        }
        Chunk chunk = job.await(WAIT_MS);
        if (chunk != null) return chunk;
        if (job.error != null) {
            state.disable("the chunk generator failed on a thread at chunk " + x + ", " + z + ": " + job.error);
            GTNHTweaks.LOG.warn("Terrain thread failed", job.error);
        } else if (job.status.get() == Job.RUNNING) {
            state.disable("a thread took longer than " + WAIT_MS + " ms for chunk " + x + ", " + z);
        }
        return null;
    }

    /** The comparison of the first chunks: still on for the world. */
    static boolean verifying(WorldServer world) {
        State state = STATES.get(world);
        return state != null && state.verified < ChunkEngineModule.verifyChunks;
    }

    /** Compares a chunk made on a thread with the one made the usual way; a difference turns the threads off. */
    static void verify(WorldServer world, Chunk made, Chunk usual) {
        State state = STATES.get(world);
        if (state == null) return;
        state.verified++;
        ChunkEngine.VERIFIED_TERRAIN.add();
        String difference = difference(made, usual);
        if (difference != null) state.disable("a chunk made on a thread differs from the usual one: " + difference);
    }

    private static String difference(Chunk made, Chunk usual) {
        if (usual == null) return null;
        if (!Arrays.equals(made.getBiomeArray(), usual.getBiomeArray())) return "the biomes of chunk " + where(usual);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 256; y++) {
                    if (made.getBlock(x, y, z) != usual.getBlock(x, y, z)
                        || made.getBlockMetadata(x, y, z) != usual.getBlockMetadata(x, y, z)) {
                        return "the block at " + (usual.xPosition * 16 + x)
                            + " "
                            + y
                            + " "
                            + (usual.zPosition * 16 + z)
                            + " of chunk "
                            + where(usual);
                    }
                }
            }
        }
        return null;
    }

    private static String where(Chunk chunk) {
        return chunk.xPosition + ", " + chunk.zPosition;
    }

    /** What the threads did, for the summary of the Chunk generation log. */
    static String status() {
        StringBuilder out = new StringBuilder();
        for (State state : STATES.values()) {
            String name = state.world.provider.getDimensionName() + " (" + state.world.provider.dimensionId + ")";
            if (state.disabled != null) {
                out.append("  terrain threads off in ")
                    .append(name)
                    .append(": ")
                    .append(state.disabled)
                    .append('\n');
            } else {
                out.append(
                    String.format(
                        Locale.ROOT,
                        "  terrain threads in %s: %d threads, %d chunks made ahead and waiting, %d being made%n",
                        name,
                        threads,
                        state.jobs.size(),
                        state.inFlight.get()));
            }
        }
        return out.toString();
    }

    private static synchronized ThreadPoolExecutor executor() {
        if (executor == null) {
            threads = ChunkEngineModule.terrainThreads > 0 ? ChunkEngineModule.terrainThreads
                : Math.max(
                    1,
                    Math.min(
                        4,
                        Runtime.getRuntime()
                            .availableProcessors() / 4));
            AtomicInteger number = new AtomicInteger();
            executor = new ThreadPoolExecutor(
                threads,
                threads,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                task -> new TerrainThread(task, number.incrementAndGet()));
            executor.allowCoreThreadTimeOut(true);
        }
        return executor;
    }

    // ---- a dimension

    private static final class State {

        final WorldServer world;
        final Map<Long, Job> jobs = new ConcurrentHashMap<>();
        final AtomicInteger inFlight = new AtomicInteger();
        final Map<EntityPlayerMP, Scan> scans = new HashMap<>();
        /** The generators of the threads, each used by one thread at a time. */
        ArrayBlockingQueue<IChunkProvider> generators;
        volatile String disabled;
        int verified;

        State(WorldServer world) {
            this.world = world;
            try {
                setUp();
            } catch (Throwable t) {
                disabled = "its chunk generator could not be made again: " + t;
                GTNHTweaks.LOG.warn("Could not make the chunk generators of the terrain threads", t);
            }
        }

        /** Makes a generator per thread, on the server thread, as the world makes its own. */
        private void setUp() {
            IChunkProvider own = PreparedProvider.unwrap(world.theChunkProviderServer.currentChunkProvider);
            Class<?> type = own.getClass();
            boolean sharedBiomes = type == ChunkProviderHell.class || type == ChunkProviderEnd.class;
            boolean ownBiomes = type.getName()
                .equals(RWG)
                || Arrays.asList(ChunkEngineModule.extraGenerators)
                    .contains(type.getName());
            if (!sharedBiomes && !ownBiomes) {
                disabled = "its chunk generator " + type.getName() + " is not known to be safe on threads";
                return;
            }
            int count = executor().getMaximumPoolSize();
            List<IChunkProvider> made = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                IChunkProvider generator;
                if (ownBiomes) {
                    // the generator takes the biome source of the world when it is made: it gets a new one
                    WorldChunkManager biomes = world.provider.terrainType.getChunkManager(world);
                    if (biomes.getClass() != world.provider.worldChunkMgr.getClass()) {
                        disabled = "its biome source " + world.provider.worldChunkMgr.getClass()
                            .getName() + " could not be made again";
                        return;
                    }
                    WorldChunkManager saved = world.provider.worldChunkMgr;
                    world.provider.worldChunkMgr = biomes;
                    try {
                        generator = world.provider.createChunkGenerator();
                    } finally {
                        world.provider.worldChunkMgr = saved;
                    }
                } else {
                    generator = world.provider.createChunkGenerator();
                }
                if (generator == null || generator.getClass() != type) {
                    disabled = "the dimension makes another chunk generator than " + type.getName();
                    return;
                }
                made.add(generator);
            }
            generators = new ArrayBlockingQueue<>(count, false, made);
        }

        void disable(String why) {
            if (disabled != null) return;
            disabled = why;
            for (Job job : jobs.values()) {
                if (job.status.compareAndSet(Job.QUEUED, Job.CANCELLED)) inFlight.decrementAndGet();
            }
            jobs.clear();
            String text = "terrain threads off in " + world.provider.getDimensionName() + ": " + why;
            GTNHTweaks.LOG.warn("Chunk engine: " + text);
            ChunkGenLog.externalLine("ENGINE  " + text);
        }

        /** Drops the chunks made ahead that are loaded now, or far from every player. */
        void prune(int radius) {
            ChunkProviderServer server = world.theChunkProviderServer;
            for (Iterator<Map.Entry<Long, Job>> it = jobs.entrySet()
                .iterator(); it.hasNext();) {
                Map.Entry<Long, Job> entry = it.next();
                Job job = entry.getValue();
                boolean loaded = server.loadedChunkHashMap.getValueByKey(entry.getKey()) != null;
                if (!loaded && nearPlayer(job.x, job.z, radius)) continue;
                it.remove();
                if (job.status.compareAndSet(Job.QUEUED, Job.CANCELLED)) inFlight.decrementAndGet();
                else ChunkEngine.WASTED_TERRAIN.add();
            }
            scans.keySet()
                .removeIf(player -> !world.playerEntities.contains(player));
        }

        private boolean nearPlayer(int x, int z, int radius) {
            for (Object entity : world.playerEntities) {
                EntityPlayerMP player = (EntityPlayerMP) entity;
                int px = MathHelper.floor_double(player.posX) >> 4, pz = MathHelper.floor_double(player.posZ) >> 4;
                if (Math.abs(x - px) <= radius && Math.abs(z - pz) <= radius) return true;
            }
            return false;
        }

        /** Goes on around the player, ring by ring from its chunk, and starts the chunks that need terrain. */
        void scan(EntityPlayerMP player, int radius) {
            int maxInFlight = threads * 3;
            if (inFlight.get() >= maxInFlight || jobs.size() >= MAX_PREPARED) return;
            int px = MathHelper.floor_double(player.posX) >> 4, pz = MathHelper.floor_double(player.posZ) >> 4;
            Scan scan = scans.computeIfAbsent(player, p -> new Scan());
            long now = world.getTotalWorldTime();
            if (scan.x != px || scan.z != pz || now - scan.started > 100) scan.restart(px, pz, now);
            ChunkProviderServer server = world.theChunkProviderServer;
            AnvilChunkLoader disk = server.currentChunkLoader instanceof AnvilChunkLoader
                ? (AnvilChunkLoader) server.currentChunkLoader
                : null;
            if (disk == null) return;
            int looked = 0, diskChecks = 0;
            while (scan.ring <= radius && looked < SCAN_PER_TICK
                && diskChecks < DISK_CHECKS_PER_TICK
                && inFlight.get() < maxInFlight
                && jobs.size() < MAX_PREPARED) {
                int x = scan.x + scan.dx, z = scan.z + scan.dz;
                scan.next();
                looked++;
                long key = ChunkCoordIntPair.chunkXZ2Int(x, z);
                // the loaded chunks first: most are, and the map of loaded chunks takes the key unboxed
                if (server.loadedChunkHashMap.getValueByKey(key) != null || jobs.containsKey(key)) continue;
                diskChecks++;
                if (disk.chunkExists(world, x, z)) continue;
                Job job = new Job(this, x, z);
                jobs.put(key, job);
                inFlight.incrementAndGet();
                executor().execute(job);
            }
        }
    }

    /** Where the scan around a player is: square rings from its chunk outward. */
    private static final class Scan {

        int x, z, ring, dx, dz;
        long started;

        void restart(int x, int z, long now) {
            this.x = x;
            this.z = z;
            ring = 0;
            dx = 0;
            dz = 0;
            started = now;
        }

        /** The next position of the ring, or the first of the next ring. */
        void next() {
            if (ring == 0) {
                ring = 1;
                dx = -1;
                dz = -1;
                return;
            }
            // along the edges of the square: top row, right column, bottom row, left column
            if (dz == -ring && dx < ring) dx++;
            else if (dx == ring && dz < ring) dz++;
            else if (dz == ring && dx > -ring) dx--;
            else if (dx == -ring && dz > -ring + 1) dz--;
            else {
                ring++;
                dx = -ring;
                dz = -ring;
            }
        }
    }

    // ---- a chunk being made

    private static final class Job implements Runnable {

        static final int QUEUED = 0, RUNNING = 1, DONE = 2, FAILED = 3, CANCELLED = 4;

        final State state;
        final int x, z;
        final AtomicInteger status = new AtomicInteger(QUEUED);
        Chunk chunk;
        Throwable error;

        Job(State state, int x, int z) {
            this.state = state;
            this.x = x;
            this.z = z;
        }

        @Override
        public void run() {
            if (!status.compareAndSet(QUEUED, RUNNING)) return;
            IChunkProvider generator = null;
            try {
                generator = state.generators.take();
                chunk = generator.provideChunk(x, z);
            } catch (Throwable t) {
                error = t;
            } finally {
                if (generator != null) state.generators.offer(generator);
                state.inFlight.decrementAndGet();
                synchronized (this) {
                    status.set(chunk != null ? DONE : FAILED);
                    notifyAll();
                }
            }
        }

        /** The chunk once made, or null when it failed or took too long. */
        synchronized Chunk await(long millis) {
            long end = System.currentTimeMillis() + millis;
            while (status.get() == RUNNING) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) return null;
                try {
                    wait(left);
                } catch (InterruptedException e) {
                    Thread.currentThread()
                        .interrupt();
                    return null;
                }
            }
            return status.get() == DONE ? chunk : null;
        }
    }

    // ---- worlds closing

    public static final class Events {

        @SubscribeEvent
        public void onWorldUnload(WorldEvent.Unload event) {
            State state = STATES.remove(event.world);
            if (state == null) return;
            for (Job job : state.jobs.values()) job.status.compareAndSet(Job.QUEUED, Job.CANCELLED);
            state.jobs.clear();
        }
    }
}
