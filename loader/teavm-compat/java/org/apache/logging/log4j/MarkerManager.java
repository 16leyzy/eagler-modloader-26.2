package org.apache.logging.log4j;

/** Log4j's MarkerManager for the browser (added by eagler-modloader). */
public final class MarkerManager {
	private MarkerManager() {
	}

	public static Marker getMarker(final String name) {
		return new Marker() {
			@Override public String getName() { return name; }
			@Override public String toString() { return name; }
		};
	}
}
