package gtnhtweaks.modules.debug;

import java.io.File;
import java.net.URL;
import java.security.CodeSource;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

/** Finds the mod a class comes from. */
final class ModLookup {

    private ModLookup() {}

    /** "GregTech (gregtech 5.09.54)", "Minecraft" or the name of the jar when no mod owns it. */
    static String describe(Class<?> type) {
        String name = type.getName();
        if (name.startsWith("net.minecraft.")) return "Minecraft";
        if (name.startsWith("net.minecraftforge.") || name.startsWith("cpw.mods.fml.")) return "Forge";

        File source = sourceOf(type);
        ModContainer mod = find(name, source);
        if (mod != null) return mod.getName() + " (" + mod.getModId() + " " + mod.getVersion() + ")";
        return source != null ? "unknown, " + source.getName() : "unknown";
    }

    /** The jar or the class folder the class was loaded from. */
    private static File sourceOf(Class<?> type) {
        try {
            CodeSource code = type.getProtectionDomain()
                .getCodeSource();
            if (code == null || code.getLocation() == null) return null;
            URL location = code.getLocation();
            if (!"file".equals(location.getProtocol())) return null;
            return new File(location.toURI()).getCanonicalFile();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The mod whose jar (or class folder in a development environment) holds the class. When a jar holds several
     * mods, the one whose classes share the longest package with the class wins. Without a match, for example for
     * coremods that report no source, a mod sharing at least two package segments is taken.
     */
    private static ModContainer find(String className, File source) {
        ModContainer best = null;
        int bestScore = -1;
        for (ModContainer mod : Loader.instance()
            .getActiveModList()) {
            if (source == null || !holds(mod.getSource(), source)) continue;
            int score = packageScore(className, mod);
            if (score > bestScore) {
                best = mod;
                bestScore = score;
            }
        }
        if (best != null) return best;

        bestScore = 1;
        for (ModContainer mod : Loader.instance()
            .getActiveModList()) {
            int score = packageScore(className, mod);
            if (score > bestScore) {
                best = mod;
                bestScore = score;
            }
        }
        return best;
    }

    /** A jar holds only its own classes; a folder holds every class under it. */
    private static boolean holds(File modSource, File classSource) {
        if (modSource == null) return false;
        try {
            File root = modSource.getCanonicalFile();
            if (root.equals(classSource)) return true;
            return root.isDirectory() && classSource.getPath()
                .startsWith(root.getPath() + File.separator);
        } catch (Exception e) {
            return false;
        }
    }

    /** Longest package the class shares with the mod class or, for coremods, the container class of the mod. */
    private static int packageScore(String className, ModContainer mod) {
        int score = 0;
        Object instance = mod.getMod();
        if (instance != null) score = commonPackage(
            className,
            instance.getClass()
                .getName());
        String container = mod.getClass()
            .getName();
        if (!container.startsWith("cpw.mods.fml.")) score = Math.max(score, commonPackage(className, container));
        return score;
    }

    /** Number of package segments the two class names share. */
    private static int commonPackage(String a, String b) {
        String[] left = a.split("\\.");
        String[] right = b.split("\\.");
        int n = 0;
        while (n < left.length - 1 && n < right.length - 1 && left[n].equals(right[n])) n++;
        return n;
    }
}
