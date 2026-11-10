package org.apache.logging.log4j;

/** Log4j's Level for the browser (added by eagler-modloader; see LogManager). */
public final class Level implements Comparable<Level> {
	public static final Level OFF = new Level("OFF", 0), FATAL = new Level("FATAL", 100), ERROR = new Level("ERROR", 200),
			WARN = new Level("WARN", 300), INFO = new Level("INFO", 400), DEBUG = new Level("DEBUG", 500),
			TRACE = new Level("TRACE", 600), ALL = new Level("ALL", Integer.MAX_VALUE);
	private final String name;
	private final int intLevel;

	private Level(String name, int intLevel) { this.name = name; this.intLevel = intLevel; }

	public static Level forName(String name, int intLevel) { Level l = toLevel(name, null); return l != null ? l : new Level(name, intLevel); }
	public static Level getLevel(String name) { return toLevel(name, null); }
	public static Level toLevel(String name) { return toLevel(name, DEBUG); }
	public static Level toLevel(String name, Level def) {
		if (name == null) return def;
		switch (name.toUpperCase()) {
		case "OFF": return OFF; case "FATAL": return FATAL; case "ERROR": return ERROR; case "WARN": return WARN;
		case "INFO": return INFO; case "DEBUG": return DEBUG; case "TRACE": return TRACE; case "ALL": return ALL;
		default: return def;
		}
	}
	public static Level valueOf(String name) { Level l = toLevel(name, null); if (l == null) throw new IllegalArgumentException(name); return l; }
	public static Level[] values() { return new Level[] { OFF, FATAL, ERROR, WARN, INFO, DEBUG, TRACE, ALL }; }

	public String name() { return name; }
	public int intLevel() { return intLevel; }
	public boolean isMoreSpecificThan(Level l) { return intLevel <= l.intLevel; }
	public boolean isLessSpecificThan(Level l) { return intLevel >= l.intLevel; }
	public boolean isInRange(Level min, Level max) { return intLevel >= min.intLevel && intLevel <= max.intLevel; }
	@Override public int compareTo(Level o) { return Integer.compare(intLevel, o.intLevel); }
	@Override public String toString() { return name; }
}
