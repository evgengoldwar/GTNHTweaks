package gtnhtweaks.modules.logs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gtnhtweaks.GTNHTweaks;

/**
 * The Chunk generation file of the logs: where the server time goes when new chunks are made, to find what lowers the
 * TPS while the world grows.
 * <p>
 * Every step is timed on the thread that runs it: the terrain of a new chunk (its chunk generator), the decoration of
 * the generator when the chunk is populated (vanilla features and the mods that hook into it through Forge events),
 * and every world generator of the mods (GameRegistry.registerWorldGenerator), each with its mod. The time of a step
 * is its own time, without the steps that ran inside it, so a generator that makes neighbor chunks is not charged
 * for their terrain twice. A chunk made while another one is populated is a cascade, a known cause of lag: it is
 * written with the generator that caused it.
 * <p>
 * Written as it happens: slow chunks, slow ticks and cascades on lines of their own. Every few minutes, and when the
 * world closes, a summary: the steps that took the most time, the cascades and the slowest chunks.
 */
public final class ChunkGenLog {

    public static final String TERRAIN = "terrain";
    public static final String DECORATION = "decoration";
    public static final String MOD_GENERATOR = "mod generator";
    private static final String POPULATION = "population";

    /** Lines of each kind written per summary period at most; the rest are only counted. */
    private static final int MAX_LINES_PER_PERIOD = 100;
    private static final int TOP_ROWS = 25;
    private static final int SLOWEST_CHUNKS = 10;

    private static final Object LOCK = new Object();
    private static final ThreadLocal<Deque<Frame>> STACK = ThreadLocal.withInitial(ArrayDeque::new);
    private static final Map<Class<?>, String> LABELS = new HashMap<>();
    private static final StringBuilder PENDING = new StringBuilder();

    private static volatile boolean active;
    private static File file;
    private static Stats period = new Stats();
    private static Stats session = new Stats();
    private static long periodStart = System.currentTimeMillis();
    /** Lines written and only counted in the period, by kind, so many cascades do not hide the slow ticks. */
    private static final Map<String, int[]> LINES = new LinkedHashMap<>();
    private static boolean headerWritten;

    /** The chunk generation of the tick that runs, on the server thread. */
    private static long tickStart;
    private static long tickChunkNanos;
    private static int tickNewChunks, tickPopulated;
    private static final Map<String, Long> TICK_PARTS = new HashMap<>();

    private ChunkGenLog() {}

    /** A step on the stack of the thread. */
    private static final class Frame implements ChunkGenEngine.Sink {

        final String stage, label, where;
        final World world;
        final int x, z;
        final long start = System.nanoTime();
        final long allocStart = MemoryLog.allocated();
        long childNanos, childAlloc;
        /** For a chunk, its terrain or its population: the own time of every step inside, by step. */
        Map<String, Long> parts;
        int cascades;
        boolean worked;
        /** The depth of the hooks of Minecraft when the step began, -1 when they are not measured for it. */
        int engineBase = -1;
        /** The own time and the calls of the work of Minecraft inside the step, by kind. */
        long[] engineNanos = new long[0], engineCalls = new long[0];

        @Override
        public void engine(int kind, long nanos) {
            if (kind >= engineNanos.length) {
                engineNanos = Arrays.copyOf(engineNanos, kind + 8);
                engineCalls = Arrays.copyOf(engineCalls, kind + 8);
            }
            engineNanos[kind] += nanos;
            engineCalls[kind]++;
        }

        Frame(String stage, String label, World world, int x, int z) {
            this.stage = stage;
            this.label = label;
            this.world = world;
            this.where = where(world);
            this.x = x;
            this.z = z;
            if (TERRAIN.equals(stage) || POPULATION.equals(stage)) parts = new LinkedHashMap<>();
        }
    }

    /** The time of one step, in one dimension, over a period. */
    private static final class Stat {

        final String stage, label, where;
        long nanos, max, alloc;
        int count;
        long[] engineNanos = new long[0], engineCalls = new long[0];

        Stat(String stage, String label, String where) {
            this.stage = stage;
            this.label = label;
            this.where = where;
        }
    }

    private static final class Cascade {

        final String label;
        int count;
        final List<String> examples = new ArrayList<>();

        Cascade(String label) {
            this.label = label;
        }
    }

    private static final class Slow {

        final long nanos;
        final String text;

        Slow(long nanos, String text) {
            this.nanos = nanos;
            this.text = text;
        }
    }

    /** What was measured over a period or the whole session. */
    private static final class Stats {

