package eaglermodloader.weaver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Switches off code that can't work in a browser (see loader/browser-compat.txt), so the browser
 * compiler doesn't need Java APIs it doesn't have: listed methods return a default value (nothing, 0,
 * false, an empty list/set/map/Optional/string, else null); listed constructors throw.
 *
 * Lines: "some.Class#method" (every overload), "some.Class#*" (every method). # starts a comment.
 */
final class BrowserCompat {
	private BrowserCompat() {
	}

	static void apply(Path classes, Path listFile) throws IOException {
		Map<String, Set<String>> list = new HashMap<>();
		for (String line : Files.readAllLines(listFile, StandardCharsets.UTF_8)) {
			int hash = line.indexOf(" #");
			if (hash >= 0) line = line.substring(0, hash);
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#")) continue;
			int i = line.indexOf('#');
			if (i < 0) continue;
			list.computeIfAbsent(line.substring(0, i).replace('.', '/'), k -> new HashSet<>()).add(line.substring(i + 1));
		}
		int n = 0;
		for (Map.Entry<String, Set<String>> e : list.entrySet()) {
			Path p = classes.resolve(e.getKey() + ".class");
			if (!Files.exists(p)) continue; // that mod isn't in this build
			ClassNode cn = new ClassNode();
			new ClassReader(Files.readAllBytes(p)).accept(cn, 0);
			boolean all = e.getValue().contains("*");
			for (MethodNode m : cn.methods) {
				if ((m.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;
				if (m.name.equals("<clinit>")) continue;
				if (all || e.getValue().contains(m.name)) {
					neuter(m);
					n++;
				}
			}
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cn.accept(cw);
			Files.write(p, cw.toByteArray());
		}
		System.out.println("[compat] " + n + " methods switched off for the browser");
		NettyTypeChecks.apply(classes);
	}

	private static void neuter(MethodNode m) {
		InsnList body = new InsnList();
		Type ret = Type.getReturnType(m.desc);
		if (m.name.equals("<init>")) {
			body.add(new TypeInsnNode(Opcodes.NEW, "java/lang/UnsupportedOperationException"));
			body.add(new InsnNode(Opcodes.DUP));
			body.add(new LdcInsnNode("not available in the browser"));
			body.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/UnsupportedOperationException", "<init>", "(Ljava/lang/String;)V", false));
			body.add(new InsnNode(Opcodes.ATHROW));
		} else {
			switch (ret.getSort()) {
			case Type.VOID: body.add(new InsnNode(Opcodes.RETURN)); break;
			case Type.BOOLEAN: case Type.CHAR: case Type.BYTE: case Type.SHORT: case Type.INT:
				body.add(new InsnNode(Opcodes.ICONST_0)); body.add(new InsnNode(Opcodes.IRETURN)); break;
			case Type.LONG: body.add(new InsnNode(Opcodes.LCONST_0)); body.add(new InsnNode(Opcodes.LRETURN)); break;
			case Type.FLOAT: body.add(new InsnNode(Opcodes.FCONST_0)); body.add(new InsnNode(Opcodes.FRETURN)); break;
			case Type.DOUBLE: body.add(new InsnNode(Opcodes.DCONST_0)); body.add(new InsnNode(Opcodes.DRETURN)); break;
			default:
				String d = ret.getDescriptor();
				// empty instead of null where callers are likely to iterate
				if (d.equals("Ljava/util/List;") || d.equals("Ljava/util/Collection;") || d.equals("Ljava/lang/Iterable;")) {
					body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Collections", "emptyList", "()Ljava/util/List;", false));
				} else if (d.equals("Ljava/util/Set;")) {
					body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Collections", "emptySet", "()Ljava/util/Set;", false));
				} else if (d.equals("Ljava/util/Map;")) {
					body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Collections", "emptyMap", "()Ljava/util/Map;", false));
				} else if (d.equals("Ljava/util/Optional;")) {
					body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Optional", "empty", "()Ljava/util/Optional;", false));
				} else if (d.equals("Ljava/lang/String;")) {
					body.add(new LdcInsnNode(""));
				} else {
					body.add(new InsnNode(Opcodes.ACONST_NULL));
				}
				body.add(new InsnNode(Opcodes.ARETURN));
				break;
			}
		}
		m.instructions = body;
		m.tryCatchBlocks.clear();
		if (m.localVariables != null) m.localVariables.clear();
		m.visibleLocalVariableAnnotations = null;
		m.invisibleLocalVariableAnnotations = null;
	}
}
