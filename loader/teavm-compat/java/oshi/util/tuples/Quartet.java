package oshi.util.tuples;

/** OSHI's Quartet for the browser (added by eagler-modloader): libraries use it as a plain tuple. */
public final class Quartet<A, B, C, D> {
	private final A a;
	private final B b;
	private final C c;
	private final D d;

	public Quartet(A a, B b, C c, D d) {
		this.a = a;
		this.b = b;
		this.c = c;
		this.d = d;
	}

	public final A getA() { return a; }
	public final B getB() { return b; }
	public final C getC() { return c; }
	public final D getD() { return d; }

	@Override public String toString() { return "(" + a + ", " + b + ", " + c + ", " + d + ")"; }
}
