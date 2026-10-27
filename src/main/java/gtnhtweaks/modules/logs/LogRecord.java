package gtnhtweaks.modules.logs;

import java.util.List;

/** One message of the log, already detached from log4j. */
final class LogRecord {

    final long time;
    final String level;
    final String thread;
    final String logger;
    /** Mod that was being loaded when the message was logged, empty when unknown. */
    final String mod;
    final String message;
    /** Stack trace lines of the attached exception, {@code null} without one. */
    final List<String> trace;

    LogRecord(long time, String level, String thread, String logger, String mod, String message, List<String> trace) {
        this.time = time;
        this.level = level;
        this.thread = thread;
        this.logger = logger;
        this.mod = mod == null ? "" : mod;
        this.message = message == null ? "" : message;
        this.trace = trace;
    }

    /** Where the message came from: the logger, and for System.out/System.err also the mod. */
    String source() {
        if (("STDERR".equals(logger) || "STDOUT".equals(logger)) && !mod.isEmpty()) return logger + " / " + mod;
        return logger.isEmpty() ? "(root)" : logger;
    }
}
