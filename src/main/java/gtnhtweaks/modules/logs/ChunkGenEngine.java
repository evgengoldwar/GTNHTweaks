package gtnhtweaks.modules.logs;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The work of Minecraft itself inside chunk generation, for the Chunk generation log: setting blocks, light, the
 * notices to neighbors, structures and caves, vanilla features, biomes, noise, mob spawning, chunks read from disk.
 * Whatever generator runs, mod or vanilla, it pays for these, so they tell what to make faster in Minecraft.
 * <p>
 * The hooks are called from mixins into hot methods, also outside generation: there they return at once, as only the
 * thread that makes a chunk is measured, while it does. A hook nests in others, as light inside setting a block; every
 * one gets its own time, without the hooks inside it, so the times add up. The time goes to the step of the log that
 * runs, a mod generator or a decoration, and its own code is what is left.
 */
public final class ChunkGenEngine {

    private static final int MAX_DEPTH = 128;
    private static final ConcurrentHashMap<String, Integer> INDEX = new ConcurrentHashMap<>();
    private static volatile String[] names = new String[0];

    public static final int SET_BLOCK = kind("World.setBlock: the rest of it");
    public static final int CHUNK_SET = kind("Chunk.setBlock: block storage, height map, block added, tile entity");
    public static final int LIGHT = kind("light updates (World.updateLightByType)");
    public static final int LIGHT_CHECK = kind("light check of a populated chunk (Chunk.func_150809_p)");
    public static final int SKYLIGHT = kind("sky light map of a new chunk (Chunk.generateSkylightMap)");
    public static final int NOTIFY = kind("block change notices (World.markAndNotifyBlock): the rest of it");
    public static final int NEIGHBORS = kind("neighbor updates (World.notifyBlocksOfNeighborChange)");
    public static final int TICKS = kind("scheduled block ticks (WorldServer.scheduleBlockUpdate)");
    public static final int TILE_ENTITY = kind("tile entities placed (World.setTileEntity)");
    public static final int SPAWN = kind("mobs spawned with the chunk (SpawnerAnimals.performWorldGenSpawning)");
    public static final int BIOMES = kind("biome layers (WorldChunkManager)");
    public static final int NOISE = kind("vanilla noise (NoiseGeneratorOctaves)");
    public static final int DISK_LOAD = kind("chunks read from disk (neighbors of the generated ones)");

    private static final ClassValue<Integer> FEATURES = new ClassValue<Integer>() {

        @Override
        protected Integer computeValue(Class<?> type) {
            return kind("feature " + simpleName(type));
        }
    };
    private static final ClassValue<Integer> CARVERS = new ClassValue<Integer>() {

        @Override
        protected Integer computeValue(Class<?> type) {
            return kind("caves and structure layout " + simpleName(type) + " (MapGenBase)");
        }
    };
    private static final ClassValue<Integer> STRUCTURES = new ClassValue<Integer>() {

        @Override
        protected Integer computeValue(Class<?> type) {
            return kind("structure built " + simpleName(type) + " (MapGenStructure.generateStructuresInChunk)");
        }
    };

    /** The thread that makes chunks now, and its stack of hooks; null while no chunk is made. */
    private static volatile Thread thread;
    private static int depth;
    /** The depth at which the step that runs began: hooks below it belong to the step around it. */
    private static int base;
    private static final int[] KINDS = new int[MAX_DEPTH];
    private static final long[] STARTS = new long[MAX_DEPTH];
    private static final long[] CHILDREN = new long[MAX_DEPTH];
    /** Where the own times go: the step that runs. */
    private static Sink sink;

    /** Takes the time of the hooks. */
    interface Sink {

        void engine(int kind, long nanos);
    }

    private static boolean saving;
    private static long saveStart;

    private ChunkGenEngine() {}

