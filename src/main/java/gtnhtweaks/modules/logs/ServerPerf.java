package gtnhtweaks.modules.logs;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import gtnhtweaks.mixins.early.logs.AccessorChunkProviderServer_ServerPerf;
import gtnhtweaks.mixins.early.logs.AccessorPlayerManager_ServerPerf;
import gtnhtweaks.mixins.early.logs.AccessorWorld_ServerPerf;

/**
 * The whole server tick, for the Chunk generation log: where it goes by the sections of the profiler of Minecraft,
 * which the server always walks through even with the profiler off, how long the ticks are, the garbage collector,
 * every dimension, and the way of the chunks to the players: the queues, the packets and their compression.
 * Everything that keeps a big view distance from loading fast without lowering the TPS.
 */
public final class ServerPerf {

    private static final Object LOCK = new Object();
    private static final int MAX_DEPTH = 64;
    /** Snapshots of the dimensions every this many ticks. */
    private static final int SNAPSHOT_TICKS = 100;

    /** A section of the profiler under its parent, with its time over the period and the session. */
    private static final class Node {

        final String name;
        final Node parent;
        final Map<String, Node> children = new HashMap<>(4);
        final long[] nanos = new long[2], calls = new long[2];
        long tickNanos;
        long tickStamp = -1;

        Node(String name, Node parent) {
            this.name = name;
            this.parent = parent;
        }
    }

    /** What is counted over a period, [0], or the whole session, [1]. */
    private static final class Totals {

        int[] tickMicros = new int[1024];
        int ticks;
        long tickNanos;
        int over50, over100, over250;
        long playerQueue, playerQueueMax, queueSamples;
        int packets, packetChunks;
        long packetNanos;
        int resends;
        long resendNanos;
        long asyncLoadNanos;
        long gcCount, gcMillis;
        final Map<Integer, long[]> dimensions = new TreeMap<>();
        final Map<Integer, String> snapshots = new LinkedHashMap<>();

        void tick(long nanos) {
            if (ticks == tickMicros.length && tickMicros.length < 1 << 21)
                tickMicros = Arrays.copyOf(tickMicros, tickMicros.length * 2);
            if (ticks < tickMicros.length) tickMicros[ticks] = (int) Math.min(Integer.MAX_VALUE, nanos / 1000);
            ticks++;
            tickNanos += nanos;
            if (nanos > 50_000_000L) over50++;
            if (nanos > 100_000_000L) over100++;
            if (nanos > 250_000_000L) over250++;
        }
    }

    private static final Node ROOT = new Node("tick", null);
    private static final Totals[] TOTALS = { new Totals(), new Totals() };
    private static final Map<Integer, String> DIMENSION_NAMES = new HashMap<>();

    private static volatile Object profiler;
    private static volatile Thread serverThread;
    private static final Node[] STACK = new Node[MAX_DEPTH];
    private static final long[] STARTS = new long[MAX_DEPTH];
    private static int depth;
    private static long tickId;
    private static long tickStart;
    private static final List<Node> TOUCHED = new ArrayList<>();
    private static long worldTickStart, entitiesStart, asyncStart, packetStart, resendStart;
    private static int worldTickDim, entitiesDim;
    private static final long[] GC_AT_PERIOD_START = gc();
    private static long periodStart = System.currentTimeMillis(), sessionStart = periodStart;

    private ServerPerf() {}

    // ---- ticks

