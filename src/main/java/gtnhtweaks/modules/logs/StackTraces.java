package gtnhtweaks.modules.logs;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import net.minecraft.launchwrapper.Launch;

/**
 * Turns exceptions into short readable stack traces: frames of Java, the launcher and the event buses are folded, the
 * number of frames is limited, and the mod jars found in the trace are named.
 */
final class StackTraces {

    /** Frames of these packages are internals: runs of them are folded into one line. */
    private static final String[] INTERNAL_PACKAGES = { "java.", "javax.", "jdk.", "sun.", "com.sun.",
        "com.google.common.", "net.minecraft.launchwrapper.", "com.gtnewhorizons.retrofuturabootstrap.",
        "cpw.mods.fml.common.eventhandler.", "cpw.mods.fml.common.LoadController", "cpw.mods.fml.common.Loader.",
        "cpw.mods.fml.common.FMLModContainer", "kotlin.", "scala." };

    /** The {@code [file.class:?]} suffix log4j adds to frames printed in the log file. */
    private static final Pattern FRAME_SUFFIX = Pattern.compile("\\s+~?\\[[^\\]]*\\]$");

    private static final int MAX_CACHED_CLASSES = 20000;
    private static final Map<String, String> JAR_CACHE = new HashMap<>();

    private StackTraces() {}

    /** The full stack trace, exactly as {@link Throwable#printStackTrace()} prints it. */
    static String full(Throwable t) {
        StringWriter writer = new StringWriter();
        t.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    /** The exception and its causes as lines: a header, {@code at ...} frames, then {@code Caused by: ...}. */
    static List<String> lines(Throwable t) {
        List<String> lines = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean first = true;
        while (t != null && seen.add(t)) {
            lines.add((first ? "" : "Caused by: ") + t);
            for (StackTraceElement frame : t.getStackTrace()) {
                lines.add("at " + frame(frame));
            }
            first = false;
            t = t.getCause();
        }
        return lines;
    }

    private static String frame(StackTraceElement e) {
        String location;
        if (e.isNativeMethod()) location = "Native Method";
        else if (e.getFileName() == null) location = "Unknown Source";
        else if (e.getLineNumber() >= 0) location = e.getFileName() + ":" + e.getLineNumber();
        else location = e.getFileName();
        return e.getClassName() + "." + e.getMethodName() + "(" + location + ")";
    }

    static boolean isFrameLine(String line) {
        return line.trim()
            .startsWith("at ");
    }

    static boolean isMoreLine(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("...") && trimmed.endsWith("more");
    }

    /** Whether a line of a printed stack trace continues the trace started by an earlier line. */
    static boolean isContinuation(String line) {
        String trimmed = line.trim();
        return isFrameLine(line) || isMoreLine(line)
            || trimmed.startsWith("Caused by:")
            || trimmed.startsWith("Suppressed:");
    }

    /**
     * The frame of an {@code at ...} line without the class loader and module prefix ({@code Launch//},
     * {@code java.base/}) and without the jar suffix log4j adds.
     */
    static String frameText(String line) {
        String text = line.trim()
            .substring(3)
            .trim();
        text = FRAME_SUFFIX.matcher(text)
            .replaceFirst("");
        int paren = text.indexOf('(');
        int slash = text.lastIndexOf('/', paren >= 0 ? paren : text.length());
        return slash >= 0 ? text.substring(slash + 1) : text;
    }

    /** The class of a frame text such as {@code a.b.C.method(C.java:1)}. */
    static String frameClass(String frame) {
        int paren = frame.indexOf('(');
        String method = paren >= 0 ? frame.substring(0, paren) : frame;
        int dot = method.lastIndexOf('.');
        return dot > 0 ? method.substring(0, dot) : method;
    }

    static boolean isInternal(String frame) {
        for (String prefix : INTERNAL_PACKAGES) {
            if (frame.startsWith(prefix)) return true;
        }
        return false;
    }

    /** The first frame that is not an internal one, used to tell apart exceptions with the same message. */
    static String firstOwnFrame(List<String> lines) {
        if (lines == null) return "";
        for (String line : lines) {
            if (!isFrameLine(line)) continue;
            String frame = frameText(line);
            if (!isInternal(frame)) return frame;
        }
        return "";
    }

    /**
     * Shortens a stack trace. Every exception of the chain keeps its first frame (where it was thrown); further runs of
     * internal frames are folded and at most {@code maxFrames} frames per exception are kept.
     */
    static List<String> condense(List<String> lines, int maxFrames) {
        List<String> out = new ArrayList<>();
        Segment segment = new Segment(out);
        for (String line : lines) {
            if (isFrameLine(line)) {
                segment.frame(frameText(line), maxFrames);
            } else if (isMoreLine(line)) {
                segment.hidden += parseMore(line);
            } else {
                segment.end();
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) out.add(trimmed);
                segment = new Segment(out);
            }
        }
        segment.end();
        return out;
    }

