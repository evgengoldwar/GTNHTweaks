package gtnhtweaks.modules.loadingscreen;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.Properties;

import gtnhtweaks.GTNHTweaks;

/**
 * Measures how long the game takes to start, from the start of the JVM until {@code Minecraft.startGame} returns, and
 * remembers the time of the previous launch.
 */
public final class LoadingTimer {

    private static final String KEY_LAST = "lastLoadTimeMs";

    private static final long START_MS = jvmStartTime();

    private static File statsFile;
    private static long previousMs = -1;
    private static volatile long finishedMs = -1;

    private LoadingTimer() {}

    private static long jvmStartTime() {
        try {
            return ManagementFactory.getRuntimeMXBean()
                .getStartTime();
        } catch (Throwable t) {
            return System.currentTimeMillis();
        }
    }

    static synchronized void init(File file) {
        statsFile = file;
        if (!file.isFile()) return;
        Properties stats = new Properties();
        try (InputStream in = new FileInputStream(file)) {
            stats.load(in);
            previousMs = Long.parseLong(stats.getProperty(KEY_LAST, "-1"));
        } catch (IOException | NumberFormatException e) {
            GTNHTweaks.LOG.warn("Could not read " + file, e);
        }
    }

    public static long getElapsedMs() {
        long finished = finishedMs;
        return (finished >= 0 ? finished : System.currentTimeMillis()) - START_MS;
    }

    /** Loading time of the previous launch in milliseconds, or -1 if unknown. */
    public static long getPreviousMs() {
        return previousMs;
    }

    public static boolean isFinished() {
        return finishedMs >= 0;
    }

    static synchronized void finish() {
        if (isFinished()) return;
        finishedMs = System.currentTimeMillis();
        long elapsed = getElapsedMs();
        GTNHTweaks.LOG.info(
            "Game loaded in {} (previous launch: {})",
            format(elapsed),
            previousMs >= 0 ? format(previousMs) : "unknown");

        if (statsFile == null) return;
        Properties stats = new Properties();
        stats.setProperty(KEY_LAST, Long.toString(elapsed));
        File dir = statsFile.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) return;
        try (OutputStream out = new FileOutputStream(statsFile)) {
            stats.store(out, "GTNHTweaks loading screen statistics");
        } catch (IOException e) {
            GTNHTweaks.LOG.warn("Could not write " + statsFile, e);
        }
    }

    /** Readable duration: {@code 8.1 s}, {@code 2 min 05 s}, {@code 1 h 05 min}. Units come from the config. */
    public static String formatHuman(long ms) {
        if (ms < 0) ms = 0;
        String h = LoadingScreenModule.unitHours, m = LoadingScreenModule.unitMinutes,
            s = LoadingScreenModule.unitSeconds;
        if (ms < 60_000) return String.format(Locale.ROOT, "%.1f %s", ms / 1000.0, s);
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = totalSeconds / 60 % 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) return String.format(Locale.ROOT, "%d %s %02d %s", hours, h, minutes, m);
        return String.format(Locale.ROOT, "%d %s %02d %s", minutes, m, seconds, s);
    }

    /** Formats a duration as {@code m:ss.t}, or {@code h:mm:ss} for an hour and more. */
    public static String format(long ms) {
        if (ms < 0) ms = 0;
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = totalSeconds / 60 % 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) return String.format("%d:%02d:%02d", hours, minutes, seconds);
        return String.format("%d:%02d.%d", minutes, seconds, ms / 100 % 10);
    }
}
