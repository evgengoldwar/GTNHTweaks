package gtnhtweaks.modules.logs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The problem behind a crash in plain words, found from the kind of exception anywhere in the chain of causes: a mixin
 * that could not be applied, a missing class, mods of versions that do not fit, out of memory. Plain Java only, so it
 * also works for crashes before Minecraft classes can be loaded.
 */
public final class CrashDiagnosis {

    private static final Pattern MIXIN_NAME = Pattern.compile("Mixin \\[([^\\]]+)\\]");
    private static final Pattern MIXIN_CONFIG = Pattern.compile("config \\[([^\\]]+)\\]");

    public final String headline;
    public final List<String> lines = new ArrayList<>();

    public CrashDiagnosis(String headline) {
        this.headline = headline;
    }

    /** The headline in capitals, then the explanation lines. */
    public void appendTo(StringBuilder out) {
        out.append(headline.toUpperCase(Locale.ROOT))
            .append('\n');
        for (String line : lines) out.append(line)
            .append('\n');
    }

    public static CrashDiagnosis of(Throwable crash) {
        List<Throwable> chain = new ArrayList<>();
        for (Throwable t = crash; t != null && chain.size() < 30 && !chain.contains(t); t = t.getCause()) chain.add(t);

        // the deepest mixin error tells the most
        Throwable mixin = null;
        for (Throwable t : chain) {
            if (t.getClass()
                .getName()
                .startsWith("org.spongepowered.asm.mixin")) mixin = t;
        }
        if (mixin != null) {
            // the apply error names the mixin and its config, the deepest one tells why
            String named = "";
            for (Throwable t : chain) {
                if (MIXIN_NAME.matcher(String.valueOf(t.getMessage()))
                    .find()) named = String.valueOf(t.getMessage());
            }
            CrashDiagnosis d = new CrashDiagnosis("A mixin could not be applied");
            d.lines.add("A mod changes the code of Minecraft or of another mod with a mixin, and the code it targets");
            d.lines.add("is not what it expects. Usually two mods have versions that do not fit together.");
            String message = String.valueOf(mixin.getMessage());
            Matcher name = MIXIN_NAME.matcher(named.isEmpty() ? message : named);
            Matcher config = MIXIN_CONFIG.matcher(named.isEmpty() ? message : named);
            if (name.find()) d.lines.add("Mixin  : " + name.group(1));
            if (config.find()) d.lines.add("Config : " + config.group(1) + "  (its mod is the one to update)");
            d.lines.add(
                "Error  : " + mixin.getClass()
                    .getSimpleName() + ": " + message);
            return d;
        }
        for (Throwable t : chain) {
            if (t instanceof NoClassDefFoundError || t instanceof ClassNotFoundException) {
                CrashDiagnosis d = new CrashDiagnosis("A class could not be found");
                String missing = String.valueOf(t.getMessage())
                    .replace('/', '.');
                d.lines.add("Missing class : " + missing);
                d.lines.add("A mod uses a class of another mod or library that is not installed, or that has a");
                d.lines.add("version without this class. The mod that needs it is in the stack trace below.");
                return d;
            }
            if (t instanceof NoSuchMethodError || t instanceof NoSuchFieldError
                || t instanceof AbstractMethodError
                || t instanceof IncompatibleClassChangeError) {
                CrashDiagnosis d = new CrashDiagnosis("Mods of versions that do not fit together");
                d.lines.add("Missing : " + t.getMessage());
                d.lines.add("A mod calls code of another mod that does not exist in the installed version.");
                d.lines.add("Update both mods to versions made for each other.");
                return d;
            }
            if (t instanceof OutOfMemoryError) {
                CrashDiagnosis d = new CrashDiagnosis("The game ran out of memory");
                d.lines.add("Give the game more memory (the -Xmx Java argument of the launcher, 6-8 GB for GTNH),");
                d.lines.add("or lower the render distance and close large bases.");
                return d;
            }
        }
        Throwable root = chain.isEmpty() ? crash : chain.get(chain.size() - 1);
        CrashDiagnosis d = new CrashDiagnosis("Unexpected error");
        d.lines.add("Root cause : " + root);
        return d;
    }
}
