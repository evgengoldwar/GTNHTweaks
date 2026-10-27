package gtnhtweaks.modules.logs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import net.minecraftforge.common.ForgeVersion;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.LoaderState.ModState;
import cpw.mods.fml.common.ModContainer;
import gtnhtweaks.core.ModuleManager;

/**
 * Summary of the game setup: versions of Java, Minecraft and Forge, memory, graphics card and the mod list with the
 * changes since the previous launch. Useful to attach to a bug report together with the Crash log.
 */
final class ModsReport {

    private static final String RULE = "----------------------------------------------------------------------";

    private static final List<String[]> MODS = new ArrayList<>();
    private static String added = "";
    private static String removed = "";
    private static String updated = "";
    private static boolean hasPrevious;
    private static volatile String graphics;

    private ModsReport() {}

    /** Reads the mod list once all mods are loaded and compares it with the previous launch. */
    static synchronized void collectMods() {
        MODS.clear();
        Map<String, String> current = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (ModContainer mod : Loader.instance()
            .getModList()) {
            String file = mod.getSource() == null ? ""
                : mod.getSource()
                    .getName();
            String state = "";
            try {
                ModState modState = Loader.instance()
                    .getModState(mod);
                if (modState == ModState.DISABLED || modState == ModState.ERRORED) state = modState.name();
            } catch (RuntimeException ignored) {}
            MODS.add(new String[] { mod.getName(), mod.getModId(), mod.getVersion(), file, state });
            current.put(mod.getModId(), mod.getVersion());
        }
        MODS.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));

        File stats = new File(ModuleManager.getConfigDir(), "logs-mods.properties");
        Properties previous = new Properties();
        hasPrevious = false;
        if (stats.isFile()) {
            try (InputStream in = new FileInputStream(stats)) {
                previous.load(in);
                hasPrevious = true;
            } catch (IOException | IllegalArgumentException ignored) {}
        }
        if (hasPrevious) {
            StringBuilder addedText = new StringBuilder();
            StringBuilder updatedText = new StringBuilder();
            StringBuilder removedText = new StringBuilder();
            for (Map.Entry<String, String> mod : current.entrySet()) {
                String before = previous.getProperty(mod.getKey());
                if (before == null) {
                    addedText.append("    ")
                        .append(mod.getKey())
                        .append(' ')
                        .append(mod.getValue())
                        .append('\n');
                } else if (!before.equals(mod.getValue())) {
                    updatedText.append("    ")
                        .append(mod.getKey())
                        .append(' ')
                        .append(before)
                        .append(" -> ")
                        .append(mod.getValue())
                        .append('\n');
                }
            }
            Map<String, String> old = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (String id : previous.stringPropertyNames()) old.put(id, previous.getProperty(id));
            for (Map.Entry<String, String> mod : old.entrySet()) {
                if (!current.containsKey(mod.getKey())) {
                    removedText.append("    ")
                        .append(mod.getKey())
                        .append(' ')
                        .append(mod.getValue())
                        .append('\n');
                }
            }
            added = addedText.toString();
            updated = updatedText.toString();
            removed = removedText.toString();
        }

        Properties save = new Properties();
        save.putAll(current);
        try (OutputStream out = new FileOutputStream(stats)) {
            save.store(out, "GTNHTweaks: mods of the previous launch");
        } catch (IOException ignored) {}
    }

    /** Graphics card and driver, known once the client has its OpenGL context. */
    static void setGraphics(String text) {
        graphics = text;
    }

    static synchronized void write() {
        LogFiles.writeReport(LogFiles.KIND_MODS, render());
    }

    private static String render() {
        StringBuilder out = new StringBuilder(16384);
        out.append("GTNHTweaks - GAME SETUP AND MODS\n");
        out.append("Session started ")
            .append(LogFiles.sessionText())
            .append('\n');

        out.append('\n')
            .append("ENVIRONMENT\n")
            .append(RULE)
            .append('\n');
        line(
            out,
            "Side",
            FMLCommonHandler.instance()
                .getSide()
                .isClient() ? "client" : "server");
        line(out, "Minecraft", Loader.MC_VERSION);
        line(out, "Forge", ForgeVersion.getVersion());
        line(
            out,
            "Java",
            System.getProperty("java.version") + " ("
                + System.getProperty("java.vm.name")
                + ", "
                + System.getProperty("java.vendor")
                + ")");
        line(
            out,
            "System",
            System.getProperty("os.name") + " "
                + System.getProperty("os.version")
                + " "
                + System.getProperty("os.arch"));
        line(
            out,
            "CPU threads",
            String.valueOf(
                Runtime.getRuntime()
                    .availableProcessors()));
        line(
            out,
            "Max memory",
            String.format(
                Locale.ROOT,
                "%d MB",
                Runtime.getRuntime()
                    .maxMemory() / (1024 * 1024)));
        line(out, "JVM memory/GC args", memoryArguments());
        if (graphics != null) line(out, "Graphics", graphics);

        if (hasPrevious) {
            out.append('\n')
                .append("CHANGES SINCE THE PREVIOUS LAUNCH\n")
                .append(RULE)
                .append('\n');
            if (added.isEmpty() && removed.isEmpty() && updated.isEmpty()) {
                out.append("    none\n");
            } else {
                if (!added.isEmpty()) out.append("  Added\n")
                    .append(added);
                if (!removed.isEmpty()) out.append("  Removed\n")
                    .append(removed);
                if (!updated.isEmpty()) out.append("  Updated\n")
                    .append(updated);
            }
        } else {
            out.append("\nNo previous launch to compare the mod list with.\n");
        }

        int nameWidth = 4;
        int idWidth = 5;
        int versionWidth = 7;
        for (String[] mod : MODS) {
            nameWidth = Math.max(nameWidth, Math.min(40, mod[0].length()));
            idWidth = Math.max(idWidth, Math.min(32, mod[1].length()));
            versionWidth = Math.max(versionWidth, Math.min(32, mod[2].length()));
        }
        String format = "%-" + nameWidth + "s  %-" + idWidth + "s  %-" + versionWidth + "s  %s\n";
        out.append('\n')
            .append("MODS (")
            .append(MODS.size())
            .append(")\n")
            .append(RULE)
            .append('\n');
        out.append(String.format(Locale.ROOT, format, "Name", "Mod id", "Version", "File"));
        for (String[] mod : MODS) {
            String file = mod[4].isEmpty() ? mod[3] : mod[3] + "  [" + mod[4] + "]";
            out.append(String.format(Locale.ROOT, format, mod[0], mod[1], mod[2], file));
        }
        return out.toString();
    }

    private static void line(StringBuilder out, String name, String value) {
        out.append(String.format(Locale.ROOT, "%-20s %s\n", name + ":", value));
    }

    /** Only the memory and garbage collector options: other arguments can hold paths and tokens. */
    private static String memoryArguments() {
        List<String> shown = new ArrayList<>();
        try {
            for (String argument : ManagementFactory.getRuntimeMXBean()
                .getInputArguments()) {
                if (argument.startsWith("-Xm") || argument.startsWith("-Xs")
                    || (argument.startsWith("-XX:") && (argument.contains("GC") || argument.contains("Heap")))) {
                    shown.add(argument);
                }
            }
        } catch (RuntimeException ignored) {}
        return shown.isEmpty() ? "none" : String.join(" ", shown);
    }
}
