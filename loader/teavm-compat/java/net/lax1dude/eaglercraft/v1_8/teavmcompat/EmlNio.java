package net.lax1dude.eaglercraft.v1_8.teavmcompat;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/** java.nio helpers for the loader's donors (added by eagler-modloader). */
public final class EmlNio {
	private EmlNio() {
	}

	/** Files.newDirectoryStream(dir, glob): the entries whose file name matches the glob (*, ?, {a,b}). */
	public static DirectoryStream<Path> newDirectoryStream(Path dir, String glob) throws IOException {
		String regex = globToRegex(glob);
		final List<Path> out = new ArrayList<>();
		try (Stream<Path> s = Files.list(dir)) {
			Iterator<Path> it = s.iterator();
			while (it.hasNext()) {
				Path p = it.next();
				Path name = p.getFileName();
				if (name != null && name.toString().matches(regex)) out.add(p);
			}
		}
		return new DirectoryStream<Path>() {
			@Override public Iterator<Path> iterator() { return out.iterator(); }
			@Override public void close() { }
		};
	}

	static String globToRegex(String glob) {
		StringBuilder sb = new StringBuilder();
		boolean inGroup = false;
		for (char c : glob.toCharArray()) {
			switch (c) {
			case '*': sb.append("[^/]*"); break;
			case '?': sb.append("[^/]"); break;
			case '{': sb.append("(?:"); inGroup = true; break;
			case '}': sb.append(')'); inGroup = false; break;
			case ',': sb.append(inGroup ? "|" : ","); break;
			case '.': case '(': case ')': case '+': case '|': case '^': case '$': case '@': case '%': case '\\': case '[': case ']':
				sb.append('\\').append(c); break;
			default: sb.append(c);
			}
		}
		return sb.toString();
	}
}
