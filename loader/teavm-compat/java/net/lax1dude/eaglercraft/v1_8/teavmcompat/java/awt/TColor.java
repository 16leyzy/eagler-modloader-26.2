package net.lax1dude.eaglercraft.v1_8.teavmcompat.java.awt;

/**
 * java.awt.Color for the browser (added by eagler-modloader). Mods use it for colour maths (HUD text
 * colours, HSB conversion), which is plain arithmetic; nothing here draws.
 */
public class TColor {
	public static final TColor white = new TColor(255, 255, 255), WHITE = white;
	public static final TColor lightGray = new TColor(192, 192, 192), LIGHT_GRAY = lightGray;
	public static final TColor gray = new TColor(128, 128, 128), GRAY = gray;
	public static final TColor darkGray = new TColor(64, 64, 64), DARK_GRAY = darkGray;
	public static final TColor black = new TColor(0, 0, 0), BLACK = black;
	public static final TColor red = new TColor(255, 0, 0), RED = red;
	public static final TColor pink = new TColor(255, 175, 175), PINK = pink;
	public static final TColor orange = new TColor(255, 200, 0), ORANGE = orange;
	public static final TColor yellow = new TColor(255, 255, 0), YELLOW = yellow;
	public static final TColor green = new TColor(0, 255, 0), GREEN = green;
	public static final TColor magenta = new TColor(255, 0, 255), MAGENTA = magenta;
	public static final TColor cyan = new TColor(0, 255, 255), CYAN = cyan;
	public static final TColor blue = new TColor(0, 0, 255), BLUE = blue;

	private final int value;

	public TColor(int r, int g, int b) {
		this(r, g, b, 255);
	}

