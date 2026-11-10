package eaglermodloader.weaver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Lets mods read and write fields by reflection (config libraries such as Mod Menu's save settings
 * this way). The browser compiler only keeps field readers/writers for fields it's told about, and has
 * no generic types.
 *
 * Mod code that lists fields (Class.getDeclaredFields(), getFields(), getDeclaredField(), getField())
 * marks the classes named in the same method (X.class) and its own class as reflected. They're
 * written to eml-reflect.txt in the game classes ("C class"), which the reflection supplier added to
 * Eaglercraft's TeaVM plugin reads, so all their fields stay readable and writable. Their fields with
 * generic types are listed too ("G class#field signature") for GeneratedMods.genericFieldType(), and
 * mods' Field.getGenericType() calls are pointed at EmlReflect.getGenericType(), which uses that table.
 */
final class ReflectionScan {
	private ReflectionScan() {
	}

	private static final Set<String> LIST_FIELDS = Set.of("getDeclaredFields", "getFields", "getDeclaredField", "getField");
	private static final String[] GAME = { "net/minecraft/", "com/mojang/", "net/lax1dude/", "org/spongepowered/", "net/fabricmc/loader/", "eaglermodloader/" };

	static void apply(Path classes) throws IOException {
		List<Path> files = new ArrayList<>();
		try (Stream<Path> s = Files.walk(classes)) {
			s.filter(p -> p.toString().endsWith(".class")).forEach(files::add);
		}
		Set<String> reflected = new TreeSet<>();
		int rewired = 0;
		for (Path p : files) {
			String name = classes.relativize(p).toString().replace('\\', '/');
			name = name.substring(0, name.length() - 6);
			if (isGame(name)) continue;
			byte[] b = Files.readAllBytes(p);
			ClassNode cn = new ClassNode();
			new ClassReader(b).accept(cn, 0);
			boolean changed = false;
			for (MethodNode m : cn.methods) {
				boolean lists = false;
				Set<String> named = new TreeSet<>();
				for (AbstractInsnNode in = m.instructions.getFirst(); in != null; in = in.getNext()) {
					if (in instanceof MethodInsnNode mi) {
						if (mi.owner.equals("java/lang/Class") && LIST_FIELDS.contains(mi.name)) lists = true;
						if (mi.owner.equals("java/lang/reflect/Field") && mi.name.equals("getGenericType") && mi.desc.equals("()Ljava/lang/reflect/Type;")) {
							mi.setOpcode(Opcodes.INVOKESTATIC);
							mi.owner = "eaglermodloader/runtime/EmlReflect";
							mi.desc = "(Ljava/lang/reflect/Field;)Ljava/lang/reflect/Type;";
							mi.itf = false;
							changed = true;
							rewired++;
						}
					} else if (in instanceof LdcInsnNode ldc && ldc.cst instanceof Type t && t.getSort() == Type.OBJECT) {
						named.add(t.getInternalName());
					}
				}
				if (lists) {
					reflected.add(cn.name);
					for (String n : named) if (!n.startsWith("java/")) reflected.add(n);
				}
			}
			if (changed) {
				ClassWriter cw = new ClassWriter(0);
				cn.accept(cw);
				Files.write(p, cw.toByteArray());
			}
		}
		StringBuilder out = new StringBuilder();
		int generic = 0;
		Map<String, ClassNode> cache = new HashMap<>();
		for (String c : reflected) {
			ClassNode cn = read(classes, c, cache);
			if (cn == null) continue;
			out.append("C ").append(c.replace('/', '.')).append('\n');
			for (FieldNode f : cn.fields) {
				if (f.signature == null || !f.signature.startsWith("L") || !allPublic(classes, f.signature, cache)) continue;
				out.append("G ").append(c.replace('/', '.')).append('#').append(f.name).append(' ').append(f.signature).append('\n');
				generic++;
			}
		}
		Files.write(classes.resolve("eml-reflect.txt"), out.toString().getBytes(StandardCharsets.UTF_8));
		System.out.println("[compat] reflection: " + reflected.size() + " classes readable by reflection, " + generic
				+ " generic field types, " + rewired + " getGenericType calls rewired");
	}

	private static boolean isGame(String name) {
		for (String g : GAME) if (name.startsWith(g)) return true;
		return false;
	}

	private static ClassNode read(Path classes, String name, Map<String, ClassNode> cache) throws IOException {
		if (cache.containsKey(name)) return cache.get(name);
		Path p = classes.resolve(name + ".class");
		ClassNode cn = null;
		if (Files.isRegularFile(p)) {
			cn = new ClassNode();
			new ClassReader(Files.readAllBytes(p)).accept(cn, ClassReader.SKIP_CODE);
		}
		cache.put(name, cn);
		return cn;
	}

	/** True if every class named in the signature can be named from generated code (public, or from the JDK). */
	private static boolean allPublic(Path classes, String sig, Map<String, ClassNode> cache) throws IOException {
		for (int i = 0; i < sig.length(); i++) {
			boolean typeStart = i == 0 || "<;+-[".indexOf(sig.charAt(i - 1)) >= 0;
			if (!typeStart) continue;
			char c = sig.charAt(i);
			if (c == 'T') return false; // a type variable: not known here
			if (c != 'L') continue;
			int end = i + 1;
			while (end < sig.length() && "<;".indexOf(sig.charAt(end)) < 0) end++;
			String n = sig.substring(i + 1, end);
			if (n.startsWith("java/")) continue;
			ClassNode cn = read(classes, n, cache);
			if (cn == null || (cn.access & Opcodes.ACC_PUBLIC) == 0 || n.contains("$") && !nestedPublic(cn, n)) return false;
		}
		return true;
	}

	private static boolean nestedPublic(ClassNode cn, String n) {
		for (InnerClassNode ic : cn.innerClasses) {
			if (ic.name.equals(n)) return (ic.access & Opcodes.ACC_PUBLIC) != 0 && ic.innerName != null;
		}
		return false;
	}
}
