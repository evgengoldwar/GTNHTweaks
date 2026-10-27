package gtnhtweaks.modules.debug;

/**
 * Target of the mixin that cannot be applied (see crashTest in debug.cfg). Loading this class fails with the mixin
 * error, so the moment of the test crash is the moment {@link #load} is first called.
 */
public final class CrashTestTarget {

    private CrashTestTarget() {}

    public static void load() {}
}
