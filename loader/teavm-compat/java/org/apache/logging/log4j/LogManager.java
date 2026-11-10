package org.apache.logging.log4j;

import java.util.HashMap;
import java.util.Map;

/** Log4j's LogManager for the browser (added by eagler-modloader): loggers print to the console. */
public final class LogManager {
	public static final String ROOT_LOGGER_NAME = "";
	private static final Map<String, Logger> loggers = new HashMap<>();

	private LogManager() {
	}

	public static synchronized Logger getLogger(String name) {
		String n = name == null ? ROOT_LOGGER_NAME : name;
		Logger l = loggers.get(n);
		if (l == null) {
			l = new Simple(n);
			loggers.put(n, l);
		}
		return l;
	}

	public static Logger getLogger(Class<?> c) { return getLogger(c == null ? null : c.getName()); }
	public static Logger getLogger(Object o) { return getLogger(o == null ? null : o.getClass().getName()); }
	public static Logger getLogger() { return getLogger("mod"); }
	public static Logger getRootLogger() { return getLogger(ROOT_LOGGER_NAME); }
	public static Logger getFormatterLogger(String name) { return getLogger(name); }
	public static Logger getFormatterLogger(Class<?> c) { return getLogger(c); }
	public static Logger getFormatterLogger() { return getLogger(); }
	public static boolean exists(String name) { return loggers.containsKey(name); }

	/** Fills Log4j's {} placeholders; a trailing Throwable is logged with its stack. */
	static void format(Logger logger, Level level, String message, Object[] params) {
		Throwable t = null;
		int n = params == null ? 0 : params.length;
		if (n > 0 && params[n - 1] instanceof Throwable) { t = (Throwable) params[n - 1]; n--; }
		StringBuilder sb = new StringBuilder();
		String m = String.valueOf(message);
		int p = 0, i = 0;
		while (i < m.length()) {
			int j = m.indexOf("{}", i);
			if (j < 0 || p >= n) { sb.append(m, i, m.length()); break; }
			sb.append(m, i, j).append(params[p++]);
			i = j + 2;
		}
		logger.logMessage(level, sb.toString(), t);
	}

	private static final class Simple implements Logger {
		private final String name;
		Simple(String name) { this.name = name; }
		@Override public String getName() { return name; }
		@Override public Level getLevel() { return Level.INFO; }
		@Override public void logMessage(Level level, String message, Throwable t) {
			String s = "[" + level + "] [" + (name.isEmpty() ? "root" : name.substring(name.lastIndexOf('.') + 1)) + "] " + message;
			if (level.isMoreSpecificThan(Level.WARN)) System.err.println(s); else System.out.println(s);
			if (t != null) t.printStackTrace();
		}
	}
}
