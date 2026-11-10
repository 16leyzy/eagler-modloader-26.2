package org.apache.logging.log4j;

/**
 * Log4j's Logger for the browser (added by eagler-modloader). Minecraft logs through SLF4J, but mods
 * often use Log4j directly; this prints to the browser console. Messages use Log4j's {} placeholders.
 */
public interface Logger {
	String getName();
	Level getLevel();
	void logMessage(Level level, String message, Throwable t);

	default boolean isEnabled(Level level) { return level.isMoreSpecificThan(getLevel()); }
	default boolean isEnabled(Level level, Marker marker) { return isEnabled(level); }
	default void log(Level level, String message) { if (isEnabled(level)) logMessage(level, message, null); }
	default void log(Level level, Object message) { if (isEnabled(level)) logMessage(level, String.valueOf(message), null); }
	default void log(Level level, String message, Throwable t) { if (isEnabled(level)) logMessage(level, message, t); }
	default void log(Level level, String message, Object p0) { if (isEnabled(level)) LogManager.format(this, level, message, new Object[] { p0 }); }
	default void log(Level level, String message, Object p0, Object p1) { if (isEnabled(level)) LogManager.format(this, level, message, new Object[] { p0, p1 }); }
	default void log(Level level, String message, Object... params) { if (isEnabled(level)) LogManager.format(this, level, message, params); }
	default void log(Level level, Marker marker, String message) { log(level, message); }
	default void log(Level level, Marker marker, String message, Object... params) { log(level, message, params); }
	default void printf(Level level, String format, Object... params) { if (isEnabled(level)) logMessage(level, String.format(format, params), null); }
	default void catching(Throwable t) { logMessage(Level.ERROR, "Catching", t); }
	default void catching(Level level, Throwable t) { logMessage(level, "Catching", t); }
	default <T extends Throwable> T throwing(T t) { logMessage(Level.ERROR, "Throwing", t); return t; }

	default boolean isTraceEnabled() { return isEnabled(Level.TRACE); }
	default boolean isTraceEnabled(Marker marker) { return isEnabled(Level.TRACE); }
	default void trace(String message) { log(Level.TRACE, message); }
	default void trace(Object message) { log(Level.TRACE, message); }
	default void trace(CharSequence message) { log(Level.TRACE, String.valueOf(message)); }
	default void trace(String message, Throwable t) { log(Level.TRACE, message, t); }
	default void trace(Object message, Throwable t) { log(Level.TRACE, String.valueOf(message), t); }
	default void trace(String message, Object p0) { log(Level.TRACE, message, p0); }
	default void trace(String message, Object p0, Object p1) { log(Level.TRACE, message, p0, p1); }
	default void trace(String message, Object p0, Object p1, Object p2) { log(Level.TRACE, message, new Object[] { p0, p1, p2 }); }
	default void trace(String message, Object p0, Object p1, Object p2, Object p3) { log(Level.TRACE, message, new Object[] { p0, p1, p2, p3 }); }
	default void trace(String message, Object... params) { log(Level.TRACE, message, params); }
	default void trace(Marker marker, String message) { log(Level.TRACE, message); }
	default void trace(Marker marker, String message, Throwable t) { log(Level.TRACE, message, t); }
	default void trace(Marker marker, String message, Object p0) { log(Level.TRACE, message, p0); }
	default void trace(Marker marker, String message, Object p0, Object p1) { log(Level.TRACE, message, p0, p1); }
	default void trace(Marker marker, String message, Object... params) { log(Level.TRACE, message, params); }

	default boolean isDebugEnabled() { return isEnabled(Level.DEBUG); }
	default boolean isDebugEnabled(Marker marker) { return isEnabled(Level.DEBUG); }
	default void debug(String message) { log(Level.DEBUG, message); }
	default void debug(Object message) { log(Level.DEBUG, message); }
	default void debug(CharSequence message) { log(Level.DEBUG, String.valueOf(message)); }
	default void debug(String message, Throwable t) { log(Level.DEBUG, message, t); }
	default void debug(Object message, Throwable t) { log(Level.DEBUG, String.valueOf(message), t); }
	default void debug(String message, Object p0) { log(Level.DEBUG, message, p0); }
	default void debug(String message, Object p0, Object p1) { log(Level.DEBUG, message, p0, p1); }
	default void debug(String message, Object p0, Object p1, Object p2) { log(Level.DEBUG, message, new Object[] { p0, p1, p2 }); }
	default void debug(String message, Object p0, Object p1, Object p2, Object p3) { log(Level.DEBUG, message, new Object[] { p0, p1, p2, p3 }); }
	default void debug(String message, Object... params) { log(Level.DEBUG, message, params); }
	default void debug(Marker marker, String message) { log(Level.DEBUG, message); }
	default void debug(Marker marker, String message, Throwable t) { log(Level.DEBUG, message, t); }
	default void debug(Marker marker, String message, Object p0) { log(Level.DEBUG, message, p0); }
	default void debug(Marker marker, String message, Object p0, Object p1) { log(Level.DEBUG, message, p0, p1); }
	default void debug(Marker marker, String message, Object... params) { log(Level.DEBUG, message, params); }

