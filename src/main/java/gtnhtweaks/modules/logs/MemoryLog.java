package gtnhtweaks.modules.logs;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import java.lang.management.ThreadMXBean;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

/**
 * Memory for the Chunk generation log: the Java and the collector that run, every long pause of the garbage collector
 * with its cause and the heap before and after, the pauses apart from the background cycles of the collector, and the
 * memory every thread allocates, the server thread by step and by section. A game that allocates much makes the
 * collector pause, and a pause stops the server and the game at once.
 */
final class MemoryLog {

    private static final Object LOCK = new Object();
    /** A pause at least this long gets a line of its own. */
    private static final long LINE_PAUSE_MS = 50;
    /** The threads are looked at every this many milliseconds. */
    private static final long THREAD_SAMPLE_MS = 5_000;

    /** The notification of a collection, of com.sun.management, read here as plain data. */
    private static final String GC_NOTIFICATION = "com.sun.management.gc.notification";

    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
    /** getCurrentThreadAllocatedBytes, fast, from Java 14; null before, where the slower call by id is used. */
    private static final MethodHandle CURRENT_ALLOCATED;
    /** getThreadAllocatedBytes(long) and getThreadAllocatedBytes(long[]) of com.sun.management.ThreadMXBean. */
    private static final MethodHandle BY_ID, BY_IDS;
    static final boolean ALLOCATION;
    /** True if the allocation of the current thread is cheap enough for every section of the profiler. */
    static final boolean FAST_ALLOCATION;

    static {
        MethodHandle current = null, byId = null, byIds = null;
        boolean supported = false;
        try {
            // com.sun.management is not part of the Java the mod is built for, so it is looked up when it runs
            Class<?> type = Class.forName("com.sun.management.ThreadMXBean");
            if (type.isInstance(THREADS)) {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                supported = (boolean) lookup
                    .findVirtual(type, "isThreadAllocatedMemorySupported", MethodType.methodType(boolean.class))
                    .invoke(THREADS);
                if (supported) {
                    lookup.findVirtual(type, "setThreadAllocatedMemoryEnabled", MethodType.methodType(void.class, boolean.class))
                        .invoke(THREADS, true);
                    byId = lookup.findVirtual(type, "getThreadAllocatedBytes", MethodType.methodType(long.class, long.class))
                        .bindTo(THREADS);
                    byIds = lookup
                        .findVirtual(type, "getThreadAllocatedBytes", MethodType.methodType(long[].class, long[].class))
                        .bindTo(THREADS);
                    try {
                        current = lookup
                            .findVirtual(type, "getCurrentThreadAllocatedBytes", MethodType.methodType(long.class))
                            .bindTo(THREADS);
                    } catch (ReflectiveOperationException ignored) {
                        // before Java 14
                    }
                }
            }
        } catch (Throwable ignored) {
            // not a HotSpot-like Java: no allocation counts
            supported = false;
        }
        CURRENT_ALLOCATED = current;
        BY_ID = byId;
        BY_IDS = byIds;
        ALLOCATION = supported && byId != null && byIds != null;
        FAST_ALLOCATION = ALLOCATION && current != null;
    }

    /** One collector: its pauses or its background cycles, over the period [0] and the session [1]. */
    private static final class Collector {

        final String name;
        final boolean pauses;
        final long[] count = new long[2], millis = new long[2], longest = new long[2];

        Collector(String name, boolean pauses) {
            this.name = name;
            this.pauses = pauses;
        }
    }

    private static final Map<String, Collector> COLLECTORS = new LinkedHashMap<>();
    private static final long[] FREED = new long[2];
    /** All the pause time so far, to tell the pauses inside a tick. */
    private static volatile long pauseNanos;

    private static final Map<Long, Long> THREAD_BYTES = new HashMap<>();
    private static final Map<String, long[]> THREAD_GROUPS = new LinkedHashMap<>();
    private static long lastThreadSample;
    private static boolean started;

    private MemoryLog() {}

