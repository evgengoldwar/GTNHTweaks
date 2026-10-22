package gtnhtweaks.modules.logs.client;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.crash.CrashReport;

import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.modules.logs.CrashDiagnosis;
import gtnhtweaks.modules.logs.LogsModule;

/** Everything the crash screen shows: what happened, the problem in plain words, the whole report. */
public final class CrashInfo {

    public enum Kind {
        /** The game crashed while running; the world was closed and the game goes on. */
        IN_GAME,
        /** The integrated server crashed; the world was closed and the game goes on. */
        SERVER,
        /** The game crashed while starting; it closes when the screen is left. */
        STARTUP,
        /** Forge found a problem with the installed mods and cannot go on. */
        MODS,
        /** The game crashed again and again, even with the crash screen open; it closes when the screen is left. */
        REPEATED
    }

    final Kind kind;
    final String title;
    final String description;
    final String headline;
    final String likelyCause;
    final String problem;
    final String fullReport;
    final File file;

    private CrashInfo(Kind kind, String description, String headline, String likelyCause, String problem,
        String fullReport, File file) {
        this.kind = kind;
        this.title = kind == Kind.STARTUP ? "The game could not start"
            : kind == Kind.MODS ? "The game cannot start with these mods"
                : kind == Kind.REPEATED ? "The game keeps crashing"
                    : kind == Kind.SERVER ? "The world crashed" : "The game crashed";
        this.description = description;
        this.headline = headline;
        this.likelyCause = likelyCause;
        this.problem = problem;
        this.fullReport = fullReport;
        this.file = file;
    }

    boolean canReturnToMenu() {
        return kind == Kind.IN_GAME || kind == Kind.SERVER;
    }

    // ---- from a crash report

    public static CrashInfo fromReport(CrashReport report, Kind kind) {
        String full;
        try {
            full = report.getCompleteReport();
        } catch (Throwable t) {
            full = stackTrace(report.getCrashCause());
        }
        Throwable cause = report.getCrashCause();
        CrashDiagnosis diagnosis = CrashDiagnosis.of(cause);
        String summary;
        try {
            summary = LogsModule.summarizeCrash(full);
        } catch (Throwable t) {
            summary = stackTrace(cause);
        }

        StringBuilder problem = new StringBuilder();
        diagnosis.appendTo(problem);
        problem.append('\n')
            .append(summary);
        appendRecentErrors(problem);

        String description = report.getDescription() + ": " + cause;
        return new CrashInfo(
            kind,
            description,
            diagnosis.headline,
            likelyCause(summary),
            problem.toString(),
            full,
            report.getFile());
    }

    /** First mod of the "Mods in trace" line of the summary, or null. */
    private static String likelyCause(String summary) {
        for (String line : summary.split("\n")) {
            if (!line.startsWith("Mods in trace")) continue;
            String value = line.substring(line.indexOf(':') + 1)
                .trim();
            if (value.startsWith("none")) return null;
            int end = value.indexOf(',');
            if (end < 0) end = value.indexOf("  ");
            return (end < 0 ? value : value.substring(0, end)).trim();
        }
        return null;
    }

    private static void appendRecentErrors(StringBuilder out) {
        List<String> errors;
        try {
            errors = LogsModule.recentErrors();
        } catch (Throwable t) {
            return;
        }
        if (errors.isEmpty()) return;
        out.append("\nLAST ERRORS IN THE LOG\n");
        for (String error : errors) out.append(error)
            .append('\n');
    }

    // ---- from a mod loading problem of Forge

    /** A problem Forge shows on its own screen: missing mods, wrong versions, duplicates, dependency loops. */
    public static CrashInfo fromModProblem(Object problem, String screenName) {
        Throwable exception = problem instanceof Throwable ? (Throwable) problem : null;
        String type = problem == null ? screenName
            : problem.getClass()
                .getSimpleName();
        CrashDiagnosis d;
        if (type.contains("MissingMods")) {
            d = new CrashDiagnosis("Mods are missing");
            d.lines.add("Some installed mods need other mods that are not installed, or need other versions:");
            for (Object version : collection(field(problem, "missingMods"))) {
                d.lines.add("  - " + call(version, "getLabel") + "  version " + call(version, "getRangeString"));
            }
            d.lines.add("The mods that need them are named in the last errors of the log below.");
        } else if (type.contains("WrongMinecraft")) {
            d = new CrashDiagnosis("A mod is made for another Minecraft version");
            Object mod = field(problem, "mod");
            if (mod != null) {
                d.lines.add(
                    "  - " + call(mod, "getName")
                        + " ("
                        + call(mod, "getModId")
                        + ") needs Minecraft "
                        + call(mod, "acceptableMinecraftVersionRange"));
            }
        } else if (type.contains("DuplicateMods") || type.contains("Dupes")) {
            d = new CrashDiagnosis("A mod is installed twice");
            Object dupes = field(problem, "dupes");
            Object map = dupes == null ? null : call(dupes, "asMap");
            if (map instanceof Map) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) map).entrySet()) {
                    d.lines.add("  - " + call(entry.getKey(), "getName") + ": " + entry.getValue());
                }
            }
            d.lines.add("Remove all but one copy of each from the mods folder.");
        } else if (type.contains("Sorting")) {
            d = new CrashDiagnosis("Mods depend on each other in a loop");
            Object data = problem == null ? null : call(problem, "getExceptionData");
            if (data != null) {
                d.lines.add("  First mod : " + call(data, "getFirstBadNode"));
                d.lines.add("  Loop      : " + call(data, "getVisitedNodes"));
            }
        } else {
            d = new CrashDiagnosis("Forge cannot load the mods");
        }
        if (exception != null && exception.getMessage() != null) d.lines.add("Forge says : " + exception.getMessage());

        StringBuilder problemText = new StringBuilder();
        d.appendTo(problemText);
        appendRecentErrors(problemText);

        // a crash report is saved as for a crash, so the problem is not lost when the game is closed
        CrashReport report = new CrashReport(
            "Mod loading problem: " + d.headline,
            exception != null ? exception : new RuntimeException(d.headline + " (" + screenName + ")"));
        File file = save(report);
        String full;
        try {
            full = problemText + "\n" + report.getCompleteReport();
        } catch (Throwable t) {
            full = problemText.toString();
        }
        return new CrashInfo(Kind.MODS, d.headline, d.headline, null, problemText.toString(), full, file);
    }

    // ---- helpers

    /** Saves the report into crash-reports like Minecraft does, unless it was saved already. */
    public static File save(CrashReport report) {
        try {
            if (report.getFile() == null) {
                File dir = new File(Minecraft.getMinecraft().mcDataDir, "crash-reports");
                String name = "crash-" + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()) + "-client.txt";
                report.saveToFile(new File(dir, name));
            }
        } catch (Throwable t) {
            GTNHTweaks.LOG.error("Could not save the crash report", t);
        }
        return report.getFile();
    }

    static String stackTrace(Throwable t) {
        if (t == null) return "unknown";
        StringWriter out = new StringWriter();
        t.printStackTrace(new PrintWriter(out));
        return out.toString();
    }

    private static Object field(Object target, String name) {
        if (target == null) return null;
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {} catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static Object call(Object target, String name) {
        if (target == null) return null;
        try {
            Method method = target.getClass()
                .getMethod(name);
            return method.invoke(target);
        } catch (Throwable t) {
            Object value = field(target, name);
            return value != null ? value : "?";
        }
    }

    private static Collection<?> collection(Object value) {
        return value instanceof Collection ? (Collection<?>) value : new ArrayList<>();
    }
}
