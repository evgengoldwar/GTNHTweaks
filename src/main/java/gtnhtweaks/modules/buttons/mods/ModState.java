package gtnhtweaks.modules.buttons.mods;

import java.io.File;
import java.util.List;

/** What is known of one mod at the last look at the mods folder and at GitHub. */
public final class ModState {

    public enum Status {
        /** No jar of the mod is in the mods folder. */
        NOT_INSTALLED,
        /** The jar is as new as the newest release, or newer. */
        UP_TO_DATE,
        /** GitHub has a newer release. */
        OUTDATED,
        /** The newest release is not known: not checked yet, no release, or GitHub did not answer. */
        UNKNOWN
    }

    public final OwnMod mod;
    /** The version the game runs now, null if the mod is not loaded. */
    public final String loadedVersion;
    /** The version of the enabled jar in the mods folder, null if there is none. */
    public final ModVersion installed;
    /** The enabled jars of the mod in the mods folder; more than one is a mistake the update fixes. */
    public final List<File> jars;
    /** The jars turned off by an update: a.jar1, a.jar2... */
    public final List<File> disabled;
    /** What waits for the game to close, as text. */
    public final List<String> pending;
    /** The mods folder differs from what the game loaded: the change takes effect after a restart. */
    public final boolean restart;
    public final Release latest;
    /** Why the newest release is not known, or null. */
    public final String error;

    ModState(OwnMod mod, String loadedVersion, ModVersion installed, List<File> jars, List<File> disabled,
        List<String> pending, boolean restart, Release latest, String error) {
        this.mod = mod;
        this.loadedVersion = loadedVersion;
        this.installed = installed;
        this.jars = jars;
        this.disabled = disabled;
        this.pending = pending;
        this.restart = restart;
        this.latest = latest;
        this.error = error;
    }

    public boolean isInstalled() {
        return installed != null;
    }

    public Status status() {
        if (installed == null) return Status.NOT_INSTALLED;
        if (latest == null) return Status.UNKNOWN;
        return installed.compareTo(latest.version) < 0 ? Status.OUTDATED : Status.UP_TO_DATE;
    }
}
