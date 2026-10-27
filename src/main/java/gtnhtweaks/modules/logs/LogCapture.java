package gtnhtweaks.modules.logs;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

/** Log4j appender that receives every message of the game and hands it to {@link LogFiles}. */
final class LogCapture extends AbstractAppender {

    private static final String NAME = "GTNHTweaksLogs";

    private LogCapture() {
        super(NAME, null, null, true);
    }

    /** Adds the appender to the root logger and to every logger that does not pass messages on to the root. */
    static void install() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();
        LogCapture appender = new LogCapture();
        appender.start();
        LoggerConfig root = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
        root.addAppender(appender, Level.ALL, null);
        for (LoggerConfig logger : config.getLoggers()
            .values()) {
            if (logger != root && !logger.isAdditive()) logger.addAppender(appender, Level.ALL, null);
        }
        context.updateLoggers();
        flushOtherAppenders(config);
    }

    /** Makes the log file appenders write out their buffers, so the earlier log can be read completely. */
    private static void flushOtherAppenders(Configuration config) {
        for (Appender appender : config.getAppenders()
            .values()) {
            try {
                Method getManager = findMethod(appender.getClass(), "getManager");
                if (getManager == null) continue;
                getManager.setAccessible(true);
                Object manager = getManager.invoke(appender);
                Method flush = manager == null ? null : findMethod(manager.getClass(), "flush");
                if (flush == null) continue;
                flush.setAccessible(true);
                flush.invoke(manager);
            } catch (Throwable ignored) {
                // Only a best effort: the last lines may then be missing from the copy
            }
        }
    }

    private static Method findMethod(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

    @Override
    public void append(LogEvent event) {
        try {
            long time;
            try {
                time = event.getMillis();
            } catch (Throwable t) {
                time = System.currentTimeMillis();
            }
            String level = event.getLevel() == null ? "INFO"
                : event.getLevel()
                    .name();
            String thread = event.getThreadName();
            String logger = event.getLoggerName() == null ? "" : event.getLoggerName();
            String mod = "";
            try {
                Map<String, String> context = event.getContextMap();
                if (context != null && context.get("mod") != null) mod = context.get("mod");
            } catch (Throwable ignored) {}
            String message = event.getMessage() == null ? ""
                : event.getMessage()
                    .getFormattedMessage();
            Throwable thrown = event.getThrown();

            List<String> trace = null;
            // The short trace is only needed for messages that can end up in the errors or warnings
            if (thrown != null && !"DEBUG".equals(level) && !"TRACE".equals(level)) {
                trace = StackTraces.lines(thrown);
            }
            String allText = null;
            if (LogsModule.writeAll) {
                StringBuilder line = new StringBuilder(message.length() + 64);
                line.append('[')
                    .append(LogFiles.time(time))
                    .append("] [")
                    .append(thread)
                    .append('/')
                    .append(level)
                    .append("] [")
                    .append(logger)
                    .append('/')
                    .append(mod)
                    .append("]: ")
                    .append(message)
                    .append('\n');
                if (thrown != null) line.append(StackTraces.full(thrown));
                allText = line.toString();
            }
            LogFiles.onRecord(new LogRecord(time, level, thread, logger, mod, message, trace), allText);
        } catch (Throwable t) {
            // Never break the logging of the game
        }
    }
}
