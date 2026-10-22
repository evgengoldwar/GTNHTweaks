package gtnhtweaks.modules.logs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Short version of a Minecraft crash report: what crashed, the exception with a folded stack trace, the mod jars in the
 * trace and the details of the crashing object. The level, the system details and the mod list are left out.
 */
final class CrashSummary {

    /** Sections of the report that do not tell what crashed. */
    private static final Set<String> SKIPPED_SECTIONS = new HashSet<>(
        Arrays.asList("Head", "System Details", "Affected level"));

    private CrashSummary() {}

    static String summarize(String report, int maxFrames) {
        String[] lines = report.split("\r?\n");
        String description = "";
        List<String> trace = new ArrayList<>();
        int i = 0;
        for (; i < lines.length; i++) {
            if (lines[i].startsWith("Description: ")) {
                description = lines[i].substring("Description: ".length())
                    .trim();
                i++;
                break;
            }
        }
        while (i < lines.length && lines[i].trim()
            .isEmpty()) i++;
        for (; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim()
                .isEmpty() || line.startsWith("A detailed walkthrough")) break;
            trace.add(line);
        }

        StringBuilder out = new StringBuilder(2048);
        String exception = trace.isEmpty() ? "unknown"
            : trace.get(0)
                .trim();
        String rootCause = null;
        for (String line : trace) {
            if (line.trim()
                .startsWith("Caused by:")) {
                rootCause = line.trim()
                    .substring("Caused by:".length())
                    .trim();
            }
        }
        out.append("What crashed  : ")
            .append(description.isEmpty() ? "unknown" : description)
            .append('\n');
        out.append("Exception     : ")
            .append(exception)
            .append('\n');
        if (rootCause != null) {
            out.append("Root cause    : ")
                .append(rootCause)
                .append('\n');
        }
        List<String> jars = jarsRootCauseFirst(trace);
        out.append("Mods in trace : ")
            .append(
                jars.isEmpty() ? "none, only Minecraft, Forge or Java code"
                    : String.join(", ", jars) + "  (the first one is the most likely cause)")
            .append('\n');

        if (!trace.isEmpty()) {
            out.append("\nSTACK TRACE (internal Java, Forge and launcher frames folded)\n");
            for (String line : StackTraces.condense(trace, maxFrames)) {
                out.append("    ")
                    .append(line)
                    .append('\n');
            }
        }

        String details = details(lines);
        if (!details.isEmpty()) {
            out.append("\nDETAILS\n")
                .append(details);
        }
        return out.toString();
    }

    /** The jars of the deepest cause come first: wrapping exceptions usually point at Minecraft code. */
    private static List<String> jarsRootCauseFirst(List<String> trace) {
        List<List<String>> segments = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String line : trace) {
            if (line.trim()
                .startsWith("Caused by:")) {
                segments.add(current);
                current = new ArrayList<>();
            }
            current.add(line);
        }
        segments.add(current);
        Set<String> jars = new LinkedHashSet<>();
        for (int i = segments.size() - 1; i >= 0; i--) {
            jars.addAll(StackTraces.modJars(segments.get(i)));
        }
        return new ArrayList<>(jars);
    }

    /** The "Details:" of every section that describes the crashing object, without their stack traces. */
    private static String details(String[] lines) {
        StringBuilder out = new StringBuilder();
        String section = null;
        boolean inDetails = false;
        boolean sectionWritten = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("-- ") && trimmed.endsWith(" --") && trimmed.length() > 6) {
                section = trimmed.substring(3, trimmed.length() - 3)
                    .trim();
                inDetails = false;
                sectionWritten = false;
                continue;
            }
            if (section == null || SKIPPED_SECTIONS.contains(section)) continue;
            if (trimmed.equals("Details:")) {
                inDetails = true;
                continue;
            }
            if (trimmed.equals("Stacktrace:") || trimmed.isEmpty()) {
                inDetails = false;
                continue;
            }
            if (!inDetails) continue;
            if (!sectionWritten) {
                out.append("  ")
                    .append(section)
                    .append('\n');
                sectionWritten = true;
            }
            out.append("      ")
                .append(trimmed)
                .append('\n');
        }
        return out.toString();
    }
}
