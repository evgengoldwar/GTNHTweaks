package gtnhtweaks.modules.logs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loading time of the game, from the progress bar steps FML writes to the log ({@code Bar Step: PreInitialization -
 * GregTech took 1.414s}): the total, every stage, the slowest mods and the slowest resource steps, compared with the
 * previous launch.
 */
final class LoadingReport {

    private static final Pattern BAR = Pattern.compile("^Bar (Step|Finished): (.+) took (\\d+(?:\\.\\d+)?)s$");
    private static final Pattern GAME_LOADED = Pattern.compile("The game loaded in (\\d+(?:\\.\\d+)?) seconds");

    /** Stages FML runs once per mod. Their steps are named after the mod. */
    private static final List<String> MOD_STAGES = Arrays.asList(
        "Construction",
        "PreInitialization",
        "Initialization",
        "InterModComms$IMC",
        "PostInitialization",
        "LoadComplete");
    private static final String[] MOD_STAGE_COLUMNS = { "constr", "preinit", "init", "imc", "postinit", "complete" };
    /** Main stages of the loading, in the order they run; the other bars are parts of them. */
    private static final List<String> MAIN_STAGES = Arrays.asList(
        "Construction",
        "PreInitialization",
        "Rendering Setup",
        "Initialization",
        "InterModComms$IMC",
        "PostInitialization",
        "LoadComplete",
        "Reloading Texture Manager",
        "Loading Resources");
    /** The outer bar of FML: it ends when the game has loaded. */
    private static final String LOADING_BAR = "Loading";
    /** Runs on every server join, not while loading. */
    private static final Set<String> IGNORED_STAGES = new HashSet<>(Arrays.asList("ModIdMapping"));

    private static final int SHOWN_MODS = 30;
    private static final int SHOWN_STEPS = 15;
    private static final double MIN_STEP = 0.05;
    private static final double SLOWER_THRESHOLD = 0.5;
    private static final String RULE = "----------------------------------------------------------------------";

    private final long jvmStart;
    private final Map<String, double[]> stages = new LinkedHashMap<>();
    private final Map<String, double[]> mods = new LinkedHashMap<>();
    private final List<Step> steps = new ArrayList<>();
    private double gameLoaded = -1;
    private double total = -1;
    private boolean dirty;

    private final Properties previous = new Properties();
    private final boolean hasPrevious;

    LoadingReport(long jvmStart, File statsFile) {
        this.jvmStart = jvmStart;
        boolean loaded = false;
        if (statsFile.isFile()) {
            try (InputStream in = new FileInputStream(statsFile)) {
                previous.load(in);
                loaded = previous.getProperty("total") != null;
            } catch (IOException | IllegalArgumentException ignored) {}
        }
        hasPrevious = loaded;
    }

    boolean isDirty() {
        return dirty;
    }

    /** Whether the game has finished loading. */
    boolean isFinished() {
        return total >= 0;
    }

    /** Takes the loading steps out of a message. Returns true when this message finished the loading. */
    boolean add(LogRecord record) {
        if (isFinished()) return false;
        String message = record.message;
        Matcher loaded = GAME_LOADED.matcher(message);
        if (loaded.find()) {
            gameLoaded = Double.parseDouble(loaded.group(1));
            dirty = true;
            return false;
        }
        if (!message.startsWith("Bar ")) return false;
        Matcher m = BAR.matcher(message);
        if (!m.matches()) return false;
        double seconds = Double.parseDouble(m.group(3));
        String text = m.group(2);
        dirty = true;
        if ("Finished".equals(m.group(1))) {
            if (text.contains(" - ")) return false;
            if (LOADING_BAR.equals(text)) {
                total = gameLoaded >= 0 ? gameLoaded : Math.max(0, (record.time - jvmStart) / 1000.0);
                return true;
            }
            if (IGNORED_STAGES.contains(text)) return false;
            double[] stage = stages.get(text);
            if (stage == null) stages.put(text, stage = new double[2]);
            stage[0] += seconds;
            stage[1]++;
            return false;
        }

        int dash = text.indexOf(" - ");
        if (dash < 0) return false;
        String stage = text.substring(0, dash);
        String name = text.substring(dash + 3);
        if (IGNORED_STAGES.contains(stage)) return false;
        int column = MOD_STAGES.indexOf(stage);
        if (column >= 0) {
            double[] times = mods.get(name);
            if (times == null) mods.put(name, times = new double[MOD_STAGES.size() + 1]);
            times[column] += seconds;
            times[MOD_STAGES.size()] += seconds;
        } else if (seconds >= MIN_STEP && steps.size() < 5000) {
            steps.add(new Step(stage + " - " + name, seconds));
        }
        return false;
    }

    /** Remembers this launch, so the next one can be compared with it. Only called once the loading has finished. */
    void save(File statsFile) {
        Properties stats = new Properties();
        stats.setProperty("total", format(total));
        for (Map.Entry<String, double[]> mod : mods.entrySet()) {
            stats.setProperty(
                "mod." + mod.getKey(),
                format(mod.getValue()[MOD_STAGES.size()]));
        }
        try (OutputStream out = new FileOutputStream(statsFile)) {
            stats.store(out, "GTNHTweaks: loading time of the previous launch");
        } catch (IOException ignored) {}
    }

