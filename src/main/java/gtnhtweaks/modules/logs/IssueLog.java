package gtnhtweaks.modules.logs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Errors or warnings of the session, grouped by source. Repeated messages are kept once with a counter, so a message
 * spammed every tick takes one entry instead of thousands of lines.
 */
final class IssueLog {

    private static final Pattern OBJECT_HASH = Pattern.compile("@[0-9a-fA-F]{4,}");
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final String RULE = repeat('=', 100);

    /** Unique messages kept per file; further new messages are only counted. */
    private static final int MAX_ENTRIES = 3000;
    private static final int MAX_EXAMPLES = 5;
    private static final int MAX_COUNTED_VARIANTS = 1000;

    private final String title;
    private final String description;
    private final Map<String, Entry> entries = new HashMap<>();
    private final Map<String, Group> groups = new LinkedHashMap<>();
    private int total;
    private int dropped;
    private boolean dirty;

    IssueLog(String title, String description) {
        this.title = title;
        this.description = description;
    }

    boolean isEmpty() {
        return total == 0;
    }

    boolean isDirty() {
        return dirty;
    }

    void add(LogRecord record) {
        total++;
        dirty = true;
        String source = record.source();
        Group group = groups.get(source);
        if (group == null) {
            group = new Group(source);
            groups.put(source, group);
        }
        group.count++;

        String key = source + '\u0000'
            + record.level
            + '\u0000'
            + normalize(record.message)
            + '\u0000'
            + traceKey(record.trace);
        Entry entry = entries.get(key);
        if (entry != null) {
            entry.repeat(record);
            return;
        }
        if (entries.size() >= MAX_ENTRIES) {
            dropped++;
            return;
        }
        entry = new Entry(record, LogsModule.maxStackFrames);
        entries.put(key, entry);
        group.entries.add(entry);
    }

    private static String normalize(String message) {
        String text = OBJECT_HASH.matcher(message)
            .replaceAll("@");
        return NUMBER.matcher(text)
            .replaceAll("#");
    }

    private static String traceKey(List<String> trace) {
        if (trace == null || trace.isEmpty()) return "";
        String header = trace.get(0);
        // Only the exception class: its message often contains numbers or object names
        int colon = header.indexOf(':');
        return (colon > 0 ? header.substring(0, colon) : header) + " " + StackTraces.firstOwnFrame(trace);
    }

    /** The whole file. Clears the dirty flag. */
    String render(String sessionStart, String updated) {
        dirty = false;
        StringBuilder out = new StringBuilder(4096);
        out.append(title)
            .append('\n');
        out.append("Session started ")
            .append(sessionStart)
            .append(", updated ")
            .append(updated)
            .append('\n');
        out.append(description)
            .append('\n');
        out.append(total)
            .append(" messages, ")
            .append(entries.size())
            .append(" unique, from ")
            .append(groups.size())
            .append(" sources. Repeated messages are shown once with a counter (xN).\n");
        if (dropped > 0) {
            out.append("Limit of ")
                .append(MAX_ENTRIES)
                .append(" unique messages reached: ")
                .append(dropped)
                .append(" later messages are only in the All log.\n");
        }

        List<Group> sorted = new ArrayList<>(groups.values());
        Collections.sort(sorted, (a, b) -> a.count != b.count ? b.count - a.count : a.name.compareTo(b.name));

        out.append('\n')
            .append("SOURCES\n");
        out.append(String.format("%8s %8s  %s\n", "messages", "unique", "source"));
        for (Group group : sorted) {
            out.append(String.format("%8d %8d  %s\n", group.count, group.entries.size(), group.name));
        }

        for (Group group : sorted) {
            out.append('\n')
                .append(RULE)
                .append('\n');
            out.append(group.name)
                .append(" - ")
                .append(group.count)
                .append(group.count == 1 ? " message" : " messages");
            if (group.entries.size() != group.count) {
                out.append(", ")
                    .append(group.entries.size())
                    .append(" unique");
            }
            out.append('\n')
                .append(RULE)
                .append('\n');
            for (Entry entry : group.entries) {
                out.append('\n');
                entry.render(out);
            }
        }
        return out.toString();
    }

    private static final class Group {

        final String name;
        final List<Entry> entries = new ArrayList<>();
        int count;

        Group(String name) {
            this.name = name;
        }
    }

    private static final class Entry {

        final long first;
        final String source;
        final String level;
        final String thread;
        final String mod;
        final String message;
        final List<String> trace;
        final List<String> examples = new ArrayList<>();
        final Set<String> variants = new HashSet<>();
        long last;
        int count = 1;
        int variantCount = 1;
        List<String> jars;

        Entry(LogRecord record, int maxFrames) {
            first = last = record.time;
            source = record.source();
            level = record.level;
            thread = record.thread;
            mod = record.mod;
            message = record.message;
            trace = record.trace == null ? null : StackTraces.condense(record.trace, maxFrames);
            // An exception printed to System.err starts with its own header, which is already the message
            if (trace != null && !trace.isEmpty()
                && trace.get(0)
                    .equals(message.trim())) {
                trace.remove(0);
            }
            variants.add(record.message);
        }

        void repeat(LogRecord record) {
            count++;
            last = record.time;
            if (variants.size() < MAX_COUNTED_VARIANTS && variants.add(record.message)) {
                variantCount++;
                if (examples.size() < MAX_EXAMPLES) examples.add(record.message);
            }
        }

        void render(StringBuilder out) {
            out.append('[')
                .append(LogFiles.time(first))
                .append("] ")
                .append(level);
            if (count > 1) {
                out.append("  x")
                    .append(count)
                    .append(" (last ")
                    .append(LogFiles.time(last))
                    .append(')');
            }
            out.append("  thread: ")
                .append(thread);
            if (!mod.isEmpty() && !source.equalsIgnoreCase(mod) && !source.endsWith(" / " + mod)) {
                out.append("  mod: ")
                    .append(mod);
            }
            if (trace != null) {
                if (jars == null) jars = StackTraces.modJars(trace);
                if (!jars.isEmpty()) {
                    out.append("  jars in trace: ")
                        .append(String.join(", ", jars));
                }
            }
            out.append('\n');
            appendIndented(out, message, "    ");
            if (variantCount > 1) {
                out.append("    similar messages: ")
                    .append(variantCount - 1)
                    .append(variants.size() >= MAX_COUNTED_VARIANTS ? "+" : "")
                    .append('\n');
                for (String example : examples) {
                    appendIndented(out, example, "      ~ ");
                }
                if (variantCount - 1 > examples.size()) out.append("      ~ ...\n");
            }
            if (trace != null) {
                for (String line : trace) {
                    out.append("    ")
                        .append(line)
                        .append('\n');
                }
            }
        }
    }

    private static void appendIndented(StringBuilder out, String text, String indent) {
        for (String line : text.split("\r?\n")) {
            out.append(indent)
                .append(line)
                .append('\n');
        }
    }

    private static String repeat(char c, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, c);
        return new String(chars);
    }
}