	public TColor(int r, int g, int b, int a) {
		check(r, "Red"); check(g, "Green"); check(b, "Blue"); check(a, "Alpha");
		value = (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF;
	}

	public TColor(int rgb) {
		value = 0xFF000000 | rgb;
	}

	public TColor(int rgba, boolean hasAlpha) {
		value = hasAlpha ? rgba : 0xFF000000 | rgba;
	}

	public TColor(float r, float g, float b) {
		this((int) (r * 255 + 0.5f), (int) (g * 255 + 0.5f), (int) (b * 255 + 0.5f));
	}

	public TColor(float r, float g, float b, float a) {
		this((int) (r * 255 + 0.5f), (int) (g * 255 + 0.5f), (int) (b * 255 + 0.5f), (int) (a * 255 + 0.5f));
	}

	private static void check(int v, String what) {
		if (v < 0 || v > 255) throw new IllegalArgumentException("Color parameter outside of expected range: " + what);
	}

	public int getRed() { return value >> 16 & 0xFF; }
	public int getGreen() { return value >> 8 & 0xFF; }
	public int getBlue() { return value & 0xFF; }
	public int getAlpha() { return value >>> 24; }
	public int getRGB() { return value; }
	public int getTransparency() { return getAlpha() == 255 ? 1 : getAlpha() == 0 ? 2 : 3; }

	public TColor brighter() {
		int r = getRed(), g = getGreen(), b = getBlue(), a = getAlpha();
		int i = (int) (1.0 / (1.0 - 0.7));
		if (r == 0 && g == 0 && b == 0) return new TColor(i, i, i, a);
		if (r > 0 && r < i) r = i;
		if (g > 0 && g < i) g = i;
		if (b > 0 && b < i) b = i;
		return new TColor(Math.min((int) (r / 0.7), 255), Math.min((int) (g / 0.7), 255), Math.min((int) (b / 0.7), 255), a);
	}

	public TColor darker() {
		return new TColor(Math.max((int) (getRed() * 0.7), 0), Math.max((int) (getGreen() * 0.7), 0), Math.max((int) (getBlue() * 0.7), 0), getAlpha());
	}

	public float[] getRGBComponents(float[] out) {
		float[] f = out != null ? out : new float[4];
		f[0] = getRed() / 255f; f[1] = getGreen() / 255f; f[2] = getBlue() / 255f; f[3] = getAlpha() / 255f;
		return f;
	}

	public float[] getRGBColorComponents(float[] out) {
		float[] f = out != null ? out : new float[3];
		f[0] = getRed() / 255f; f[1] = getGreen() / 255f; f[2] = getBlue() / 255f;
		return f;
	}

	public float[] getComponents(float[] out) { return getRGBComponents(out); }
	public float[] getColorComponents(float[] out) { return getRGBColorComponents(out); }

	public static TColor decode(String nm) throws NumberFormatException {
		int i = Integer.decode(nm);
		return new TColor(i >> 16 & 0xFF, i >> 8 & 0xFF, i & 0xFF);
	}

	public static TColor getColor(String nm) { return getColor(nm, (TColor) null); }
	public static TColor getColor(String nm, TColor v) { return v; }
	public static TColor getColor(String nm, int v) { return new TColor(v); }

	public static int HSBtoRGB(float hue, float saturation, float brightness) {
		int r = 0, g = 0, b = 0;
		if (saturation == 0) {
			r = g = b = (int) (brightness * 255.0f + 0.5f);
		} else {
			float h = (hue - (float) Math.floor(hue)) * 6.0f;
			float f = h - (float) Math.floor(h);
			float p = brightness * (1.0f - saturation);
			float q = brightness * (1.0f - saturation * f);
			float t = brightness * (1.0f - (saturation * (1.0f - f)));
			switch ((int) h) {
			case 0: r = (int) (brightness * 255.0f + 0.5f); g = (int) (t * 255.0f + 0.5f); b = (int) (p * 255.0f + 0.5f); break;
			case 1: r = (int) (q * 255.0f + 0.5f); g = (int) (brightness * 255.0f + 0.5f); b = (int) (p * 255.0f + 0.5f); break;
			case 2: r = (int) (p * 255.0f + 0.5f); g = (int) (brightness * 255.0f + 0.5f); b = (int) (t * 255.0f + 0.5f); break;
			case 3: r = (int) (p * 255.0f + 0.5f); g = (int) (q * 255.0f + 0.5f); b = (int) (brightness * 255.0f + 0.5f); break;
			case 4: r = (int) (t * 255.0f + 0.5f); g = (int) (p * 255.0f + 0.5f); b = (int) (brightness * 255.0f + 0.5f); break;
			case 5: r = (int) (brightness * 255.0f + 0.5f); g = (int) (p * 255.0f + 0.5f); b = (int) (q * 255.0f + 0.5f); break;
			}
		}
		return 0xff000000 | (r << 16) | (g << 8) | b;
	}

	public static float[] RGBtoHSB(int r, int g, int b, float[] hsb) {
		if (hsb == null) hsb = new float[3];
		int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
		float brightness = max / 255.0f;
		float saturation = max != 0 ? (float) (max - min) / max : 0;
		float hue;
		if (saturation == 0) {
			hue = 0;
		} else {
			float rc = (float) (max - r) / (max - min), gc = (float) (max - g) / (max - min), bc = (float) (max - b) / (max - min);
			if (r == max) hue = bc - gc;
			else if (g == max) hue = 2.0f + rc - bc;
			else hue = 4.0f + gc - rc;
			hue = hue / 6.0f;
			if (hue < 0) hue = hue + 1.0f;
		}
		hsb[0] = hue; hsb[1] = saturation; hsb[2] = brightness;
		return hsb;
	}

	public static TColor getHSBColor(float h, float s, float b) { return new TColor(HSBtoRGB(h, s, b)); }

	@Override public int hashCode() { return value; }
	@Override public boolean equals(Object o) { return o instanceof TColor c && c.value == value; }
	@Override public String toString() { return "java.awt.Color[r=" + getRed() + ",g=" + getGreen() + ",b=" + getBlue() + "]"; }
}
