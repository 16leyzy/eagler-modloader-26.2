package oshi.util.tuples;

/** OSHI's Triplet for the browser (added by eagler-modloader): libraries use it as a plain tuple. */
public final class Triplet<A, B, C> {
	private final A a;
	private final B b;
	private final C c;

	public Triplet(A a, B b, C c) {
		this.a = a;
		this.b = b;
		this.c = c;
	}

	public final A getA() { return a; }
	public final B getB() { return b; }
	public final C getC() { return c; }

	@Override public String toString() { return "(" + a + ", " + b + ", " + c + ")"; }
}
