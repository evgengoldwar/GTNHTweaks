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
     * The mod whose source holds the class. When a jar holds several mods, the one whose main class shares the
     * longest package with the class wins; without a source only the package is compared.
     */
    private static ModContainer find(String className, File source) {
        ModContainer best = null;
        int bestScore = -1;
        for (ModContainer mod : Loader.instance()
            .getActiveModList()) {
            if (source != null && !contains(mod.getSource(), source)) continue;
            Object instance = mod.getMod();
            int score = instance == null ? 0
                : commonPackage(
                    className,
                    instance.getClass()
                        .getName());
            if (source == null && score == 0) continue;
            if (score > bestScore) {
                best = mod;
                bestScore = score;
            }
        }
        return best;
    }

    private static boolean contains(File modSource, File classSource) {
        if (modSource == null) return false;
        try {
            String root = modSource.getCanonicalPath();
            String path = classSource.getPath();
            return path.equals(root) || path.startsWith(root + File.separator);
        } catch (Exception e) {
            return false;
        }
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
