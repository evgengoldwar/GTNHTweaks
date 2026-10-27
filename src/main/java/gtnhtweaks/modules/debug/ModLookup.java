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

    /**
     * The jar or the class folder the class was loaded from: its code source, or where its class file is found when
     * the class loader gives none (RetroFuturaBootstrap in GTNH does not).
     */
    private static File sourceOf(Class<?> type) {
        try {
            CodeSource code = type.getProtectionDomain()
                .getCodeSource();
            URL location = code != null ? code.getLocation() : null;
            if (location != null && "file".equals(location.getProtocol())) {
                return new File(location.toURI()).getCanonicalFile();
            }
        } catch (Exception ignored) {}
        try {
            ClassLoader loader = type.getClassLoader();
            URL resource = loader == null ? null
                : loader.getResource(
                    type.getName()
                        .replace('.', '/') + ".class");
            if (resource == null) return null;
            String url = resource.toString();
            if (url.startsWith("jar:")) {
                String jar = url.substring(4, url.indexOf("!/"));
                return new File(new URL(jar).toURI()).getCanonicalFile();
            }
            if ("file".equals(resource.getProtocol())) return new File(resource.toURI()).getCanonicalFile();
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * The mod whose jar (or class folder in a development environment) holds the class. When a jar holds several
     * mods, the one whose classes share the longest package with the class wins. Without a match, for example for
     * coremods that report no source, the mod whose package holds the class is taken, or one sharing at least two
     * package segments with it.
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

        // inside the package of a mod, or sharing at least two package segments with it
        bestScore = 2;
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

    /**
     * How well the package of the class fits the mod: high when the class lies inside the package of the mod class
     * (or, for coremods, of the container class), lower for a partly shared package.
     */
    private static int packageScore(String className, ModContainer mod) {
        int score = 0;
        Object instance = mod.getMod();
        if (instance != null) score = packageScore(
            className,
            instance.getClass()
                .getName());
        String container = mod.getClass()
            .getName();
        if (!container.startsWith("cpw.mods.fml.")) score = Math.max(score, packageScore(className, container));
        return score;
    }

    private static int packageScore(String className, String ownerName) {
        int dot = ownerName.lastIndexOf('.');
        if (dot > 0) {
            String ownerPackage = ownerName.substring(0, dot);
            if (className.startsWith(ownerPackage + ".")) return ownerPackage.split("\\.").length * 2 + 1;
        }
        return commonPackage(className, ownerName) * 2;
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