    /** The index of a kind of work, made on first use. */
    static int kind(String name) {
        Integer index = INDEX.get(name);
        if (index != null) return index;
        synchronized (INDEX) {
            index = INDEX.get(name);
            if (index != null) return index;
            String[] grown = Arrays.copyOf(names, names.length + 1);
            grown[names.length] = name;
            index = names.length;
            names = grown;
            INDEX.put(name, index);
            return index;
        }
    }

    static String[] names() {
        return names;
    }

    private static String simpleName(Class<?> type) {
        String name = type.getName();
        return name.substring(name.lastIndexOf('.') + 1);
    }

    // ---- the steps of the log

    /** A step begins: from now on, and until it ends, the hooks of the thread are measured for it. */
    static int stepBegins(Sink step) {
        Thread current = Thread.currentThread();
        if (thread == null) {
            thread = current;
            depth = base = 0;
        } else if (thread != current) return -1;
        int stepBase = base;
        base = depth;
        sink = step;
        return stepBase;
    }

    /**
     * A step ended after {@code nanos}, begun at {@code stepBase}: the hook it ran inside, if any, does not count its
     * time as its own.
     */
    static void stepEnds(long nanos, int stepBase, Sink parent) {
        if (thread != Thread.currentThread() || stepBase < 0) return;
        if (depth > base) depth = base;
        if (base > 0 && base <= MAX_DEPTH) CHILDREN[base - 1] += nanos;
        base = stepBase;
        sink = parent;
        if (parent == null) {
            thread = null;
            depth = base = 0;
        }
    }

    /** Time spent by the log itself inside the hook that runs, which is not the time of the hook. */
    static void exclude(long nanos) {
        if (thread != Thread.currentThread() || depth <= 0 || depth > MAX_DEPTH) return;
        CHILDREN[depth - 1] += nanos;
    }

    // ---- hooks

    public static void enter(int kind) {
        if (thread != Thread.currentThread()) return;
        if (depth < MAX_DEPTH) {
            KINDS[depth] = kind;
            STARTS[depth] = System.nanoTime();
            CHILDREN[depth] = 0;
        }
        depth++;
    }

    public static void exit(int kind) {
        if (thread != Thread.currentThread() || depth <= base) return;
        depth--;
        if (depth >= MAX_DEPTH) return;
        if (KINDS[depth] != kind) {
            // a hook missed its end, after an exception: what is open is dropped
            depth = base;
            return;
        }
        long total = System.nanoTime() - STARTS[depth];
        if (depth > base) CHILDREN[depth - 1] += total;
        Sink step = sink;
        if (step != null) step.engine(kind, Math.max(0, total - CHILDREN[depth]));
    }

    public static void enterFeature(Object generator) {
        if (thread == Thread.currentThread()) enter(FEATURES.get(generator.getClass()));
    }

    public static void exitFeature(Object generator) {
        if (thread == Thread.currentThread()) exit(FEATURES.get(generator.getClass()));
    }

    public static void enterCarver(Object carver) {
        if (thread == Thread.currentThread()) enter(CARVERS.get(carver.getClass()));
    }

    public static void exitCarver(Object carver) {
        if (thread == Thread.currentThread()) exit(CARVERS.get(carver.getClass()));
    }

    public static void enterStructure(Object structure) {
        if (thread == Thread.currentThread()) enter(STRUCTURES.get(structure.getClass()));
    }

    public static void exitStructure(Object structure) {
        if (thread == Thread.currentThread()) exit(STRUCTURES.get(structure.getClass()));
    }

    // ---- saving, outside generation

    /** A chunk is saved on the server thread: written to NBT there, the file is written in the background. */
    public static void saveBegins() {
        // a save that failed with an exception never ended: this one starts anew
        if (!ChunkGenLog.isActive()) return;
        saving = true;
        saveStart = System.nanoTime();
    }

    public static void saveEnds() {
        if (!saving) return;
        saving = false;
        ChunkGenLog.chunkSaved(System.nanoTime() - saveStart);
    }
}