    private static int parseMore(String line) {
        String number = line.trim()
            .replaceAll("[^0-9]", "");
        try {
            return number.isEmpty() ? 0 : Integer.parseInt(number);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Frames of one exception of the chain. */
    private static final class Segment {

        private final List<String> out;
        private final List<String> internalRun = new ArrayList<>();
        private int kept;
        int hidden;

        Segment(List<String> out) {
            this.out = out;
        }

        void frame(String frame, int maxFrames) {
            if (kept >= maxFrames) {
                hidden += internalRun.size() + 1;
                internalRun.clear();
                return;
            }
            if (kept > 0 && isInternal(frame)) {
                internalRun.add(frame);
                return;
            }
            flushRun();
            out.add("    at " + frame);
            kept++;
        }

        private void flushRun() {
            if (internalRun.size() == 1) {
                out.add("    at " + internalRun.get(0));
                kept++;
            } else if (!internalRun.isEmpty()) {
                out.add("    ... " + internalRun.size() + " internal frames (Java, Forge, launcher)");
            }
            internalRun.clear();
        }

        void end() {
            // Internal frames at the bottom of the trace are only the launcher and the main loop
            hidden += internalRun.size();
            internalRun.clear();
            if (hidden > 0) out.add("    ... " + hidden + " more");
            hidden = 0;
        }
    }

    /**
     * Names of the mod jars whose classes appear in the trace, from the top of the stack. Only jars of the mods folder
     * count, so Minecraft, Forge, Java and the libraries are never named.
     */
    static List<String> modJars(List<String> lines) {
        Set<String> jars = new LinkedHashSet<>();
        if (lines == null) return new ArrayList<>(jars);
        for (String line : lines) {
            if (!isFrameLine(line)) continue;
            String frame = frameText(line);
            if (isInternal(frame)) continue;
            String jar = jarOf(frameClass(frame));
            if (jar != null) jars.add(jar);
        }
        return new ArrayList<>(jars);
    }

    private static String jarOf(String className) {
        synchronized (JAR_CACHE) {
            if (JAR_CACHE.containsKey(className)) return JAR_CACHE.get(className);
        }
        String jar = findJar(className);
        synchronized (JAR_CACHE) {
            if (JAR_CACHE.size() < MAX_CACHED_CLASSES) JAR_CACHE.put(className, jar);
        }
        return jar;
    }

    private static String findJar(String className) {
        // Inner and generated classes live in the file of their outer class
        int dollar = className.indexOf('$');
        String outer = dollar > 0 ? className.substring(0, dollar) : className;
        String resource = outer.replace('.', '/') + ".class";
        try {
            ClassLoader loader = Launch.classLoader != null ? Launch.classLoader : StackTraces.class.getClassLoader();
            URL url = loader.getResource(resource);
            if (url == null) return null;
            // jar:file:/.../mods/name.jar!/package/Class.class
            String path = url.getPath();
            int bang = path.indexOf("!/");
            if (bang < 0) return null;
            String jarPath = new URI(path.substring(0, bang)).getPath()
                .replace('\\', '/');
            if (!jarPath.contains("/mods/")) return null;
            return jarPath.substring(jarPath.lastIndexOf('/') + 1);
        } catch (Throwable t) {
            return null;
        }
    }
}
