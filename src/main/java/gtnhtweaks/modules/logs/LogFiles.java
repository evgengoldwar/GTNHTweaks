package gtnhtweaks.modules.logs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import net.minecraft.launchwrapper.Launch;

import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.core.ModuleManager;

/**
 * Files of the current session in {@code GTNHTweaksLogs}: the full log, errors, warnings and crashes. Every message of
 * the game passes through {@link #onRecord}, the file of errors and warnings is rewritten in the background.
 */
final class LogFiles {

    static final String KIND_ALL = "All";
    static final String KIND_ERRORS = "Errors";
    static final String KIND_WARNINGS = "Warnings";
    static final String KIND_CRASH = "Crash";
    static final String KIND_ALL_CRASH = "All crash";
    static final String KIND_LOADING = "Loading";
    static final String KIND_MODS = "Mods";
    private static final String[] KINDS = { KIND_ALL, KIND_ERRORS, KIND_WARNINGS, KIND_CRASH, KIND_ALL_CRASH,
        KIND_LOADING, KIND_MODS };

    private static final Pattern SESSION_FILE = Pattern
        .compile("^(All crash|All|Errors|Warnings|Crash|Loading|Mods) (.+)\\.log$");
    private static final Pattern BACKUP_FILE = Pattern.compile("^Backup .+\\.zip$");
    /** A line of the FML log: {@code [12:00:00] [Client thread/INFO] [logger/mod]: message}. */
    private static final Pattern LOG_LINE = Pattern.compile(
        "^\\[(\\d{2}:\\d{2}:\\d{2})\\] \\[([^\\]]*)/(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\] \\[([^\\]]*)\\]: ?(.*)$");
    /** The caller prefix FML puts before System.out/System.err lines. */
    private static final Pattern STD_PREFIX = Pattern.compile("^\\[[^\\]\\s]*:-?\\d+\\]: ?");
    /** A level written into an INFO message, as MCPatcher does: {@code [CTM/ERROR]: ...}. */
    private static final Pattern INNER_LEVEL = Pattern.compile("^\\[[^\\]]+/(ERROR|SEVERE|FATAL|WARN|WARNING)\\]");

    /** Something to read: banner lines such as {@code +=+=+=} or {@code =====} have nothing. */
    private static final Pattern READABLE = Pattern.compile("[\\p{L}\\p{N}]");

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter SESSION_TEXT = DateTimeFormatter
        .ofPattern("d MMMM yyyy HH:mm:ss", Locale.ENGLISH);

    private static final long ISSUE_WRITE_INTERVAL = 2000;
    private static final long STDERR_BLOCK_TIMEOUT = 1500;

    private static final Object LOCK = new Object();
    /** Set while this class works, so messages logged meanwhile only go to the full log. */
    private static final ThreadLocal<Boolean> BUSY = new ThreadLocal<>();

    private static File dir;
    private static String session;
    private static String sessionText;
    private static LocalDate sessionDate;
    private static Writer allWriter;
    private static final IssueLog ERRORS = new IssueLog(
        "GTNHTweaks - ERRORS AND EXCEPTIONS",
        "Errors, exceptions and System.err output of the game. Everything else is in the All log.");
    private static final IssueLog WARNINGS = new IssueLog(
        "GTNHTweaks - WARNINGS",
        "Warnings of the game. Everything else is in the All log.");
    private static LoadingReport loading;
    private static long lastIssueWrite;
    private static final Map<String, StderrBlock> STDERR = new HashMap<>();
    private static final Set<Object> SAVED_CRASHES = Collections.newSetFromMap(new WeakHashMap<>());
    private static int crashCount;
    private static boolean started;
    private static boolean closed;

    private LogFiles() {}

    /** The log folder, also before the logs are started. */
    static File folder() {
        if (dir != null) return dir;
        File home = Launch.minecraftHome != null ? Launch.minecraftHome : new File(".");
        return new File(home, LogsModule.folder);
    }

