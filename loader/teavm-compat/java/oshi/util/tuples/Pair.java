package oshi.util.tuples;

/** OSHI's Pair for the browser (added by eagler-modloader): libraries use it as a plain tuple. */
public final class Pair<A, B> {
	private final A a;
	private final B b;

	public Pair(A a, B b) {
		this.a = a;
		this.b = b;
	}

	public final A getA() { return a; }
	public final B getB() { return b; }

	@Override public String toString() { return "(" + a + ", " + b + ")"; }
}
