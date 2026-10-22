package gtnhtweaks.modules.logs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.core.TweakModule;

/**
 * Sorts the game log into separate files in {@code GTNHTweaksLogs}: errors, warnings and crashes without the noise,
 * plus the full log and the full crash report.
 */
public final class LogsModule extends TweakModule {

    public static final LogsModule INSTANCE = new LogsModule();

    private static final String CATEGORY_GENERAL = "general";
    private static final String CATEGORY_FILES = "files";
    private static final String CATEGORY_DETAILS = "details";
    private static final String CATEGORY_CRASH_SCREEN = "crashScreen";

    static String folder = "GTNHTweaksLogs";
    static int maxBackups = 5;

    static boolean writeAll = true;
    static boolean writeErrors = true;
    static boolean writeWarnings = true;
    static boolean writeCrash = true;
    static boolean writeFullCrash = true;
    static boolean writeLoading = true;
    static boolean writeMods = true;

    public static boolean replaceCrashScreen = true;
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
        maxBackups = config.getInt(
            "maxBackups",
            CATEGORY_GENERAL,
            maxBackups,
            0,
            1000,
            "On start, the logs of the previous launch are packed into \"Backup <launch>.zip\" in the same folder.\n"
                + "How many backups to keep, older ones are deleted. 0 deletes the old logs without a backup");

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
        writeLoading = config.getBoolean(
            "writeLoading",
            CATEGORY_FILES,
            writeLoading,
            "Loading: loading time by stage, the slowest mods and steps, compared with the previous launch");
        writeMods = config.getBoolean(
            "writeMods",
            CATEGORY_FILES,
            writeMods,
            "Mods: versions of Java, Minecraft and Forge, memory, graphics card and the mod list with the changes "
                + "since the previous launch");

        config.addCustomCategoryComment(CATEGORY_CRASH_SCREEN, "The screen shown after a crash");
        replaceCrashScreen = config.getBoolean(
            "replaceCrashScreen",
            CATEGORY_CRASH_SCREEN,
            replaceCrashScreen,
            "After a crash, keep the game open and show the GTNHTweaks crash screen: the problem in plain words, "
                + "the whole crash report to scroll through, buttons to copy it and to open the log folder.\n"
                + "Crash while starting: the screen is shown before the game closes. Crash in game or of the "
                + "integrated server: the world is closed and the game goes on in the main menu.\n"
                + "Also replaces the Forge screens of missing, duplicate or wrong mods. With BetterCrashes "
                + "installed, it keeps catching the crashes and only its screen is replaced.\n"
                + "Changes require a game restart");

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
        if (replaceCrashScreen) {
            try {
                EarlyCrashWindow.install();
            } catch (Throwable t) {
                GTNHTweaks.LOG.warn("Could not install the early crash window", t);
            }
        }
    }

    @Override
    public void init(FMLInitializationEvent event) {
        if (writeMods && event.getSide()
            .isClient()) MinecraftForge.EVENT_BUS.register(new GraphicsInfo());
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        if (!writeMods) return;
        try {
            ModsReport.collectMods();
            ModsReport.write();
        } catch (RuntimeException e) {
            GTNHTweaks.LOG.error("Failed to write the mod list", e);
        }
    }

    /** Short description of a crash report: what crashed, the exception, the mods in the stack trace. */
    public static String summarizeCrash(String completeReport) {
        return CrashSummary.summarize(completeReport, maxCrashStackFrames);
    }

    /** The last errors of the log, oldest first, empty when the logs are off. */
    public static List<String> recentErrors() {
        return INSTANCE.isEnabled() ? LogFiles.recentErrors() : new ArrayList<>();
    }

    /** True if BetterCrashes is installed: it then catches the crashes, and only its screen is replaced. */
    public static boolean betterCrashesInstalled() {
        ClassLoader loader = LogsModule.class.getClassLoader();
        return loader != null && loader.getResource("vfyjxf/bettercrashes/mixins/MixinPlugin.class") != null;
    }

    /** The folder the logs are written to. */
    public static File logFolder() {
        return LogFiles.folder();
    }

    /** Called from the crash report mixin after Minecraft saved a crash report. */
    public static void onCrashReportSaved(Object report, String completeReport, File file) {
        if (!INSTANCE.isEnabled()) return;
        LogFiles.onCrash(report, completeReport, file);
    }
}