        final Map<String, Stat> steps = new HashMap<>();
        final Map<String, Integer> newChunks = new LinkedHashMap<>();
        final Map<String, Cascade> cascades = new LinkedHashMap<>();
        final List<Slow> slowest = new ArrayList<>();
        int populated;
        long nanos, alloc;
        long[] engineNanos = new long[0], engineCalls = new long[0];
        int saves;
        long saveNanos;
        int ticks, slowTicks;
        long worstTick;
        String worstTickAt = "";

        boolean isEmpty() {
            return nanos == 0 && newChunks.isEmpty();
        }
    }

    // ---- start

    /** Starts the log: the tick and world events, and the writer. */
    static void start() {
        if (active) return;
        active = true;
        MemoryLog.start();
        ChunkGenLog listener = new ChunkGenLog();
        FMLCommonHandler.instance()
            .bus()
            .register(listener);
        MinecraftForge.EVENT_BUS.register(listener);
        ScheduledExecutorService writer = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "GTNHTweaks chunk generation log");
            thread.setDaemon(true);
            return thread;
        });
        writer.scheduleWithFixedDelay(ChunkGenLog::tick, 2, 2, TimeUnit.SECONDS);
        Runtime.getRuntime()
            .addShutdownHook(new Thread(() -> {
                summarize(true);
                flush();
            }, "GTNHTweaks chunk generation log shutdown"));
    }

    // ---- hooks of the mixins

    /** Begins a step; gives what {@link #end} takes. */
    public static Object begin(String stage, Object source, World world, int x, int z) {
        if (!active) return null;
        Deque<Frame> stack = STACK.get();
        // noted before the step starts, so writing it down is not part of its time
        if (TERRAIN.equals(stage) && !stack.isEmpty()) {
            long noted = System.nanoTime();
            cascade(stack, where(world), x, z);
            // writing it down is not the time of what runs around
            long bookkeeping = System.nanoTime() - noted;
            stack.peek().childNanos += bookkeeping;
            ChunkGenEngine.exclude(bookkeeping);
        }
        Frame chunk = chunkOf(stack);
        if (chunk != null) chunk.worked = true;
        Frame frame = new Frame(stage, label(source), world, x, z);
        stack.push(frame);
        frame.engineBase = ChunkGenEngine.stepBegins(frame);
        return frame;
    }

    /** Ends the step and every one left open inside it, as after an exception. */
    public static void end(Object token) {
        if (!(token instanceof Frame)) return;
        Frame frame = (Frame) token;
        Deque<Frame> stack = STACK.get();
        if (!stack.contains(frame)) return;
        while (!stack.isEmpty()) {
            Frame top = stack.pop();
            finish(top, stack);
            if (top == frame) break;
        }
    }

    /** Begins the population of a chunk; most calls find it populated already and are not counted. */
    public static void beginPopulation(Object generator, World world, int x, int z) {
        if (!active) return;
        Frame frame = new Frame(POPULATION, label(generator), world, x, z);
        STACK.get()
            .push(frame);
        frame.engineBase = ChunkGenEngine.stepBegins(frame);
    }

    /** Ends the innermost population of the chunk, and the steps left open inside it. */
    public static void endPopulation(int x, int z) {
        endChunk(POPULATION, x, z);
    }

    /** Ends the innermost terrain of the chunk begun by {@link #begin}, and the steps left open inside it. */
    public static void endTerrain(int x, int z) {
        endChunk(TERRAIN, x, z);
    }

    private static void endChunk(String stage, int x, int z) {
        if (!active) return;
        for (Frame frame : STACK.get()) {
            if (stage.equals(frame.stage) && frame.x == x && frame.z == z) {
                end(frame);
                return;
            }
        }
    }

    static boolean isActive() {
        return active;
    }

    /** A chunk was saved on the server thread, outside generation: new chunks are saved too. */
    static void chunkSaved(long nanos) {
        synchronized (LOCK) {
            for (Stats stats : new Stats[] { period, session }) {
                if (stats.isEmpty()) continue;
                stats.saves++;
                stats.saveNanos += nanos;
            }
        }
    }

    private static long[] add(long[] into, long[] values) {
        if (values.length > into.length) into = Arrays.copyOf(into, values.length);
        for (int i = 0; i < values.length; i++) into[i] += values[i];
        return into;
    }

    // ---- measuring

    private static Frame chunkOf(Deque<Frame> stack) {
        for (Frame frame : stack) {
            if (frame.parts != null) return frame;
        }
        return null;
    }

    private static void cascade(Deque<Frame> stack, String where, int x, int z) {
        Frame cause = null;
        for (Frame frame : stack) {
            // the innermost step that runs code of its own: a mod generator or a decoration
            if (MOD_GENERATOR.equals(frame.stage) || DECORATION.equals(frame.stage) || TERRAIN.equals(frame.stage)) {
                cause = frame;
                break;
            }
        }
        Frame populated = null;
        for (Frame frame : stack) {
            if (POPULATION.equals(frame.stage) || TERRAIN.equals(frame.stage)) {
                populated = frame;
                break;
            }
        }
        if (populated != null) populated.cascades++;
        String culprit = cause == null ? "unknown" : cause.stage + " " + cause.label;
        String example = where + " "
            + chunkText(x, z)
            + (populated == null ? ""
                : " while " + (TERRAIN.equals(populated.stage) ? "making the terrain of " : "populating ")
                    + chunkText(populated.x, populated.z));
        synchronized (LOCK) {
            for (Stats stats : new Stats[] { period, session }) {
                Cascade c = stats.cascades.computeIfAbsent(culprit, Cascade::new);
                c.count++;
                if (c.examples.size() < 3) c.examples.add(example);
            }
            line("CASCADE  " + example + ", caused by " + culprit);
        }
    }

    private static void finish(Frame frame, Deque<Frame> stack) {
        long end = System.nanoTime();
        long total = end - frame.start;
        try {
            record(frame, stack, total);
        } finally {
            // the bookkeeping of the log is not the time of the step around
            Frame parent = stack.peek();
            if (parent != null) parent.childNanos += System.nanoTime() - end;
        }
    }

    private static void record(Frame frame, Deque<Frame> stack, long total) {
        long allocTotal = frame.allocStart < 0 ? 0 : Math.max(0, MemoryLog.allocated() - frame.allocStart);
        long allocOwn = Math.max(0, allocTotal - frame.childAlloc);
        if (stack.peek() != null) stack.peek().childAlloc += allocTotal;
        long own = Math.max(0, total - frame.childNanos);
        Frame parent = stack.peek();
        if (parent != null) parent.childNanos += total;
        ChunkGenEngine.stepEnds(total, frame.engineBase, parent);
        // a population that found the chunk populated did nothing
        if (POPULATION.equals(frame.stage) && !frame.worked) return;

        Frame chunk = chunkOf(stack);
        if (chunk != null) {
            // a step inside a chunk is one of its parts; a chunk made or populated inside it, with all it took
            if (frame.parts == null) chunk.parts.merge(frame.stage + " " + frame.label, own, Long::sum);
            // a new chunk populates itself and its neighbors once they are all there; anything else inside a chunk
            // is a cascade
            else if (TERRAIN.equals(chunk.stage) && POPULATION.equals(frame.stage)) {
                chunk.parts.merge("population " + frame.label, total, Long::sum);
            } else chunk.parts.merge("cascaded " + frame.stage + " " + frame.label, total, Long::sum);
        }
        if (frame.parts != null && own > 0) {
            frame.parts
                .merge(POPULATION.equals(frame.stage) ? "other" : frame.stage + " " + frame.label, own, Long::sum);
        }
        boolean slow = frame.parts != null && total >= LogsModule.chunkSlowMs * 1_000_000L;
        String biome = slow ? biome(frame.world, frame.x, frame.z) : "";

        synchronized (LOCK) {
            // the period starts with its first chunk, not when the last one ended or the game started
            if (period.isEmpty()) periodStart = System.currentTimeMillis() - total / 1_000_000;
            for (Stats stats : new Stats[] { period, session }) {
                if (!POPULATION.equals(frame.stage)) {
                    Stat stat = stats.steps.computeIfAbsent(
                        frame.stage + "|" + frame.label + "|" + frame.where,
                        key -> new Stat(frame.stage, frame.label, frame.where));
                    stat.count++;
                    stat.nanos += own;
                    stat.max = Math.max(stat.max, own);
                    stat.alloc += allocOwn;
                    stat.engineNanos = add(stat.engineNanos, frame.engineNanos);
                    stat.engineCalls = add(stat.engineCalls, frame.engineCalls);
                }
                stats.engineNanos = add(stats.engineNanos, frame.engineNanos);
                stats.engineCalls = add(stats.engineCalls, frame.engineCalls);
                if (TERRAIN.equals(frame.stage)) stats.newChunks.merge(frame.where, 1, Integer::sum);
                if (POPULATION.equals(frame.stage)) stats.populated++;
                if (parent == null) {
                    stats.nanos += total;
                    stats.alloc += allocTotal;
                }
            }
            if (parent == null) {
                tickChunkNanos += total;
            }
            if (TERRAIN.equals(frame.stage)) tickNewChunks++;
            if (POPULATION.equals(frame.stage)) tickPopulated++;
            if (frame.parts == null) TICK_PARTS.merge(frame.stage + " " + frame.label, own, Long::sum);
            else if (TERRAIN.equals(frame.stage)) TICK_PARTS.merge(TERRAIN + " " + frame.label, own, Long::sum);

            if (slow) slowChunk(frame, total, biome);
        }
    }

    private static void slowChunk(Frame frame, long total, String biome) {
        String text = ms(total) + "  "
            + frame.where
            + " "
            + chunkText(frame.x, frame.z)
            + biome
            + "  "
            + (TERRAIN.equals(frame.stage) ? "terrain by " + frame.label : "population")
            + (frame.cascades > 0
                ? ", made " + frame.cascades + (frame.cascades == 1 ? " more chunk" : " more chunks") + " (cascade)"
                : "")
            + ": "
            + parts(frame.parts, 6);
        for (Stats stats : new Stats[] { period, session }) {
            stats.slowest.add(new Slow(total, text));
            stats.slowest.sort((a, b) -> Long.compare(b.nanos, a.nanos));
            while (stats.slowest.size() > SLOWEST_CHUNKS) stats.slowest.remove(stats.slowest.size() - 1);
        }
        line("SLOW CHUNK  " + text);
    }

    private static String parts(Map<String, Long> parts, int max) {
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(parts.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        StringBuilder text = new StringBuilder();
        int shown = 0;
        long rest = 0;
        for (Map.Entry<String, Long> part : sorted) {
            if (shown < max) {
                if (shown > 0) text.append(", ");
                text.append(part.getKey())
                    .append(' ')
                    .append(ms(part.getValue()));
                shown++;
            } else rest += part.getValue();
        }
        if (rest > 0) text.append(", the rest ")
            .append(ms(rest));
        return text.length() == 0 ? "nothing measured inside" : text.toString();
    }

    // ---- ticks

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            if (LogsModule.serverTickDetails) ServerPerf.tickBegins();
            synchronized (LOCK) {
                tickStart = System.nanoTime();
                tickChunkNanos = 0;
                tickNewChunks = tickPopulated = 0;
                TICK_PARTS.clear();
            }
            return;
        }
        String lag = LogsModule.serverTickDetails ? ServerPerf.tickEnds(LogsModule.chunkLagTickMs * 1_000_000L) : null;
        synchronized (LOCK) {
            if (lag != null) line(lag);
            if (tickStart == 0) return;
            long tick = System.nanoTime() - tickStart;
            for (Stats stats : new Stats[] { period, session }) {
                // the ticks since chunks began to be made
                if (stats.isEmpty()) continue;
                stats.ticks++;
                if (tickChunkNanos > 0 && tick > stats.worstTick) {
                    stats.worstTick = tick;
                    stats.worstTickAt = LogFiles.time(System.currentTimeMillis());
                }
            }
            if (tickChunkNanos >= LogsModule.chunkSlowTickMs * 1_000_000L) {
                period.slowTicks++;
                session.slowTicks++;
                line(
                    "SLOW TICK  " + ms(tick)
                        + ", of it chunk generation "
                        + ms(tickChunkNanos)
                        + " ("
                        + tickNewChunks
                        + " new, "
                        + tickPopulated
                        + " populated): "
                        + parts(TICK_PARTS, 5));
            }
            tickStart = 0;
        }
    }

    /** The summary of the session of a world when it closes, in singleplayer when going back to the menu. */
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world.isRemote || event.world.provider.dimensionId != 0) return;
        summarize(true);
    }

    // ---- writing

    /** A line from another thread, as of the garbage collector. */
    static void externalLine(String text) {
        if (!active) return;
        synchronized (LOCK) {
            line(text);
        }
    }

    private static void line(String text) {
        String kind = text.substring(0, text.indexOf("  "));
        int[] count = LINES.computeIfAbsent(kind, k -> new int[2]);
        if (count[0] >= MAX_LINES_PER_PERIOD) {
            count[1]++;
            return;
        }
        count[0]++;
        PENDING.append(LogFiles.time(System.currentTimeMillis()))
            .append("  ")
            .append(text)
            .append('\n');
    }

    /** Every 2 seconds on the writer thread: the summary when its period is over, then the pending lines. */
    private static void tick() {
        try {
            MemoryLog.sampleThreads();
            if (System.currentTimeMillis() - periodStart >= LogsModule.chunkSummaryMinutes * 60_000L) summarize(false);
            flush();
        } catch (Throwable t) {
            GTNHTweaks.LOG.warn("Could not write the chunk generation log", t);
        }
    }

    /** Writes the summary of the period, or of the whole session, which then starts again. */
    private static void summarize(boolean whole) {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            // the server tick part, counted for the period anyway, written with the summary
            StringBuilder ticks = new StringBuilder();
            boolean lagged = ServerPerf.periodLagged();
            ServerPerf.summary(ticks, 0);
            if (!period.isEmpty() || lagged) {
                for (Map.Entry<String, int[]> entry : LINES.entrySet()) {
                    if (entry.getValue()[1] > 0) PENDING.append("... and ")
                        .append(entry.getValue()[1])
                        .append(" more ")
                        .append(entry.getKey())
                        .append(" lines in this period, only counted in the summary\n");
                }
                PENDING.append(summary(period, "the last " + minutes(now - periodStart), now - periodStart));
                PENDING.append(ticks)
                    .append('\n');
            }
            period = new Stats();
            periodStart = now;
            LINES.clear();
            if (whole && !session.isEmpty()) {
                PENDING.append(summary(session, "the whole world session", 0));
                StringBuilder sessionTicks = new StringBuilder();
                ServerPerf.summary(sessionTicks, 1);
                PENDING.append(sessionTicks)
                    .append('\n');
                session = new Stats();
            }
            if (whole) {
                ServerPerf.resetSession();
                MemoryLog.resetSession();
            }
        }
    }

    private static void flush() {
        String text;
        synchronized (LOCK) {
            if (PENDING.length() == 0) return;
            text = PENDING.toString();
            PENDING.setLength(0);
            if (!headerWritten) {
                text = header() + text;
                headerWritten = true;
            }
        }
        if (file == null) file = LogFiles.sessionFile(LogFiles.KIND_CHUNKS);
        if (file == null) return;
        try (Writer out = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
            out.write(text);
        } catch (IOException e) {
            GTNHTweaks.LOG.warn("Could not write {}", file, e);
        }
    }

    private static String header() {
        return "Chunk generation of " + LogFiles.sessionText()
            + "\n\n"
            + MemoryLog.jvm()
            + "\n"
            + "Where the server time goes when new chunks are made. Times are own times: a step does not count the\n"
            + "steps that ran inside it. Steps:\n"
            + "  terrain        a new chunk is made: the chunk generator of the dimension makes its blocks (and the\n"
            + "                 populations it starts, written apart, are not in its own time)\n"
            + "  decoration     the chunk generator populates a chunk: vanilla ores, trees, lakes, structures,\n"
            + "                 and the mods that generate through Forge events (DecorateBiome, OreGen, PopulateChunk)\n"
            + "  mod generator  a world generator a mod registered (GameRegistry.registerWorldGenerator)\n"
            + "Lines while it runs:\n"
            + "  SLOW CHUNK   a chunk whose terrain or population took longer than "
            + LogsModule.chunkSlowMs
            + " ms, with its steps\n"
            + "  SLOW TICK    a server tick in which chunk generation took longer than "
            + LogsModule.chunkSlowTickMs
            + " ms (50 ms is a whole tick at 20 TPS)\n"
            + "  GC PAUSE     a pause of the garbage collector of 50 ms or more: the server and the game stood still\n"
            + "  LAG TICK     a server tick longer than "
            + LogsModule.chunkLagTickMs
            + " ms for any reason, with the sections of the profiler that took most of it\n"
            + "  CASCADE      a chunk made while another was populated: a generator reached outside its chunk.\n"
            + "               A cascade makes more chunks, which are populated in turn, and lags the server.\n"
            + "A summary every "
            + LogsModule.chunkSummaryMinutes
            + " minutes while chunks are made, and one of the whole session when the world closes.\n\n";
    }

    private static String summary(Stats stats, String what, long periodMillis) {
        StringBuilder out = new StringBuilder();
        out.append("\n======== Summary of ")
            .append(what)
            .append(", ")
            .append(LogFiles.time(System.currentTimeMillis()))
            .append(" ========\n");
        int newChunks = 0;
        for (int count : stats.newChunks.values()) newChunks += count;
        out.append("New chunks: ")
            .append(newChunks);
        if (!stats.newChunks.isEmpty()) {
            out.append(" (");
            boolean first = true;
            for (Map.Entry<String, Integer> entry : stats.newChunks.entrySet()) {
                if (!first) out.append(", ");
                first = false;
                out.append(entry.getKey())
                    .append(' ')
                    .append(entry.getValue());
            }
            out.append(')');
        }
        out.append(", chunks populated: ")
            .append(stats.populated)
            .append('\n');
        out.append("Server time spent making chunks: ")
            .append(ms(stats.nanos));
        if (periodMillis > 0) out.append(
            String.format(
                Locale.ROOT,
                " (%.1f%% of the %s)",
                stats.nanos / 1e6 * 100 / periodMillis,
                minutes(periodMillis)));
        if (newChunks > 0) out.append(", ")
            .append(ms(stats.nanos / newChunks))
            .append(" per new chunk");
        if (stats.alloc > 0) out.append("; memory allocated ")
            .append(MemoryLog.megabytes(stats.alloc))
            .append(newChunks > 0 ? ", " + (stats.alloc / newChunks >> 10) + " KB per new chunk" : "");
        out.append('\n');
        out.append("Ticks: ")
            .append(stats.ticks)
            .append(", slowed by chunk generation (over ")
            .append(LogsModule.chunkSlowTickMs)
            .append(" ms): ")
            .append(stats.slowTicks);
        if (stats.worstTick > 0) out.append(", worst tick with chunk generation ")
            .append(ms(stats.worstTick))
            .append(" at ")
            .append(stats.worstTickAt);
        out.append("\n\n");

        // by stage
        Map<String, long[]> byStage = new LinkedHashMap<>();
        for (String stage : new String[] { TERRAIN, DECORATION, MOD_GENERATOR }) byStage.put(stage, new long[2]);
        for (Stat stat : stats.steps.values()) {
            long[] sum = byStage.get(stat.stage);
            if (sum == null) continue;
            sum[0] += stat.nanos;
            sum[1] += stat.count;
        }
        out.append("By step:\n");
        for (Map.Entry<String, long[]> entry : byStage.entrySet()) {
            out.append(
                String.format(
                    Locale.ROOT,
                    "  %-14s %10s  %s\n",
                    entry.getKey(),
                    ms(entry.getValue()[0]),
                    share(entry.getValue()[0], stats.nanos)));
        }

        // the steps that cost the most
        List<Stat> steps = new ArrayList<>(stats.steps.values());
        steps.sort((a, b) -> Long.compare(b.nanos, a.nanos));
        out.append("\nWhat took the most time (own time):\n");
        out.append(
            String.format(
                Locale.ROOT,
                "  %10s %6s %8s %10s %10s %10s  %s\n",
                "time",
                "share",
                "calls",
                "average",
                "max",
                "allocated",
                "step"));
        for (int i = 0; i < Math.min(TOP_ROWS, steps.size()); i++) {
            Stat stat = steps.get(i);
            out.append(
                String.format(
                    Locale.ROOT,
                    "  %10s %6s %8d %10s %10s %10s  %s %s  ·  %s\n",
                    ms(stat.nanos),
                    share(stat.nanos, stats.nanos),
                    stat.count,
                    ms(stat.nanos / Math.max(1, stat.count)),
                    ms(stat.max),
                    MemoryLog.megabytes(stat.alloc),
                    stat.stage,
                    stat.label,
                    stat.where));
        }
        if (steps.size() > TOP_ROWS) out.append("  ... and ")
            .append(steps.size() - TOP_ROWS)
            .append(" more\n");

        // cascades
        out.append("\nCascades (chunks made while another was populated):");
        if (stats.cascades.isEmpty()) out.append(" none\n");
        else {
            out.append('\n');
            List<Cascade> cascades = new ArrayList<>(stats.cascades.values());
            cascades.sort((a, b) -> b.count - a.count);
            for (Cascade cascade : cascades) {
                out.append("  ")
                    .append(cascade.count)
                    .append(cascade.count == 1 ? " chunk by " : " chunks by ")
                    .append(cascade.label)
                    .append(", for example ")
                    .append(String.join("; ", cascade.examples))
                    .append('\n');
            }
        }

        engineSummary(out, stats);

        // slowest chunks
        out.append("\nSlowest chunks (over ")
            .append(LogsModule.chunkSlowMs)
            .append(" ms):");
        if (stats.slowest.isEmpty()) out.append(" none\n");
        else {
            out.append('\n');
            for (Slow slow : stats.slowest) out.append("  ")
                .append(slow.text)
                .append('\n');
        }
        out.append('\n');
        return out.toString();
    }

    /** The work of Minecraft itself inside the generation, what is worth making faster in Minecraft. */
    private static void engineSummary(StringBuilder out, Stats stats) {
        String[] names = ChunkGenEngine.names();
        long engine = 0;
        for (long nanos : stats.engineNanos) engine += nanos;
        if (engine > 0) {
            out.append(
                "\nInside Minecraft (own times: a line does not count the lines that ran inside it, so they add up):\n");
            out.append(
                String.format(Locale.ROOT, "  %10s %6s %9s %10s  %s\n", "time", "share", "calls", "average", "work"));
            List<Integer> kinds = new ArrayList<>();
            for (int i = 0; i < stats.engineNanos.length; i++) if (stats.engineCalls[i] > 0) kinds.add(i);
            kinds.sort((a, b) -> Long.compare(stats.engineNanos[b], stats.engineNanos[a]));
            for (int i = 0; i < Math.min(40, kinds.size()); i++) {
                int kind = kinds.get(i);
                out.append(
                    String.format(
                        Locale.ROOT,
                        "  %10s %6s %9s %10s  %s\n",
                        ms(stats.engineNanos[kind]),
                        share(stats.engineNanos[kind], stats.nanos),
                        count(stats.engineCalls[kind]),
                        micros(stats.engineNanos[kind] / Math.max(1, stats.engineCalls[kind])),
                        kind < names.length ? names[kind] : "?"));
            }
            if (kinds.size() > 40) out.append("  ... and ")
                .append(kinds.size() - 40)
                .append(" more\n");
            out.append(
                String.format(
                    Locale.ROOT,
                    "  %10s %6s %9s %10s  %s\n",
                    ms(Math.max(0, stats.nanos - engine)),
                    share(Math.max(0, stats.nanos - engine), stats.nanos),
                    "",
                    "",
                    "the own code of the generators, outside all of these"));

            // where it happens
            List<Stat> steps = new ArrayList<>(stats.steps.values());
            steps.sort((a, b) -> Long.compare(b.nanos, a.nanos));
            out.append("\nInside Minecraft, by step (the costliest steps):\n");
            for (int i = 0; i < Math.min(12, steps.size()); i++) {
                Stat stat = steps.get(i);
                long inside = 0;
                List<Integer> stepKinds = new ArrayList<>();
                for (int k = 0; k < stat.engineNanos.length; k++) {
                    inside += stat.engineNanos[k];
                    if (stat.engineNanos[k] > 0) stepKinds.add(k);
                }
                stepKinds.sort((a, b) -> Long.compare(stat.engineNanos[b], stat.engineNanos[a]));
                out.append("  ")
                    .append(stat.stage)
                    .append(' ')
                    .append(stat.label)
                    .append("  ·  ")
                    .append(stat.where)
                    .append(": ")
                    .append(ms(stat.nanos))
                    .append('\n');
                for (int k = 0; k < Math.min(6, stepKinds.size()); k++) {
                    int kind = stepKinds.get(k);
                    out.append(
                        String.format(
                            Locale.ROOT,
                            "      %10s %6s %9s calls  %s\n",
                            ms(stat.engineNanos[kind]),
                            share(stat.engineNanos[kind], stat.nanos),
                            count(stat.engineCalls[kind]),
                            kind < names.length ? names[kind] : "?"));
                }
                out.append(
                    String.format(
                        Locale.ROOT,
                        "      %10s %6s %9s        %s\n",
                        ms(Math.max(0, stat.nanos - inside)),
                        share(Math.max(0, stat.nanos - inside), stat.nanos),
                        "",
                        "its own code"));
            }
        }
        if (stats.saves > 0) {
            out.append("\nChunks saved on the server thread (after generation too, written to NBT there): ")
                .append(count(stats.saves))
                .append(", ")
                .append(ms(stats.saveNanos))
                .append(", ")
                .append(micros(stats.saveNanos / stats.saves))
                .append(" each\n");
        }
    }

    static String micros(long nanos) {
        if (nanos >= 1_000_000) return ms(nanos);
        return String.format(Locale.ROOT, "%.1f µs", nanos / 1e3);
    }

    private static String count(long count) {
        if (count >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", count / 1e6);
        if (count >= 10_000) return String.format(Locale.ROOT, "%.1fK", count / 1e3);
        return Long.toString(count);
    }

    // ---- names

    /** The class of a generator with its mod, as "GT_Worldgenerator (GregTech)". */
    private static String label(Object source) {
        if (source == null) return "unknown";
        Class<?> type = source.getClass();
        synchronized (LABELS) {
            String label = LABELS.get(type);
            if (label != null) return label;
        }
        String name = type.getName();
        String simple = name.substring(name.lastIndexOf('.') + 1);
        String label = simple + " (" + owner(type) + ")";
        synchronized (LABELS) {
            LABELS.put(type, label);
        }
        return label;
    }

    /** The name of the mod whose jar the class comes from, or "Minecraft"; the jar or "unknown mod" if none. */
    private static String owner(Class<?> type) {
        String className = type.getName();
        if (className.startsWith("net.minecraft.") || className.startsWith("net.minecraftforge.")) return "Minecraft";
        File source = sourceOf(type);
        if (source != null) {
            try {
                for (ModContainer mod : Loader.instance()
                    .getModList()) {
                    // the same file, or one of the same name where paths differ, as through a link
                    if (mod.getSource() != null && (sameFile(mod.getSource(), source) || mod.getSource()
                        .getName()
                        .equals(source.getName()))) return mod.getName();
                }
            } catch (RuntimeException ignored) {
                // the mod list is not there yet
            }
            return source.getName();
        }
        String jar = StackTraces.jarOf(className);
        return jar != null ? jar : "unknown mod";
    }

    /** The jar or folder the class was loaded from, in the mods folder or, in a development run, anywhere. */
    private static File sourceOf(Class<?> type) {
        try {
            if (type.getProtectionDomain() != null && type.getProtectionDomain()
                .getCodeSource() != null
                && type.getProtectionDomain()
                    .getCodeSource()
                    .getLocation() != null) {
                return new File(
                    type.getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI());
            }
            String name = type.getName();
            int dollar = name.indexOf('$');
            String resource = (dollar > 0 ? name.substring(0, dollar) : name).replace('.', '/') + ".class";
            ClassLoader loader = type.getClassLoader() != null ? type.getClassLoader()
                : ClassLoader.getSystemClassLoader();
            URL url = loader.getResource(resource);
            if (url == null) return null;
            String path = url.toString();
            if (path.startsWith("jar:")) {
                return new File(new URI(path.substring(4, path.indexOf("!/"))));
            }
            if (path.startsWith("file:")) {
                // a folder of classes: its root
                File file = new File(url.toURI());
                for (int i = resource.split("/").length; i > 0 && file != null; i--) file = file.getParentFile();
                return file;
            }
        } catch (Exception ignored) {
            // no source to tell
        }
        return null;
    }

    private static boolean sameFile(File a, File b) {
        try {
            return a.getCanonicalFile()
                .equals(b.getCanonicalFile());
        } catch (IOException e) {
            return a.getAbsoluteFile()
                .equals(b.getAbsoluteFile());
        }
    }

    private static final Map<Integer, String> DIMENSIONS = new HashMap<>();

    /** The name of the dimension with its id, as "Overworld (0)". */
    private static String where(World world) {
        if (world == null || world.provider == null) return "unknown dimension";
        int id = world.provider.dimensionId;
        synchronized (DIMENSIONS) {
            String name = DIMENSIONS.get(id);
            if (name != null) return name;
        }
        String name;
        try {
            name = world.provider.getDimensionName() + " (" + id + ")";
        } catch (RuntimeException e) {
            name = "dimension " + id;
        }
        synchronized (DIMENSIONS) {
            DIMENSIONS.put(id, name);
        }
        return name;
    }

    /** The biome in the middle of a chunk, for the lines of slow chunks. */
    static String biome(World world, int x, int z) {
        try {
            BiomeGenBase biome = world.getBiomeGenForCoords(x * 16 + 8, z * 16 + 8);
            return biome == null ? "" : ", " + biome.biomeName;
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String chunkText(int x, int z) {
        return "chunk (" + x + ", " + z + ") at x " + x * 16 + " z " + z * 16;
    }

    static String ms(long nanos) {
        double ms = nanos / 1e6;
        if (ms >= 10_000) return String.format(Locale.ROOT, "%.1f s", ms / 1000);
        if (ms >= 100) return String.format(Locale.ROOT, "%.0f ms", ms);
        return String.format(Locale.ROOT, "%.1f ms", ms);
    }

    private static String share(long part, long whole) {
        return whole <= 0 ? "" : String.format(Locale.ROOT, "%.0f%%", part * 100.0 / whole);
    }

    private static String minutes(long millis) {
        long minutes = Math.round(millis / 60_000.0);
        return minutes <= 1 ? "minute" : minutes + " minutes";
    }
}
