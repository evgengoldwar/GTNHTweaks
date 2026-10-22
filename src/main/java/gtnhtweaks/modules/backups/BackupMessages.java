package gtnhtweaks.modules.backups;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import gtnhtweaks.GTNHTweaks;

/** Tells what a log line or a chat message says about a backup. Shared by the server and the client side. */
public final class BackupMessages {

    public enum Kind {
        NONE,
        STARTED,
        PROGRESS,
        FINISHED,
        FAILED,
        /** About a backup, but says nothing about its state. */
        OTHER
    }

    static final String DEFAULT_LOGGERS = "(?iu).*server.?utilit.*";
    static final String DEFAULT_KEYWORDS = "(?iu).*(backup|backing up|compressing|бэкап|бекап|резервн).*";
    static final String DEFAULT_IGNORED = "(?iu).*(\\bnext\\b|\\bwill\\b|\\bin \\d|\\bevery\\b|следующ|через|будет).*";
    static final String DEFAULT_FAILED = "(?iu).*(fail|error|exception|could ?n[o']t|ошибк|не удал|не получ).*";
    static final String DEFAULT_FINISHED = "(?iu).*(\\bdone\\b|finished|complete|\\bcreated\\b|\\bsaved\\b|заверш|готов|создан|сохран).*";
    static final String DEFAULT_STARTED = "(?iu).*(start|begin|backing up|launch|compressing|начина|начат|запуск|создаётся|создается).*";

    private static final Pattern PERCENT = Pattern.compile("(\\d{1,3}(?:[.,]\\d+)?)\\s*%");
    /** Size of the archive, like {@code 2.0GB} in "Backup done in 00:41 seconds (2.0GB)!". */
    private static final Pattern SIZE = Pattern.compile("(?i)(\\d+(?:[.,]\\d+)?)\\s*([KMGT]i?B)\\b");

    static volatile Pattern loggers = Pattern.compile(DEFAULT_LOGGERS);
    static volatile Pattern keywords = Pattern.compile(DEFAULT_KEYWORDS);
    static volatile Pattern ignored = Pattern.compile(DEFAULT_IGNORED);
    static volatile Pattern failed = Pattern.compile(DEFAULT_FAILED);
    static volatile Pattern finished = Pattern.compile(DEFAULT_FINISHED);
    static volatile Pattern started = Pattern.compile(DEFAULT_STARTED);

    private BackupMessages() {}

    static Pattern compile(String regex, String fallback) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            GTNHTweaks.LOG.warn("Invalid pattern in backups.cfg: {}", regex);
            return Pattern.compile(fallback);
        }
    }

    public static boolean isBackupLogger(String logger) {
        return logger != null && loggers.matcher(logger)
            .matches();
    }

    /**
     * @param text    message without color codes
     * @param fromMod the line comes from the ServerUtilities logger, so a bare percentage is backup progress
     * @param running a backup is running, so a bare "Done!" ends it
     */
    public static Kind classify(String text, boolean fromMod, boolean running) {
        if (text == null || text.isEmpty()) return Kind.NONE;
        if (!keywords.matcher(text)
            .matches()) {
            if (fromMod && progress(text) >= 0) return Kind.PROGRESS;
            if (running && !fromMod
                && finished.matcher(text)
                    .matches())
                return Kind.FINISHED;
            return Kind.NONE;
        }
        if (failed.matcher(text)
            .matches()) return Kind.FAILED;
        if (finished.matcher(text)
            .matches()) return Kind.FINISHED;
        if (progress(text) >= 0) return Kind.PROGRESS;
        if (ignored.matcher(text)
            .matches()) return Kind.OTHER;
        if (started.matcher(text)
            .matches()) return Kind.STARTED;
        return Kind.OTHER;
    }

    /** Kind of a ServerUtilities translation key like {@code serverutilities.lang.backup_start}, NONE if unknown. */
    public static Kind classifyKey(String key) {
        if (key == null) return Kind.NONE;
        String lower = key.toLowerCase();
        if (!lower.contains("backup")) return Kind.NONE;
        if (lower.contains("fail") || lower.contains("error")) return Kind.FAILED;
        if (lower.contains("end") || lower.contains("done") || lower.contains("finish") || lower.contains("complete"))
            return Kind.FINISHED;
        if (lower.contains("start") || lower.contains("begin") || lower.contains("launch") || lower.contains("manual"))
            return Kind.STARTED;
        if (lower.contains("progress")) return Kind.PROGRESS;
        return Kind.OTHER;
    }

    /** Size of the backup named in the text, like "2.0 GB", or an empty string. */
    public static String size(String text) {
        Matcher matcher = SIZE.matcher(text);
        if (!matcher.find()) return "";
        return matcher.group(1) + " "
            + matcher.group(2)
                .toUpperCase()
                .replace("IB", "iB");
    }

    /** The percentage in the text from 0 to 1, or -1 if there is none. */
    public static float progress(String text) {
        Matcher matcher = PERCENT.matcher(text);
        if (!matcher.find()) return -1;
        try {
            float percent = Float.parseFloat(
                matcher.group(1)
                    .replace(',', '.'));
            return percent > 100 ? -1 : percent / 100F;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
