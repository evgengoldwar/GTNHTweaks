package gtnhtweaks.modules.buttons.mods;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Changes to the mods folder that cannot be made while the game runs: on Windows a loaded jar can be neither renamed
 * nor deleted. They are kept in order in a file and made by a small process started when the game closes, which waits
 * for the jars to be let go, and again early in the next start.
 * <p>
 * Plain Java only: the process that runs {@link #main} has neither Minecraft nor Forge.
 */
public final class PendingFiles {

    /** Renames a jar to the same name with the first free number after it: a.jar to a.jar1, else a.jar2... */
    static final String DISABLE = "DISABLE";
    /** Renames a file to another name; waits while the other name is taken. */
    static final String MOVE = "MOVE";
    static final String DELETE = "DELETE";

    private static final String QUEUE = "pending-mod-files.txt";

    /** How long the process after the game waits for the jars to be let go. */
    private static final long WAIT_MS = 120_000;

    private PendingFiles() {}

    /** One change: what to do, the file, and the new name for {@link #MOVE}. */
    static final class Change {

        final String action;
        final File file;
        final File target;

        Change(String action, File file, File target) {
            this.action = action;
            this.file = file;
            this.target = target;
        }

        String line() {
            return action + "\t" + file.getPath() + (target == null ? "" : "\t" + target.getPath());
        }

        static Change parse(String line) {
            String[] parts = line.split("\t");
            if (parts.length < 2) return null;
            return new Change(parts[0], new File(parts[1]), parts.length > 2 ? new File(parts[2]) : null);
        }
    }

    /** The file of the waiting changes, in the config folder of GTNHTweaks. */
    public static File queue(File configDir) {
        return new File(configDir, QUEUE);
    }

    static synchronized List<Change> read(File queue) {
        List<Change> changes = new ArrayList<>();
        if (!queue.isFile()) return changes;
        try {
            for (String line : Files.readAllLines(queue.toPath(), StandardCharsets.UTF_8)) {
                Change change = Change.parse(line);
                if (change != null) changes.add(change);
            }
        } catch (IOException e) {
            return changes;
        }
        return changes;
    }

    private static void write(File queue, List<Change> changes) throws IOException {
        if (changes.isEmpty()) {
            Files.deleteIfExists(queue.toPath());
            return;
        }
        List<String> lines = new ArrayList<>();
        for (Change change : changes) lines.add(change.line());
        File parent = queue.getParentFile();
        if (parent != null) parent.mkdirs();
        Files.write(queue.toPath(), lines, StandardCharsets.UTF_8);
    }

    static synchronized void add(File queue, Change change) throws IOException {
        List<Change> changes = read(queue);
        changes.add(change);
        write(queue, changes);
    }

    /**
     * Makes the changes in order and forgets the ones made; stops at the first one that cannot be made yet, so a new
     * jar never joins an old one still enabled.
     *
     * @return true if no change is left
     */
    public static synchronized boolean applyAll(File queue) {
        List<Change> changes = read(queue);
        if (changes.isEmpty()) return true;
        List<Change> left = new ArrayList<>(changes);
        for (Change change : changes) {
            if (!apply(change)) break;
            left.remove(0);
        }
        try {
            write(queue, left);
        } catch (IOException e) {
            return false;
        }
        return left.isEmpty();
    }

    private static boolean apply(Change change) {
        File file = change.file;
        // already gone, by hand or by an earlier try
        if (!file.exists()) return true;
        if (DELETE.equals(change.action)) return file.delete();
        if (DISABLE.equals(change.action)) return file.renameTo(disabledName(file));
        if (MOVE.equals(change.action))
            return change.target != null && !change.target.exists() && file.renameTo(change.target);
        // an unknown action from a newer version is left alone
        return true;
    }

    /** The name a jar gets when it is turned off: its name with the first number not taken after it. */
    static File disabledName(File jar) {
        for (int n = 1;; n++) {
            File disabled = new File(jar.getParentFile(), jar.getName() + n);
            if (!disabled.exists()) return disabled;
        }
    }

    /** The process started when the game closes: {@code PendingFiles <queue file>}. */
    public static void main(String[] args) throws InterruptedException {
        if (args.length < 1) return;
        File queue = new File(args[0]);
        long end = System.currentTimeMillis() + WAIT_MS;
        while (!applyAll(queue) && System.currentTimeMillis() < end) Thread.sleep(1000);
    }
}