    static void start() {
        synchronized (LOCK) {
            if (started) return;
            started = true;
        }
        try {
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                if (!(bean instanceof NotificationEmitter)) continue;
                ((NotificationEmitter) bean).addNotificationListener(MemoryLog::onCollection, null, null);
            }
        } catch (Throwable t) {
            // no notifications on this Java: the totals of the beans stay in the summary
        }
    }

    // ---- allocation

    /** The bytes the current thread allocated so far, -1 when not known. */
    static long allocated() {
        if (!ALLOCATION) return -1;
        try {
            if (CURRENT_ALLOCATED != null) return (long) CURRENT_ALLOCATED.invokeExact();
            return (long) BY_ID.invokeExact(
                Thread.currentThread()
                    .getId());
        } catch (Throwable t) {
            return -1;
        }
    }

    static long pauseNanos() {
        return pauseNanos;
    }

    // ---- collections

    private static void onCollection(Notification notification, Object handback) {
        if (!GC_NOTIFICATION.equals(notification.getType())) return;
        try {
            CompositeData data = (CompositeData) notification.getUserData();
            String name = String.valueOf(data.get("gcName"));
            String action = String.valueOf(data.get("gcAction"));
            String cause = String.valueOf(data.get("gcCause"));
            CompositeData gc = (CompositeData) data.get("gcInfo");
            // the cycles of G1, ZGC and Shenandoah that run beside the game are not pauses
            boolean pause = !(name.contains("Cycles") || name.contains("Concurrent")
                || action.toLowerCase(Locale.ROOT)
                    .contains("concurrent"));
            long millis = ((Number) gc.get("duration")).longValue();
            long before = used((TabularData) gc.get("memoryUsageBeforeGc"));
            long after = used((TabularData) gc.get("memoryUsageAfterGc"));
            synchronized (LOCK) {
                Collector collector = COLLECTORS.computeIfAbsent(name, n -> new Collector(n, pause));
                for (int i = 0; i < 2; i++) {
                    collector.count[i]++;
                    collector.millis[i] += millis;
                    collector.longest[i] = Math.max(collector.longest[i], millis);
                    FREED[i] += Math.max(0, before - after);
                }
            }
            if (pause) {
                pauseNanos += millis * 1_000_000L;
                if (millis >= LINE_PAUSE_MS) ChunkGenLog.externalLine(
                    String.format(
                        Locale.ROOT,
                        "GC PAUSE  %d ms  %s (%s), heap %s -> %s",
                        millis,
                        name,
                        cause,
                        megabytes(before),
                        megabytes(after)));
            }
        } catch (Throwable ignored) {
            // a notification of another shape
        }
    }

    /** The used memory of all the pools of a table of MemoryUsage by pool name. */
    private static long used(TabularData pools) {
        long used = 0;
        if (pools == null) return 0;
        for (Object row : pools.values()) {
            Object value = ((CompositeData) row).get("value");
            if (value instanceof CompositeData) used += MemoryUsage.from((CompositeData) value)
                .getUsed();
        }
        return used;
    }

    // ---- threads

    /** Every few seconds, from the writer of the log: the memory every thread allocated meanwhile, by its name. */
    static void sampleThreads() {
        if (!ALLOCATION) return;
        long now = System.currentTimeMillis();
        if (now - lastThreadSample < THREAD_SAMPLE_MS) return;
        lastThreadSample = now;
        try {
            long[] ids = THREADS.getAllThreadIds();
            long[] bytes = (long[]) BY_IDS.invokeExact(ids);
            ThreadInfo[] infos = THREADS.getThreadInfo(ids);
            synchronized (LOCK) {
                Map<Long, Long> alive = new HashMap<>();
                for (int i = 0; i < ids.length; i++) {
                    if (infos[i] == null || bytes[i] < 0) continue;
                    alive.put(ids[i], bytes[i]);
                    Long last = THREAD_BYTES.get(ids[i]);
                    long delta = last == null ? 0 : bytes[i] - last;
                    if (delta <= 0) continue;
                    // threads of one pool are one group: "Chunk Batcher 3" is "Chunk Batcher #"
                    String group = infos[i].getThreadName()
                        .replaceAll("\\d+", "#");
                    long[] sum = THREAD_GROUPS.computeIfAbsent(group, g -> new long[2]);
                    sum[0] += delta;
                    sum[1] += delta;
                }
                THREAD_BYTES.clear();
                THREAD_BYTES.putAll(alive);
            }
        } catch (Throwable ignored) {
            // the next sample
        }
    }

    // ---- writing

    /** The Java, the collector and the memory, for the head of the log. */
    static String jvm() {
        StringBuilder out = new StringBuilder();
        out.append("Java ")
            .append(System.getProperty("java.version"))
            .append(" (")
            .append(System.getProperty("java.vm.name"))
            .append(", ")
            .append(System.getProperty("java.vendor"))
            .append("), ")
            .append(
                Runtime.getRuntime()
                    .availableProcessors())
            .append(" processor threads\n");
        List<String> collectors = new ArrayList<>();
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans())
            collectors.add(bean.getName());
        out.append("Garbage collector: ")
            .append(String.join(", ", collectors))
            .append('\n');
        MemoryUsage heap = ManagementFactory.getMemoryMXBean()
            .getHeapMemoryUsage();
        out.append("Heap: at most ")
            .append(megabytes(heap.getMax()))
            .append(", at start ")
            .append(megabytes(heap.getInit()))
            .append('\n');
        List<String> arguments = new ArrayList<>();
        try {
            for (String argument : ManagementFactory.getRuntimeMXBean()
                .getInputArguments()) {
                if (argument.startsWith("-Xm") || argument.startsWith("-Xs")
                    || argument.startsWith("-XX:")
                    || argument.startsWith("-Xlog")) arguments.add(argument);
            }
        } catch (RuntimeException ignored) {
            // not allowed to look
        }
        out.append("JVM memory and GC arguments: ")
            .append(arguments.isEmpty() ? "none, the defaults of this Java" : String.join(" ", arguments))
            .append('\n');
        if (!ALLOCATION) out.append("The allocation of the threads is not measured on this Java\n");
        return out.toString();
    }

    static void summary(StringBuilder out, int which, long periodMillis) {
        synchronized (LOCK) {
            MemoryUsage heap = ManagementFactory.getMemoryMXBean()
                .getHeapMemoryUsage();
            out.append("\nMemory and garbage collection:\n");
            out.append("  heap now ")
                .append(megabytes(heap.getUsed()))
                .append(" used of ")
                .append(megabytes(heap.getMax() > 0 ? heap.getMax() : heap.getCommitted()));
            if (periodMillis > 0) out.append(
                String.format(
                    Locale.ROOT,
                    "; the collector freed %s, %.0f MB a second",
                    megabytes(FREED[which]),
                    FREED[which] / 1048576.0 / (periodMillis / 1000.0)));
            out.append('\n');
            long pauses = 0;
            for (Collector collector : COLLECTORS.values()) {
                if (collector.count[which] == 0) continue;
                if (collector.pauses) pauses += collector.millis[which];
                out.append(
                    String.format(
                        Locale.ROOT,
                        "  %-28s %s %d, %s in all, the longest %d ms, %s on average\n",
                        collector.name,
                        collector.pauses ? "pauses:" : "background cycles (beside the game, not pauses):",
                        collector.count[which],
                        ChunkGenLog.ms(collector.millis[which] * 1_000_000L),
                        collector.longest[which],
                        ChunkGenLog.ms(collector.millis[which] * 1_000_000L / collector.count[which])));
            }
            if (periodMillis > 0) out.append(
                String.format(
                    Locale.ROOT,
                    "  the game stood in pauses %s, %.1f%% of the time\n",
                    ChunkGenLog.ms(pauses * 1_000_000L),
                    pauses * 100.0 / periodMillis));

            List<Map.Entry<String, long[]>> groups = new ArrayList<>(THREAD_GROUPS.entrySet());
            groups.sort((a, b) -> Long.compare(b.getValue()[which], a.getValue()[which]));
            if (!groups.isEmpty() && periodMillis > 0) {
                out.append("  memory allocated by the threads (what makes the collector work):\n");
                for (int i = 0; i < Math.min(12, groups.size()); i++) {
                    long bytes = groups.get(i)
                        .getValue()[which];
                    if (bytes <= 0) break;
                    out.append(
                        String.format(
                            Locale.ROOT,
                            "    %8.0f MB/s  %s\n",
                            bytes / 1048576.0 / (periodMillis / 1000.0),
                            groups.get(i)
                                .getKey()));
                }
            }
            if (which == 0) {
                for (Collector collector : COLLECTORS.values()) {
                    collector.count[0] = collector.millis[0] = collector.longest[0] = 0;
                }
                FREED[0] = 0;
                for (long[] sum : THREAD_GROUPS.values()) sum[0] = 0;
            }
        }
    }

    static void resetSession() {
        synchronized (LOCK) {
            for (Collector collector : COLLECTORS.values()) {
                collector.count[1] = collector.millis[1] = collector.longest[1] = 0;
            }
            FREED[1] = 0;
            for (long[] sum : THREAD_GROUPS.values()) sum[1] = 0;
        }
    }

    static String megabytes(long bytes) {
        if (bytes >= 10L << 30) return String.format(Locale.ROOT, "%.1f GB", bytes / 1073741824.0);
        return String.format(Locale.ROOT, "%d MB", bytes >> 20);
    }
}
