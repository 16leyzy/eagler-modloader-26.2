package eaglermodloader.weaver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

/**
 * Crash isolation for mods' @Inject handlers. Mixin merges each handler into the game class it hooks,
 * named handler$&lt;id&gt;$&lt;mod id&gt;$&lt;name&gt;. These return nothing and only add to what the game does,
 * so if one throws (the mod failed to start, or it uses something the browser doesn't have) skipping
 * it is safe: the exception is reported once (EmlHooks.handlerFailed) and the game carries on as if
 * the mod hadn't hooked there. Handlers that return a value (redirects, wrapped calls, modified values)
 * replace part of the game's logic and are left alone.
 * Mods' Mod Menu integrations (ModMenuApi) are guarded too: Mod Menu calls them while drawing the title
 * screen, so a mod that failed to start used to crash it; now Mod Menu gets an empty answer.
 */
final class HandlerGuard {
	private HandlerGuard() {
	}

	static void apply(Path classes) throws IOException {
		List<Path> files = new ArrayList<>();
		try (Stream<Path> s = Files.walk(classes)) {
			s.filter(p -> p.toString().endsWith(".class")).forEach(files::add);
		}
		int n = 0, api = 0;
		for (Path p : files) {
			byte[] b = Files.readAllBytes(p);
			ClassNode cn = new ClassNode();
			new ClassReader(b).accept(cn, 0);
			boolean changed = false;
			boolean modMenuApi = cn.interfaces.contains(MOD_MENU_API);
			for (MethodNode m : cn.methods) {
				if ((m.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0 || m.instructions.size() == 0) continue;
				if (m.name.startsWith("handler$") && m.desc.endsWith(")V")) {
					String[] parts = m.name.split("\\$");
					String modId = parts.length >= 4 ? parts[2] : "?";
					String handler = parts.length >= 4 ? parts[parts.length - 1] : m.name;
					wrap(cn, m, modId, cn.name.replace('/', '.') + " (" + handler + ")");
					changed = true;
					n++;
				} else if (modMenuApi && (m.access & Opcodes.ACC_STATIC) == 0 && !m.name.startsWith("<")) {
					wrap(cn, m, cn.name.substring(cn.name.lastIndexOf('/') + 1), cn.name.replace('/', '.') + "." + m.name + " (Mod Menu integration)");
					changed = true;
					api++;
				}
			}
			if (changed) {
				ClassWriter cw = new ClassWriter(0);
				cn.accept(cw);
				Files.write(p, cw.toByteArray());
			}
		}
		System.out.println("[compat] crash isolation: " + n + " mod @Inject handlers skip themselves if they throw, "
				+ api + " Mod Menu integration methods return an empty answer");
	}

	static final String MOD_MENU_API = "com/terraformersmc/modmenu/api/ModMenuApi";

	/** try { body } catch (Throwable t) { EmlHooks.handlerFailed(t, modId, where); return <empty value>; } */
	private static void wrap(ClassNode cn, MethodNode m, String modId, String where) {
		LabelNode start = new LabelNode(), end = new LabelNode(), catcher = new LabelNode();
		m.instructions.insert(start);
		m.instructions.add(end);
		m.instructions.add(catcher);
		FrameNode f = new FrameNode(Opcodes.F_FULL, 0, null, 1, new Object[] { "java/lang/Throwable" });
		f.local = locals(cn.name, m);
		m.instructions.add(f);
		m.instructions.add(new LdcInsnNode(modId));
		m.instructions.add(new LdcInsnNode(where));
		m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "eaglermodloader/runtime/EmlHooks", "handlerFailed",
				"(Ljava/lang/Throwable;Ljava/lang/String;Ljava/lang/String;)V", false));
		Type ret = Type.getReturnType(m.desc);
		switch (ret.getSort()) {
		case Type.VOID: m.instructions.add(new InsnNode(Opcodes.RETURN)); break;
		case Type.BOOLEAN: case Type.BYTE: case Type.CHAR: case Type.SHORT: case Type.INT:
			m.instructions.add(new InsnNode(Opcodes.ICONST_0)); m.instructions.add(new InsnNode(Opcodes.IRETURN)); break;
		case Type.LONG: m.instructions.add(new InsnNode(Opcodes.LCONST_0)); m.instructions.add(new InsnNode(Opcodes.LRETURN)); break;
		case Type.FLOAT: m.instructions.add(new InsnNode(Opcodes.FCONST_0)); m.instructions.add(new InsnNode(Opcodes.FRETURN)); break;
		case Type.DOUBLE: m.instructions.add(new InsnNode(Opcodes.DCONST_0)); m.instructions.add(new InsnNode(Opcodes.DRETURN)); break;
		default:
			String empty = switch (ret.getDescriptor()) {
				case "Ljava/util/Map;" -> "emptyMap()Ljava/util/Map;";
				case "Ljava/util/List;", "Ljava/util/Collection;" -> "emptyList()Ljava/util/List;";
				case "Ljava/util/Set;" -> "emptySet()Ljava/util/Set;";
				default -> null;
			};
			if (empty != null) {
				int paren = empty.indexOf('(');
				m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Collections", empty.substring(0, paren), empty.substring(paren), false));
			} else if (ret.getDescriptor().equals("Ljava/util/Optional;")) {
				m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Optional", "empty", "()Ljava/util/Optional;", false));
			} else {
				m.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
			}
			m.instructions.add(new InsnNode(Opcodes.ARETURN));
		}
		// outermost: added last, so the mod's own try/catch blocks inside still take precedence
		m.tryCatchBlocks.add(new TryCatchBlockNode(start, end, catcher, "java/lang/Throwable"));
		m.maxStack = Math.max(m.maxStack, 4);
	}

	private static List<Object> locals(String owner, MethodNode m) {
		List<Object> out = new ArrayList<>();
		if ((m.access & Opcodes.ACC_STATIC) == 0) out.add(owner);
		for (Type t : Type.getArgumentTypes(m.desc)) {
			switch (t.getSort()) {
			case Type.BOOLEAN: case Type.BYTE: case Type.CHAR: case Type.SHORT: case Type.INT: out.add(Opcodes.INTEGER); break;
			case Type.FLOAT: out.add(Opcodes.FLOAT); break;
			case Type.LONG: out.add(Opcodes.LONG); break;
			case Type.DOUBLE: out.add(Opcodes.DOUBLE); break;
			case Type.ARRAY: out.add(t.getDescriptor()); break;
			default: out.add(t.getInternalName());
			}
		}
		return out;
	}
}