    String render(String sessionStart, String updated) {
        dirty = false;
        StringBuilder out = new StringBuilder(4096);
        out.append("GTNHTweaks - LOADING TIME\n");
        out.append("Session started ")
            .append(sessionStart)
            .append(", updated ")
            .append(updated)
            .append('\n');
        out.append("Taken from the FML loading progress bar. Times are in seconds.\n\n");

        double previousTotal = previousValue("total");
        if (isFinished()) {
            out.append("Game loaded in ")
                .append(format(total))
                .append(" s");
            if (hasPrevious) {
                out.append("   previous launch: ")
                    .append(format(previousTotal))
                    .append(" s (")
                    .append(signed(total - previousTotal))
                    .append(')');
            }
            out.append('\n');
        } else {
            out.append("The game has not finished loading yet");
            if (hasPrevious) {
                out.append(" (previous launch: ")
                    .append(format(previousTotal))
                    .append(" s)");
            }
            out.append('\n');
        }

        out.append('\n')
            .append("STAGES\n")
            .append(RULE)
            .append('\n');
        for (String name : MAIN_STAGES) {
            if (stages.containsKey(name)) appendStage(out, name, stages.get(name));
        }
        boolean otherHeader = false;
        for (Map.Entry<String, double[]> stage : stages.entrySet()) {
            if (MAIN_STAGES.contains(stage.getKey())) continue;
            if (!otherHeader) {
                out.append("  parts of the stages above:\n");
                otherHeader = true;
            }
            appendStage(out, "  " + stage.getKey(), stage.getValue());
        }

        List<Map.Entry<String, double[]>> sortedMods = new ArrayList<>(mods.entrySet());
        final int totalColumn = MOD_STAGES.size();
        sortedMods.sort((a, b) -> Double.compare(b.getValue()[totalColumn], a.getValue()[totalColumn]));
        out.append('\n')
            .append("SLOWEST MODS (")
            .append(mods.size())
            .append(" mods in total)\n")
            .append(RULE)
            .append('\n');
        out.append(String.format(Locale.ROOT, "%8s %8s", "total", hasPrevious ? "change" : ""));
        for (String column : MOD_STAGE_COLUMNS) {
            out.append(String.format(Locale.ROOT, " %8s", column));
        }
        out.append("  mod\n");
        for (int i = 0; i < sortedMods.size() && i < SHOWN_MODS; i++) {
            Map.Entry<String, double[]> mod = sortedMods.get(i);
            double[] times = mod.getValue();
            String change = "";
            double before = previousValue("mod." + mod.getKey());
            if (hasPrevious) change = before >= 0 ? signed(times[totalColumn] - before) : "new";
            out.append(String.format(Locale.ROOT, "%8.2f %8s", times[totalColumn], change));
            for (int c = 0; c < totalColumn; c++) {
                out.append(String.format(Locale.ROOT, " %8.2f", times[c]));
            }
            out.append("  ")
                .append(mod.getKey())
                .append('\n');
        }

        if (hasPrevious) {
            List<String> slower = new ArrayList<>();
            for (Map.Entry<String, double[]> mod : sortedMods) {
                double before = previousValue("mod." + mod.getKey());
                double now = mod.getValue()[totalColumn];
                if (before >= 0 && now - before >= SLOWER_THRESHOLD) {
                    slower.add(mod.getKey() + " " + format(before) + " -> " + format(now) + " s");
                }
            }
            if (!slower.isEmpty()) {
                out.append('\n')
                    .append("SLOWER THAN IN THE PREVIOUS LAUNCH (by ")
                    .append(format(SLOWER_THRESHOLD))
                    .append(" s or more)\n")
                    .append(RULE)
                    .append('\n');
                for (String line : slower) {
                    out.append("    ")
                        .append(line)
                        .append('\n');
                }
            }
        }

        List<Step> sortedSteps = new ArrayList<>(steps);
        sortedSteps.sort((a, b) -> Double.compare(b.seconds, a.seconds));
        out.append('\n')
            .append("SLOWEST OTHER STEPS (resources, textures, rendering)\n")
            .append(RULE)
            .append('\n');
        for (int i = 0; i < sortedSteps.size() && i < SHOWN_STEPS; i++) {
            Step step = sortedSteps.get(i);
            out.append(String.format(Locale.ROOT, "%8.2f  %s\n", step.seconds, step.name));
        }
        return out.toString();
    }

    private static void appendStage(StringBuilder out, String name, double[] value) {
        out.append(String.format(Locale.ROOT, "%8.2f  %s", value[0], name));
        if (value[1] > 1) out.append("  (")
            .append((int) value[1])
            .append(" times)");
        out.append('\n');
    }

    private double previousValue(String key) {
        String value = previous.getProperty(key);
        if (value == null) return -1;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String format(double seconds) {
        return String.format(Locale.ROOT, "%.1f", seconds);
    }

    private static String signed(double seconds) {
        double rounded = Math.round(seconds * 10) / 10.0;
        if (rounded == 0) return "0.0";
        return (rounded > 0 ? "+" : "") + String.format(Locale.ROOT, "%.1f", rounded);
    }

    private static final class Step {

        final String name;
        final double seconds;

        Step(String name, double seconds) {
            this.name = name;
            this.seconds = seconds;
        }
    }
}
