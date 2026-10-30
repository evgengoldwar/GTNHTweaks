package gtnhtweaks.modules.buttons.mods;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The numbers of a version: {@code 0.0.24}, {@code v1.2}, {@code 0.0.24-dirty}, or {@code 1-1-0} in a file name.
 * Anything after the first run of numbers is ignored; a version without numbers compares by its text only.
 */
public final class ModVersion implements Comparable<ModVersion> {

    /** At least two numbers, not running straight into letters: a commit hash such as 15221ab is no version. */
    private static final Pattern NUMBERS = Pattern.compile("[vV]?(\\d++(?:[.\\-_]\\d++)++)(?![0-9A-Za-z])");

    public final String text;
    private final int[] parts;

    private ModVersion(String text, int[] parts) {
        this.text = text;
        this.parts = parts;
    }

    /** The version a tag or version string starts with. */
    public static ModVersion parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        Matcher m = NUMBERS.matcher(text);
        if (!m.lookingAt()) return new ModVersion(text, null);
        return fromNumbers(text, m.group(1));
    }

    /** The first version found anywhere in a file name, or null: {@code InfoHUD-1-1-0.jar} is 1.1.0. */
    public static ModVersion inFileName(String fileName) {
        Matcher m = NUMBERS.matcher(fileName);
        for (int from = 0; from < fileName.length() && m.find(from); from = m.start() + 1) {
            // the number must start a part of the name, not end a word such as "mod2"
            int start = m.start();
            if (start == 0 || !Character.isLetterOrDigit(fileName.charAt(start - 1))) {
                ModVersion version = fromNumbers(m.group(1), m.group(1));
                if (version.isParsed()) return version;
            }
        }
        return null;
    }

    private static ModVersion fromNumbers(String text, String numbers) {
        String[] split = numbers.split("[.\\-_]");
        int[] parts = new int[split.length];
        for (int i = 0; i < split.length; i++) {
            try {
                parts[i] = Integer.parseInt(split[i]);
            } catch (NumberFormatException e) {
                return new ModVersion(text, null);
            }
        }
        return new ModVersion(text, parts);
    }

    public boolean isParsed() {
        return parts != null;
    }

    @Override
    public int compareTo(ModVersion other) {
        if (parts == null || other.parts == null) {
            return text.equals(other.text) ? 0 : parts == null ? -1 : 1;
        }
        int n = Math.max(parts.length, other.parts.length);
        for (int i = 0; i < n; i++) {
            int a = i < parts.length ? parts[i] : 0;
            int b = i < other.parts.length ? other.parts[i] : 0;
            if (a != b) return Integer.compare(a, b);
        }
        return 0;
    }

    /** Just the numbers, 0.0.16 for 0.0.16-FixBug.37, or the text when it has none. */
    @Override
    public String toString() {
        if (parts == null) return text.length() > 12 ? text.substring(0, 12) : text;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) out.append('.');
            out.append(parts[i]);
        }
        return out.toString();
    }
}