	default boolean isInfoEnabled() { return isEnabled(Level.INFO); }
	default boolean isInfoEnabled(Marker marker) { return isEnabled(Level.INFO); }
	default void info(String message) { log(Level.INFO, message); }
	default void info(Object message) { log(Level.INFO, message); }
	default void info(CharSequence message) { log(Level.INFO, String.valueOf(message)); }
	default void info(String message, Throwable t) { log(Level.INFO, message, t); }
	default void info(Object message, Throwable t) { log(Level.INFO, String.valueOf(message), t); }
	default void info(String message, Object p0) { log(Level.INFO, message, p0); }
	default void info(String message, Object p0, Object p1) { log(Level.INFO, message, p0, p1); }
	default void info(String message, Object p0, Object p1, Object p2) { log(Level.INFO, message, new Object[] { p0, p1, p2 }); }
	default void info(String message, Object p0, Object p1, Object p2, Object p3) { log(Level.INFO, message, new Object[] { p0, p1, p2, p3 }); }
	default void info(String message, Object... params) { log(Level.INFO, message, params); }
	default void info(Marker marker, String message) { log(Level.INFO, message); }
	default void info(Marker marker, String message, Throwable t) { log(Level.INFO, message, t); }
	default void info(Marker marker, String message, Object p0) { log(Level.INFO, message, p0); }
	default void info(Marker marker, String message, Object p0, Object p1) { log(Level.INFO, message, p0, p1); }
	default void info(Marker marker, String message, Object... params) { log(Level.INFO, message, params); }

	default boolean isWarnEnabled() { return isEnabled(Level.WARN); }
	default boolean isWarnEnabled(Marker marker) { return isEnabled(Level.WARN); }
	default void warn(String message) { log(Level.WARN, message); }
	default void warn(Object message) { log(Level.WARN, message); }
	default void warn(CharSequence message) { log(Level.WARN, String.valueOf(message)); }
	default void warn(String message, Throwable t) { log(Level.WARN, message, t); }
	default void warn(Object message, Throwable t) { log(Level.WARN, String.valueOf(message), t); }
	default void warn(String message, Object p0) { log(Level.WARN, message, p0); }
	default void warn(String message, Object p0, Object p1) { log(Level.WARN, message, p0, p1); }
	default void warn(String message, Object p0, Object p1, Object p2) { log(Level.WARN, message, new Object[] { p0, p1, p2 }); }
	default void warn(String message, Object p0, Object p1, Object p2, Object p3) { log(Level.WARN, message, new Object[] { p0, p1, p2, p3 }); }
	default void warn(String message, Object... params) { log(Level.WARN, message, params); }
	default void warn(Marker marker, String message) { log(Level.WARN, message); }
	default void warn(Marker marker, String message, Throwable t) { log(Level.WARN, message, t); }
	default void warn(Marker marker, String message, Object p0) { log(Level.WARN, message, p0); }
	default void warn(Marker marker, String message, Object p0, Object p1) { log(Level.WARN, message, p0, p1); }
	default void warn(Marker marker, String message, Object... params) { log(Level.WARN, message, params); }

	default boolean isErrorEnabled() { return isEnabled(Level.ERROR); }
	default boolean isErrorEnabled(Marker marker) { return isEnabled(Level.ERROR); }
	default void error(String message) { log(Level.ERROR, message); }
	default void error(Object message) { log(Level.ERROR, message); }
	default void error(CharSequence message) { log(Level.ERROR, String.valueOf(message)); }
	default void error(String message, Throwable t) { log(Level.ERROR, message, t); }
	default void error(Object message, Throwable t) { log(Level.ERROR, String.valueOf(message), t); }
	default void error(String message, Object p0) { log(Level.ERROR, message, p0); }
	default void error(String message, Object p0, Object p1) { log(Level.ERROR, message, p0, p1); }
	default void error(String message, Object p0, Object p1, Object p2) { log(Level.ERROR, message, new Object[] { p0, p1, p2 }); }
	default void error(String message, Object p0, Object p1, Object p2, Object p3) { log(Level.ERROR, message, new Object[] { p0, p1, p2, p3 }); }
	default void error(String message, Object... params) { log(Level.ERROR, message, params); }
	default void error(Marker marker, String message) { log(Level.ERROR, message); }
	default void error(Marker marker, String message, Throwable t) { log(Level.ERROR, message, t); }
	default void error(Marker marker, String message, Object p0) { log(Level.ERROR, message, p0); }
	default void error(Marker marker, String message, Object p0, Object p1) { log(Level.ERROR, message, p0, p1); }
	default void error(Marker marker, String message, Object... params) { log(Level.ERROR, message, params); }

	default boolean isFatalEnabled() { return isEnabled(Level.FATAL); }
	default boolean isFatalEnabled(Marker marker) { return isEnabled(Level.FATAL); }
	default void fatal(String message) { log(Level.FATAL, message); }
	default void fatal(Object message) { log(Level.FATAL, message); }
	default void fatal(CharSequence message) { log(Level.FATAL, String.valueOf(message)); }
	default void fatal(String message, Throwable t) { log(Level.FATAL, message, t); }
	default void fatal(Object message, Throwable t) { log(Level.FATAL, String.valueOf(message), t); }
	default void fatal(String message, Object p0) { log(Level.FATAL, message, p0); }
	default void fatal(String message, Object p0, Object p1) { log(Level.FATAL, message, p0, p1); }
	default void fatal(String message, Object p0, Object p1, Object p2) { log(Level.FATAL, message, new Object[] { p0, p1, p2 }); }
	default void fatal(String message, Object p0, Object p1, Object p2, Object p3) { log(Level.FATAL, message, new Object[] { p0, p1, p2, p3 }); }
	default void fatal(String message, Object... params) { log(Level.FATAL, message, params); }
	default void fatal(Marker marker, String message) { log(Level.FATAL, message); }
	default void fatal(Marker marker, String message, Throwable t) { log(Level.FATAL, message, t); }
	default void fatal(Marker marker, String message, Object p0) { log(Level.FATAL, message, p0); }
	default void fatal(Marker marker, String message, Object p0, Object p1) { log(Level.FATAL, message, p0, p1); }
	default void fatal(Marker marker, String message, Object... params) { log(Level.FATAL, message, params); }
}
