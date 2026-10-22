package gtnhtweaks.modules.logs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.minecraftforge.common.config.Configuration;

import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.core.TweakModule;

/**
 * Sorts the game log into separate files in {@code GTNHTweaksLogs}: errors, warnings and crashes without the noise, plus
 * the full log and the full crash report.
 */
public final class LogsModule extends TweakModule {

    public static final LogsModule INSTANCE = new LogsModule();

    private static final String CATEGORY_GENERAL = "general";
    private static final String CATEGORY_FILES = "files";
    private static final String CATEGORY_DETAILS = "details";

    static String folder = "GTNHTweaksLogs";
    static int keepSessions = 30;

    static boolean writeAll = true;
    static boolean writeErrors = true;
    static boolean writeWarnings = true;
    static boolean writeCrash = true;
    static boolean writeFullCrash = true;

    static int maxStackFrames = 12;
    static int maxCrashStackFrames = 30;
    static boolean stderrAsErrors = true;
    private static volatile List<Pattern> ignorePatterns = new ArrayList<>();

    private LogsModule() {
        super("logs", "Sorted logs: errors, warnings and crashes in separate files without the noise", false);
    }

    @Override
    protected void loadConfig(Configuration config) {
        config.addCustomCategoryComment(CATEGORY_GENERAL, "Where the logs are written");
        folder = config.getString(
            "folder",
            CATEGORY_GENERAL,
            folder,
            "Folder in the Minecraft directory for the logs. Changes require a game restart");
        keepSessions = config.getInt(
            "keepSessions",
            CATEGORY_GENERAL,
            keepSessions,
            0,
            10000,
            "How many game launches to keep the logs of, older ones are deleted. 0 keeps all");

        config.addCustomCategoryComment(
            CATEGORY_FILES,
            "Which files are written. Every launch gets its own files named like \"Errors 22 October 08:14.log\"");
        writeAll = config.getBoolean(
            "writeAll",
            CATEGORY_FILES,
            writeAll,
            "All: the full log with every message, like fml-client-latest.log. Changes require a game restart");
        writeErrors = config.getBoolean(
            "writeErrors",
            CATEGORY_FILES,
            writeErrors,
            "Errors: errors, exceptions and System.err output, grouped by source");
        writeWarnings = config
            .getBoolean("writeWarnings", CATEGORY_FILES, writeWarnings, "Warnings: warnings, grouped by source");
        writeCrash = config.getBoolean(
            "writeCrash",
            CATEGORY_FILES,
            writeCrash,
            "Crash: what crashed, the exception with a short stack trace and the mods in it");
        writeFullCrash = config
            .getBoolean("writeFullCrash", CATEGORY_FILES, writeFullCrash, "All crash: the whole crash report");

        config.addCustomCategoryComment(CATEGORY_DETAILS, "Content of the Errors, Warnings and Crash files");
        maxStackFrames = config.getInt(
            "maxStackFrames",
            CATEGORY_DETAILS,
            maxStackFrames,
            1,
            1000,
            "Stack frames kept per exception in Errors and Warnings. Java and Forge internals are folded anyway");
        maxCrashStackFrames = config.getInt(
            "maxCrashStackFrames",
            CATEGORY_DETAILS,
            maxCrashStackFrames,
            1,
            1000,
            "Stack frames kept per exception in Crash");
        stderrAsErrors = config.getBoolean(
            "stderrAsErrors",
            CATEGORY_DETAILS,
            stderrAsErrors,
            "Treat text printed to System.err (mostly printStackTrace) as errors");
        String[] ignore = config.getStringList(
            "ignore",
            CATEGORY_DETAILS,
            new String[0],
            "Regular expressions: matching messages are left out of Errors and Warnings (they stay in All).\n"
                + "Matched against \"source: message\", for example \"Angelica: Frame pacing.*\"");
        List<Pattern> patterns = new ArrayList<>();
        for (String regex : ignore) {
            if (regex.trim()
                .isEmpty()) continue;
            try {
                patterns.add(Pattern.compile(regex));
            } catch (PatternSyntaxException e) {
                GTNHTweaks.LOG.warn("Invalid ignore pattern in logs.cfg: {}", regex);
            }
        }
        ignorePatterns = patterns;
    }

    static boolean isIgnored(LogRecord record) {
        List<Pattern> patterns = ignorePatterns;
        if (patterns.isEmpty()) return false;
        String text = record.source() + ": " + record.message;
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text)
                .matches()) return true;
        }
        return false;
    }

    @Override
    public void coremodInit() {
        LogFiles.start();
    }

    /** Called from the crash report mixin after Minecraft saved a crash report. */
    public static void onCrashReportSaved(Object report, String completeReport, File file) {
        if (!INSTANCE.isEnabled()) return;
        LogFiles.onCrash(report, completeReport, file);
    }
}