    static String time(long millis) {
        return TIME.format(
            Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault()));
    }

    /** Opens the files of the session, copies what was logged so far and starts capturing the log. */
    static void start() {
        synchronized (LOCK) {
            if (started) return;
            started = true;
        }
        try {
            long jvmStart = ManagementFactory.getRuntimeMXBean()
                .getStartTime();
            File home = Launch.minecraftHome != null ? Launch.minecraftHome : new File(".");
            dir = new File(home, LogsModule.folder);
            if (!dir.isDirectory() && !dir.mkdirs()) {
                GTNHTweaks.LOG.warn("Cannot create the log folder {}", dir);
                return;
            }
            ZonedDateTime start = Instant.ofEpochMilli(jvmStart)
                .atZone(ZoneId.systemDefault());
            sessionDate = start.toLocalDate();
            sessionText = SESSION_TEXT.format(start);
            // Logs of earlier launches are packed into backups in the background, the new files have other names
            Map<String, List<File>> earlier = earlierSessions();
            session = uniqueSessionName(start);
            if (!earlier.isEmpty()) {
                Thread backup = new Thread(() -> backUp(earlier), "GTNHTweaks log backup");
                backup.setDaemon(true);
                backup.start();
            }
            loading = new LoadingReport(jvmStart, loadingStatsFile());

            if (LogsModule.writeAll) {
                allWriter = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(file(KIND_ALL)), StandardCharsets.UTF_8),
                    1 << 16);
            }
            // Capture first, then copy the earlier part of the log, so no message falls in between. Other threads
            // wait for the copy to finish, so their messages come after it
            synchronized (LOCK) {
                LogCapture.install();
                copyEarlierLog(home, jvmStart);
            }

            Thread writer = new Thread(LogFiles::writerLoop, "GTNHTweaks log writer");
            writer.setDaemon(true);
            writer.start();
            Runtime.getRuntime()
                .addShutdownHook(new Thread(LogFiles::close, "GTNHTweaks log writer shutdown"));
            GTNHTweaks.LOG.info("Writing logs to {} as \"{}\"", dir, session);
        } catch (Throwable t) {
            GTNHTweaks.LOG.error("Failed to start the GTNHTweaks logs", t);
        }
    }

    private static File file(String kind) {
        return new File(dir, kind + " " + session + ".log");
    }

    private static String uniqueSessionName(ZonedDateTime start) {
        String time = DateTimeFormatter.ofPattern("d MMMM HH:mm", Locale.ENGLISH)
            .format(start);
        // A colon is not allowed in Windows file names
        if (File.separatorChar == '\\') time = time.replace(':', '-');
        String name = time;
        for (int i = 2; sessionExists(name); i++) {
            name = time + " (" + i + ")";
        }
        return name;
    }

    private static boolean sessionExists(String name) {
        for (String kind : KINDS) {
            if (new File(dir, kind + " " + name + ".log").exists()) return true;
        }
        return new File(dir, "Backup " + name + ".zip").exists();
    }

    /** Files of earlier launches still in the folder, by session name. */
    private static Map<String, List<File>> earlierSessions() {
        Map<String, List<File>> sessions = new TreeMap<>();
        File[] files = dir.listFiles();
        if (files == null) return sessions;
        for (File f : files) {
            Matcher m = SESSION_FILE.matcher(f.getName());
            if (f.isFile() && m.matches()) {
                sessions.computeIfAbsent(m.group(2), k -> new ArrayList<>())
                    .add(f);
            }
        }
        return sessions;
    }

    /**
     * Packs the logs of every earlier launch into {@code Backup <launch>.zip} and removes them from the folder, then
     * keeps only the newest backups.
     */
    private static void backUp(Map<String, List<File>> sessions) {
        int maxBackups = LogsModule.maxBackups;
        for (Map.Entry<String, List<File>> entry : sessions.entrySet()) {
            List<File> files = entry.getValue();
            if (maxBackups > 0 && !zip(entry.getKey(), files)) continue;
            for (File f : files) {
                if (!f.delete()) GTNHTweaks.LOG.debug("Cannot delete old log {}", f);
            }
        }

        File[] files = dir.listFiles();
        if (files == null) return;
        List<File> backups = new ArrayList<>();
        for (File f : files) {
            if (f.isFile() && BACKUP_FILE.matcher(f.getName())
                .matches()) backups.add(f);
        }
        backups.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = Math.max(0, maxBackups); i < backups.size(); i++) {
            if (!backups.get(i)
                .delete()) GTNHTweaks.LOG.debug("Cannot delete old backup {}", backups.get(i));
        }
    }

    private static boolean zip(String name, List<File> files) {
        File target = new File(dir, "Backup " + name + ".zip");
        for (int i = 2; target.exists(); i++) {
            target = new File(dir, "Backup " + name + " (" + i + ").zip");
        }
        File temp = new File(target.getPath() + ".tmp");
        long newest = 0;
        byte[] buffer = new byte[1 << 16];
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(temp))) {
            for (File f : files) {
                ZipEntry entry = new ZipEntry(f.getName());
                entry.setTime(f.lastModified());
                zip.putNextEntry(entry);
                try (InputStream in = new FileInputStream(f)) {
                    int read;
                    while ((read = in.read(buffer)) > 0) zip.write(buffer, 0, read);
                }
                zip.closeEntry();
                newest = Math.max(newest, f.lastModified());
            }
        } catch (IOException e) {
            GTNHTweaks.LOG.warn("Cannot back up the logs of {}", name, e);
            if (!temp.delete()) temp.deleteOnExit();
            return false;
        }
        try {
            Files.move(temp.toPath(), target.toPath());
        } catch (IOException e) {
            GTNHTweaks.LOG.warn("Cannot back up the logs of {}", name, e);
            return false;
        }
        // Backups are kept by the time of their launch
        if (newest > 0 && !target.setLastModified(newest)) GTNHTweaks.LOG.debug("Cannot date {}", target);
        return true;
    }

    /**
     * Copies the part of the FML log written before the capture started, so the files also cover the start of the
     * game.
     */
    private static void copyEarlierLog(File home, long jvmStart) {
        File logs = new File(home, "logs");
        File source = null;
        for (String name : new String[] { "fml-client-latest.log", "fml-server-latest.log" }) {
            File f = new File(logs, name);
            // Older files are left from a previous launch
            if (f.isFile() && f.lastModified() >= jvmStart - 5000
                && (source == null || f.lastModified() > source.lastModified())) {
                source = f;
            }
        }
        if (source == null) return;

        BUSY.set(Boolean.TRUE);
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(source), StandardCharsets.UTF_8))) {
            List<String> raw = new ArrayList<>();
            ParsedLine pending = null;
            String line;
            while ((line = reader.readLine()) != null) {
                raw.add(line);
                Matcher m = LOG_LINE.matcher(line);
                if (m.matches()) {
                    if (pending != null) classifyEarlier(pending);
                    pending = new ParsedLine(m);
                } else if (pending != null) {
                    pending.extra.add(line);
                }
                if (raw.size() >= 1000) {
                    writeRawLines(raw);
                    raw.clear();
                }
            }
            if (pending != null) classifyEarlier(pending);
            writeRawLines(raw);
        } catch (IOException e) {
            GTNHTweaks.LOG.warn("Cannot read the earlier log {}", source, e);
        } finally {
            BUSY.remove();
        }
    }

    private static void writeRawLines(List<String> lines) {
        synchronized (LOCK) {
            if (allWriter == null || closed) return;
            try {
                for (String line : lines) {
                    allWriter.write(line);
                    allWriter.write('\n');
                }
            } catch (IOException e) {
                allWriterFailed(e);
            }
        }
    }

    private static void classifyEarlier(ParsedLine line) {
        synchronized (LOCK) {
            classify(line.toRecord());
        }
    }

    /** A line of the FML log with the lines that continue it. */
    private static final class ParsedLine {

        final String time;
        final String thread;
        final String level;
        final String logger;
        final String mod;
        final String message;
        final List<String> extra = new ArrayList<>();

        ParsedLine(Matcher m) {
            time = m.group(1);
            thread = m.group(2);
            level = m.group(3);
            String source = m.group(4);
            int slash = source.lastIndexOf('/');
            logger = slash >= 0 ? source.substring(0, slash) : source;
            mod = slash >= 0 ? source.substring(slash + 1) : "";
            message = m.group(5);
        }

        LogRecord toRecord() {
            long millis;
            try {
                millis = LocalTime.parse(time)
                    .atDate(sessionDate)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
            } catch (RuntimeException e) {
                millis = System.currentTimeMillis();
            }
            boolean hasTrace = false;
            for (String line : extra) {
                if (StackTraces.isFrameLine(line)) hasTrace = true;
            }
            if (!hasTrace) {
                // A message of several lines, not an exception
                String text = extra.isEmpty() ? message : message + "\n" + String.join("\n", extra);
                return new LogRecord(millis, level, thread, logger, mod, text, null);
            }
            return new LogRecord(millis, level, thread, logger, mod, message, new ArrayList<>(extra));
        }
    }

    /** Called by the appender for every message of the game. */
    static void onRecord(LogRecord record, String allText) {
        synchronized (LOCK) {
            if (closed) return;
            if (allWriter != null && allText != null) {
                try {
                    allWriter.write(allText);
                } catch (IOException e) {
                    allWriterFailed(e);
                }
            }
            if (BUSY.get() == null) classify(record);
        }
    }

    private static void allWriterFailed(IOException e) {
        try {
            allWriter.close();
        } catch (IOException ignored) {}
        allWriter = null;
        System.err.println("GTNHTweaks: cannot write the All log, it is stopped: " + e);
    }

    /** Puts a message into the errors or warnings. Holds {@link #LOCK}. */
    private static void classify(LogRecord record) {
        if (loading != null && LogsModule.writeLoading && loading.add(record)) loading.save(loadingStatsFile());
        if ("STDERR".equals(record.logger)) {
            if (LogsModule.stderrAsErrors) addStderrLine(record);
            return;
        }
        // Anything else printed by the thread ends its System.err output
        StderrBlock block = STDERR.remove(record.thread);
        if (block != null) addIssue(ERRORS, block.toRecord());

        switch (record.level) {
            case "ERROR":
            case "FATAL":
                rememberError(record);
                addIssue(ERRORS, record);
                break;
            case "WARN":
                addIssue(WARNINGS, record);
                break;
            case "INFO":
                if (record.trace != null) {
                    addIssue(ERRORS, record);
                    break;
                }
                Matcher m = INNER_LEVEL.matcher(record.message);
                if (m.find()) addIssue(
                    m.group(1)
                        .startsWith("WARN") ? WARNINGS : ERRORS,
                    record);
                break;
            default:
                // DEBUG and TRACE, exceptions included, are only for the All log
                break;
        }
    }

    private static final int RECENT_ERRORS = 20;
    /** The last errors of the log, for the crash screen. Guarded by {@link #LOCK}. */
    private static final Deque<String> RECENT = new ArrayDeque<>();

    private static void rememberError(LogRecord record) {
        StringBuilder line = new StringBuilder();
        line.append('[')
            .append(time(record.time))
            .append("] ")
            .append(record.source())
            .append(": ")
            .append(record.message);
        if (record.trace != null && !record.trace.isEmpty()) {
            line.append("\n    ")
                .append(
                    record.trace.get(0)
                        .trim());
        }
        if (RECENT.size() >= RECENT_ERRORS) RECENT.removeFirst();
        RECENT.addLast(line.toString());
    }

    /** The last errors of the log, oldest first. */
    static List<String> recentErrors() {
        synchronized (LOCK) {
            return new ArrayList<>(RECENT);
        }
    }

    private static void addIssue(IssueLog log, LogRecord record) {
        if (log == ERRORS && !LogsModule.writeErrors) return;
        if (log == WARNINGS && !LogsModule.writeWarnings) return;
        if (LogsModule.isIgnored(record)) return;
        if (record.trace == null && !READABLE.matcher(record.message)
            .find()) return;
        log.add(record);
    }

    /** System.err prints a stack trace line by line: the lines of one thread are joined back. */
    private static void addStderrLine(LogRecord record) {
        String text = STD_PREFIX.matcher(record.message)
            .replaceFirst("");
        StderrBlock block = STDERR.get(record.thread);
        if (block != null && StackTraces.isContinuation(text)) {
            block.lines.add(text);
            block.last = System.currentTimeMillis();
            return;
        }
        if (block != null) addIssue(ERRORS, block.toRecord());
        if (text.trim()
            .isEmpty()) {
            STDERR.remove(record.thread);
            return;
        }
        STDERR.put(record.thread, new StderrBlock(record, text));
    }

    private static final class StderrBlock {

        final LogRecord first;
        final List<String> lines = new ArrayList<>();
        long last = System.currentTimeMillis();

        StderrBlock(LogRecord first, String text) {
            this.first = first;
            lines.add(text);
        }

        LogRecord toRecord() {
            List<String> trace = lines.size() > 1 ? lines : null;
            return new LogRecord(first.time, "STDERR", first.thread, first.logger, first.mod, lines.get(0), trace);
        }
    }

    private static void writerLoop() {
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
            try {
                flush(false);
            } catch (Throwable t) {
                System.err.println("GTNHTweaks: log writer error: " + t);
            }
        }
    }

    /** Writes everything buffered. {@code now} also writes errors and warnings changed less than 2 s ago. */
    static void flush(boolean now) {
        String errors = null;
        String warnings = null;
        String loadingText = null;
        BUSY.set(Boolean.TRUE);
        try {
            synchronized (LOCK) {
                if (closed) return;
                long time = System.currentTimeMillis();
                for (Iterator<StderrBlock> it = STDERR.values()
                    .iterator(); it.hasNext();) {
                    StderrBlock block = it.next();
                    if (now || time - block.last >= STDERR_BLOCK_TIMEOUT) {
                        it.remove();
                        addIssue(ERRORS, block.toRecord());
                    }
                }
                if (allWriter != null) {
                    try {
                        allWriter.flush();
                    } catch (IOException e) {
                        allWriterFailed(e);
                    }
                }
                if (now || time - lastIssueWrite >= ISSUE_WRITE_INTERVAL) {
                    String updated = time(time);
                    if (ERRORS.isDirty()) errors = ERRORS.render(sessionText, updated);
                    if (WARNINGS.isDirty()) warnings = WARNINGS.render(sessionText, updated);
                    if (loading != null && loading.isDirty()) loadingText = loading.render(sessionText, updated);
                    lastIssueWrite = time;
                }
            }
            if (errors != null) writeWhole(file(KIND_ERRORS), errors);
            if (warnings != null) writeWhole(file(KIND_WARNINGS), warnings);
            if (loadingText != null) writeWhole(file(KIND_LOADING), loadingText);
        } finally {
            BUSY.remove();
        }
    }

    /** Replaces a file, so a reader never sees it half written. */
    private static void writeWhole(File target, String text) {
        File temp = new File(target.getPath() + ".tmp");
        try {
            Files.write(temp.toPath(), text.getBytes(StandardCharsets.UTF_8));
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("GTNHTweaks: cannot write " + target + ": " + e);
        }
    }

    private static File loadingStatsFile() {
        return new File(ModuleManager.getConfigDir(), "logs-loading-stats.properties");
    }

    /** Writes a whole report file of the session, such as the mod list. */
    static void writeReport(String kind, String text) {
        if (!started || dir == null || session == null) return;
        writeWhole(file(kind), text);
    }

    static String sessionText() {
        return sessionText;
    }

    private static void close() {
        try {
            flush(true);
        } catch (Throwable ignored) {}
        synchronized (LOCK) {
            closed = true;
            if (allWriter != null) {
                try {
                    allWriter.close();
                } catch (IOException ignored) {}
                allWriter = null;
            }
        }
    }

    /**
     * Called when Minecraft saves a crash report: the whole report goes to the All crash file, the short summary to
     * the Crash file.
     */
    static void onCrash(Object report, String completeReport, File reportFile) {
        if (!started || dir == null || session == null) return;
        int number;
        synchronized (LOCK) {
            // The same report can be saved more than once
            if (!SAVED_CRASHES.add(report)) return;
            number = ++crashCount;
        }
        BUSY.set(Boolean.TRUE);
        try {
            String header = "CRASH #" + number
                + " at "
                + SESSION_TEXT.format(ZonedDateTime.now())
                + (reportFile != null ? "\nReport file: " + reportFile.getPath() : "")
                + "\n";
            if (LogsModule.writeFullCrash) {
                append(file(KIND_ALL_CRASH), (number > 1 ? "\n\n" : "") + header + "\n" + completeReport + "\n");
            }
            if (LogsModule.writeCrash) {
                String summary = CrashSummary.summarize(completeReport, LogsModule.maxCrashStackFrames);
                append(file(KIND_CRASH), (number > 1 ? "\n\n" : "") + header + summary);
            }
        } catch (Throwable t) {
            System.err.println("GTNHTweaks: cannot write the crash log: " + t);
        } finally {
            BUSY.remove();
        }
        // The game usually exits right after saving the report
        flush(true);
    }

    private static void append(File target, String text) throws IOException {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(target, true), StandardCharsets.UTF_8)) {
            writer.write(text);
        }
    }
}