    /** At the start of a server tick, from the tick event. */
    static void tickBegins() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null) profiler = server.theProfiler;
        serverThread = Thread.currentThread();
        depth = 0;
        tickId++;
        TOUCHED.clear();
        tickStart = System.nanoTime();
    }

    /** At the end of a tick: its length; the line of a lagging tick with its costliest sections, or null. */
    static String tickEnds(long lagNanos) {
        if (tickStart == 0) return null;
        long nanos = System.nanoTime() - tickStart;
        tickStart = 0;
        long queue = 0;
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null && server.getConfigurationManager() != null) {
            for (Object player : server.getConfigurationManager().playerEntityList) {
                queue += ((EntityPlayerMP) player).loadedChunks.size();
            }
        }
        boolean snapshot = tickId % SNAPSHOT_TICKS == 0;
        Map<Integer, String> snapshots = snapshot ? snapshots() : null;
        synchronized (LOCK) {
            for (Totals totals : TOTALS) {
                totals.tick(nanos);
                totals.playerQueue += queue;
                totals.playerQueueMax = Math.max(totals.playerQueueMax, queue);
                totals.queueSamples++;
                if (snapshots != null) totals.snapshots.putAll(snapshots);
            }
        }
        return nanos >= lagNanos ? "LAG TICK  " + ChunkGenLog.ms(nanos) + ": " + costliest(nanos) : null;
    }

    /** The sections of this tick that took the most of it by their own time. */
    private static String costliest(long tick) {
        List<long[]> own = new ArrayList<>();
        for (int i = 0; i < TOUCHED.size(); i++) {
            Node node = TOUCHED.get(i);
            long children = 0;
            for (Node child : node.children.values()) if (child.tickStamp == tickId) children += child.tickNanos;
            own.add(new long[] { Math.max(0, node.tickNanos - children), i });
        }
        own.sort((a, b) -> Long.compare(b[0], a[0]));
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < Math.min(6, own.size()); i++) {
            if (own.get(i)[0] < tick / 50) break;
            if (text.length() > 0) text.append(", ");
            text.append(path(TOUCHED.get((int) own.get(i)[1])))
                .append(' ')
                .append(ChunkGenLog.ms(own.get(i)[0]));
        }
        return text.length() == 0 ? "no section of the profiler stands out" : text.toString();
    }

    private static String path(Node node) {
        StringBuilder path = new StringBuilder(node.name);
        for (Node up = node.parent; up != null && up != ROOT && !"root".equals(up.name); up = up.parent) {
            path.insert(0, up.name + ".");
        }
        return path.toString();
    }

    // ---- hooks

    /** Profiler.startSection of the server's profiler, on the server thread. */
    public static void startSection(Object which, String name) {
        if (which != profiler || Thread.currentThread() != serverThread || tickStart == 0) return;
        Node parent = depth == 0 ? ROOT : STACK[Math.min(depth, MAX_DEPTH) - 1];
        Node node = parent.children.get(name);
        if (node == null) {
            synchronized (LOCK) {
                node = new Node(name, parent);
                parent.children.put(name, node);
            }
        }
        if (depth < MAX_DEPTH) {
            STACK[depth] = node;
            STARTS[depth] = System.nanoTime();
        }
        depth++;
    }

    public static void endSection(Object which) {
        if (which != profiler || Thread.currentThread() != serverThread || depth == 0) return;
        depth--;
        if (depth >= MAX_DEPTH) return;
        Node node = STACK[depth];
        long nanos = System.nanoTime() - STARTS[depth];
        node.nanos[0] += nanos;
        node.nanos[1] += nanos;
        node.calls[0]++;
        node.calls[1]++;
        if (node.tickStamp != tickId) {
            node.tickStamp = tickId;
            node.tickNanos = 0;
            TOUCHED.add(node);
        }
        node.tickNanos += nanos;
    }

    public static void worldTickBegins(World world) {
        if (Thread.currentThread() != serverThread) return;
        worldTickDim = world.provider.dimensionId;
        worldTickStart = System.nanoTime();
    }

    public static void worldTickEnds(World world) {
        if (Thread.currentThread() != serverThread || worldTickStart == 0) return;
        addDimension(worldTickDim, 0, System.nanoTime() - worldTickStart, world);
        worldTickStart = 0;
    }

    public static void entitiesBegin(World world) {
        if (Thread.currentThread() != serverThread) return;
        entitiesDim = world.provider.dimensionId;
        entitiesStart = System.nanoTime();
    }

    public static void entitiesEnd(World world) {
        if (Thread.currentThread() != serverThread || entitiesStart == 0) return;
        addDimension(entitiesDim, 1, System.nanoTime() - entitiesStart, world);
        entitiesStart = 0;
    }

    private static void addDimension(int dimension, int what, long nanos, World world) {
        if (!DIMENSION_NAMES.containsKey(dimension)) {
            String name;
            try {
                name = world.provider.getDimensionName() + " (" + dimension + ")";
            } catch (RuntimeException e) {
                name = "dimension " + dimension;
            }
            synchronized (LOCK) {
                DIMENSION_NAMES.put(dimension, name);
            }
        }
        synchronized (LOCK) {
            for (Totals totals : TOTALS) totals.dimensions.computeIfAbsent(dimension, d -> new long[2])[what] += nanos;
        }
    }

    public static void asyncLoadsBegin() {
        if (Thread.currentThread() == serverThread) asyncStart = System.nanoTime();
    }

    public static void asyncLoadsEnd() {
        if (Thread.currentThread() != serverThread || asyncStart == 0) return;
        long nanos = System.nanoTime() - asyncStart;
        asyncStart = 0;
        synchronized (LOCK) {
            for (Totals totals : TOTALS) totals.asyncLoadNanos += nanos;
        }
    }

    /** A packet of many chunks for a player is built and compressed. */
    public static void packetBegins() {
        if (Thread.currentThread() == serverThread) packetStart = System.nanoTime();
    }

    public static void packetEnds(int chunks) {
        if (Thread.currentThread() != serverThread || packetStart == 0) return;
        long nanos = System.nanoTime() - packetStart;
        packetStart = 0;
        synchronized (LOCK) {
            for (Totals totals : TOTALS) {
                totals.packets++;
                totals.packetChunks += chunks;
                totals.packetNanos += nanos;
            }
        }
    }

    /** A packet of the sections of one chunk is built and compressed, as when many blocks of it changed. */
    public static void resendBegins() {
        if (Thread.currentThread() == serverThread) resendStart = System.nanoTime();
    }

    public static void resendEnds() {
        if (Thread.currentThread() != serverThread || resendStart == 0) return;
        long nanos = System.nanoTime() - resendStart;
        resendStart = 0;
        synchronized (LOCK) {
            for (Totals totals : TOTALS) {
                totals.resends++;
                totals.resendNanos += nanos;
            }
        }
    }

    // ---- snapshots

    /** What every dimension holds now, on the server thread. */
    private static Map<Integer, String> snapshots() {
        Map<Integer, String> snapshots = new LinkedHashMap<>();
        try {
            for (WorldServer world : DimensionManager.getWorlds()) {
                int loaded = world.theChunkProviderServer.loadedChunks.size();
                int unloading = ((AccessorChunkProviderServer_ServerPerf) world.theChunkProviderServer)
                    .gtnhtweaks$getChunksToUnload()
                    .size();
                int active = ((AccessorWorld_ServerPerf) world).gtnhtweaks$getActiveChunkSet()
                    .size();
                AccessorPlayerManager_ServerPerf players = (AccessorPlayerManager_ServerPerf) world.getPlayerManager();
                snapshots.put(
                    world.provider.dimensionId,
                    String.format(
                        Locale.ROOT,
                        "%d chunks loaded, %d to unload, %d active for random ticks, %d watched by players "
                            + "(%d with changes to send), %d players, %d entities, %d tile entities",
                        loaded,
                        unloading,
                        active,
                        players.gtnhtweaks$getPlayerInstances()
                            .getNumHashElements(),
                        players.gtnhtweaks$getChunkWatcherWithPlayers()
                            .size(),
                        world.playerEntities.size(),
                        world.loadedEntityList.size(),
                        world.loadedTileEntityList.size()));
            }
        } catch (RuntimeException | LinkageError ignored) {
            // a dimension unloading meanwhile, or an accessor another mod kept from applying: the next snapshot
        }
        return snapshots;
    }

    // ---- summary

    private static long[] gc() {
        long count = 0, millis = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            count += Math.max(0, bean.getCollectionCount());
            millis += Math.max(0, bean.getCollectionTime());
        }
        return new long[] { count, millis };
    }

    /** True if a tick of the period lagged, to write a summary even without new chunks. */
    static boolean periodLagged() {
        synchronized (LOCK) {
            return TOTALS[0].over50 > 0;
        }
    }

    /** Writes the part of the server tick of the summary of the period, [0], or the session, [1]. */
    static void summary(StringBuilder out, int which) {
        synchronized (LOCK) {
            Totals totals = TOTALS[which];
            long periodMillis = System.currentTimeMillis() - (which == 0 ? periodStart : sessionStart);
            if (which == 0) {
                long[] now = gc();
                totals.gcCount = now[0] - GC_AT_PERIOD_START[0];
                totals.gcMillis = now[1] - GC_AT_PERIOD_START[1];
                GC_AT_PERIOD_START[0] = now[0];
                GC_AT_PERIOD_START[1] = now[1];
                TOTALS[1].gcCount += totals.gcCount;
                TOTALS[1].gcMillis += totals.gcMillis;
            }
            if (totals.ticks == 0) {
                if (which == 0) resetPeriod();
                return;
            }
            int[] sorted = Arrays.copyOf(totals.tickMicros, Math.min(totals.ticks, totals.tickMicros.length));
            Arrays.sort(sorted);
            out.append("\nServer ticks: ")
                .append(totals.ticks);
            if (periodMillis > 0) out.append(
                String.format(Locale.ROOT, " in %.0f s, %.1f TPS", periodMillis / 1000.0, totals.ticks * 1000.0 / periodMillis));
            out.append(
                String.format(
                    Locale.ROOT,
                    "\n  tick time: average %s, median %s, 95%% %s, 99%% %s, worst %s\n",
                    ChunkGenLog.ms(totals.tickNanos / totals.ticks),
                    micros(sorted[sorted.length / 2]),
                    micros(sorted[(int) (sorted.length * 0.95)]),
                    micros(sorted[(int) Math.min(sorted.length - 1, sorted.length * 0.99)]),
                    micros(sorted[sorted.length - 1])));
            out.append(
                String.format(
                    Locale.ROOT,
                    "  ticks over 50 ms (below 20 TPS): %d, over 100 ms: %d, over 250 ms: %d\n",
                    totals.over50,
                    totals.over100,
                    totals.over250));
            out.append(
                String.format(
                    Locale.ROOT,
                    "  garbage collection: %d collections, %s of pauses; heap %s\n",
                    totals.gcCount,
                    ChunkGenLog.ms(totals.gcMillis * 1_000_000L),
                    heap()));

            out.append("\nChunks to the players:\n");
            MinecraftServer server = MinecraftServer.getServer();
            if (server != null && server.getConfigurationManager() != null) out.append("  view distance: ")
                .append(
                    server.getConfigurationManager()
                        .getViewDistance())
                .append(" chunks\n");
            out.append(
                String.format(
                    Locale.ROOT,
                    "  waiting to be sent to the players: %.0f on average, %d at most\n",
                    totals.playerQueue / (double) Math.max(1, totals.queueSamples),
                    totals.playerQueueMax));
            out.append(
                String.format(
                    Locale.ROOT,
                    "  sent: %d chunks in %d packets, built and compressed on the server thread in %s (%s per chunk)\n",
                    totals.packetChunks,
                    totals.packets,
                    ChunkGenLog.ms(totals.packetNanos),
                    ChunkGenLog.micros(totals.packetNanos / Math.max(1, totals.packetChunks))));
            out.append(
                String.format(
                    Locale.ROOT,
                    "  chunk sections sent again after many block changes: %d packets, %s\n",
                    totals.resends,
                    ChunkGenLog.ms(totals.resendNanos)));
            out.append("  chunks read from disk in the background, finished on the server thread: ")
                .append(ChunkGenLog.ms(totals.asyncLoadNanos))
                .append('\n');

            if (!totals.dimensions.isEmpty()) {
                out.append("\nDimensions (world tick: blocks, chunks, weather; entities: entities and tile entities):\n");
                for (Map.Entry<Integer, long[]> entry : totals.dimensions.entrySet()) {
                    String name = DIMENSION_NAMES.getOrDefault(entry.getKey(), "dimension " + entry.getKey());
                    out.append(
                        String.format(
                            Locale.ROOT,
                            "  %-28s world tick %9s, entities %9s  (%s of the ticks)\n",
                            name,
                            ChunkGenLog.ms(entry.getValue()[0]),
                            ChunkGenLog.ms(entry.getValue()[1]),
                            share(entry.getValue()[0] + entry.getValue()[1], totals.tickNanos)));
                    String snapshot = totals.snapshots.get(entry.getKey());
                    if (snapshot != null) out.append("      ")
                        .append(snapshot)
                        .append('\n');
                }
            }

            out.append("\nServer tick by section of the profiler (share of all tick time, per tick, calls per tick):\n");
            tree(out, ROOT, which, totals, 0);
            if (which == 0) resetPeriod();
        }
    }

    private static void resetPeriod() {
        TOTALS[0] = new Totals();
        reset(ROOT, 0);
        periodStart = System.currentTimeMillis();
    }

    /** A new world session: the totals of the session start again. */
    static void resetSession() {
        synchronized (LOCK) {
            TOTALS[1] = new Totals();
            reset(ROOT, 1);
            sessionStart = System.currentTimeMillis();
        }
    }

    private static void tree(StringBuilder out, Node node, int which, Totals totals, int level) {
        List<Node> children = new ArrayList<>(node.children.values());
        children.sort((a, b) -> Long.compare(b.nanos[which], a.nanos[which]));
        for (Node child : children) {
            long nanos = child.nanos[which];
            if (nanos * 200 < totals.tickNanos || level > 9) continue;
            out.append(
                String.format(
                    Locale.ROOT,
                    "  %6s %10s %9s %8.1f  %s%s\n",
                    share(nanos, totals.tickNanos),
                    ChunkGenLog.ms(nanos),
                    ChunkGenLog.micros(nanos / totals.ticks),
                    child.calls[which] / (double) totals.ticks,
                    repeat(level),
                    child.name));
            tree(out, child, which, totals, level + 1);
        }
    }

    private static void reset(Node node, int which) {
        node.nanos[which] = 0;
        node.calls[which] = 0;
        for (Node child : node.children.values()) reset(child, which);
    }

    private static String repeat(int level) {
        char[] spaces = new char[level * 2];
        Arrays.fill(spaces, ' ');
        return new String(spaces);
    }

    private static String heap() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean()
            .getHeapMemoryUsage();
        return String.format(
            Locale.ROOT,
            "%d of %d MB used",
            heap.getUsed() >> 20,
            (heap.getMax() > 0 ? heap.getMax() : heap.getCommitted()) >> 20);
    }

    private static String micros(int micros) {
        return ChunkGenLog.micros(micros * 1000L);
    }

    private static String share(long part, long whole) {
        return whole <= 0 ? "" : String.format(Locale.ROOT, "%.1f%%", part * 100.0 / whole);
    }
}
