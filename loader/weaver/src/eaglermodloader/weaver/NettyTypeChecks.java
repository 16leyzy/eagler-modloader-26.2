package eaglermodloader.weaver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Netty decides which messages a handler takes (MessageToMessageEncoder&lt;Packet&gt; only takes
 * Packets) by reading the handler's generic type at run time. The browser build has no generic type
 * information, so Eaglercraft makes every handler take every message, and its own handlers check the
 * type themselves. Mods' handlers don't, and crash with a ClassCastException when another message
 * passes through (Fabric API's packet splitter, for one).
 *
 * The class files still have the generic types, so here each handler that extends one of netty's
 * typed base classes directly gets the explicit check: acceptInboundMessage/acceptOutboundMessage
 * returning "msg instanceof T". Handlers that already override the method are left alone.
 */
final class NettyTypeChecks {
	private NettyTypeChecks() {
	}

	/** netty base class -> { method, index of the type argument it checks }... */
	private static final Map<String, Object[][]> BASES = new HashMap<>();
	static {
		Object[][] out0 = { { "acceptOutboundMessage", 0 } }, in0 = { { "acceptInboundMessage", 0 } };
		BASES.put("io/netty/handler/codec/MessageToMessageEncoder", out0);
		BASES.put("io/netty/handler/codec/MessageToByteEncoder", out0);
		BASES.put("io/netty/handler/codec/ByteToMessageCodec", out0);
		BASES.put("io/netty/handler/codec/MessageToMessageDecoder", in0);
		BASES.put("io/netty/channel/SimpleChannelInboundHandler", in0);
		BASES.put("io/netty/handler/codec/MessageToMessageCodec", new Object[][] { { "acceptInboundMessage", 0 }, { "acceptOutboundMessage", 1 } });
	}
	private static final String DESC = "(Ljava/lang/Object;)Z";

	static void apply(Path classes) throws IOException {
		List<Path> files = new ArrayList<>();
		try (Stream<Path> s = Files.walk(classes)) {
			s.filter(p -> p.toString().endsWith(".class")).forEach(files::add);
		}
		int n = 0;
		for (Path p : files) {
			byte[] b = Files.readAllBytes(p);
			ClassReader cr = new ClassReader(b);
			Object[][] checks = BASES.get(cr.getSuperName());
			if (checks == null) continue;
			ClassNode cn = new ClassNode();
			cr.accept(cn, 0);
			if (cn.signature == null) continue;
			List<String> args = superTypeArgs(cn.signature);
			boolean changed = false;
			for (Object[] c : checks) {
				String method = (String) c[0];
				int idx = (Integer) c[1];
				if (idx >= args.size()) continue;
				String type = erase(args.get(idx));
				if (type == null || type.equals("java/lang/Object")) continue;
				if (cn.methods.stream().anyMatch(m -> m.name.equals(method) && m.desc.equals(DESC))) continue;
				MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC, method, DESC, null, new String[] { "java/lang/Exception" });
				m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
				m.instructions.add(new TypeInsnNode(Opcodes.INSTANCEOF, type));
				m.instructions.add(new InsnNode(Opcodes.IRETURN));
				cn.methods.add(m);
				changed = true;
				n++;
			}
			if (changed) {
				ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
				cn.accept(cw);
				Files.write(p, cw.toByteArray());
			}
		}
		System.out.println("[compat] " + n + " netty handlers given an explicit message type check");
	}

	/** The type arguments of the superclass in a class signature, e.g. "Lnet/minecraft/network/protocol/Packet<*>;". */
	static List<String> superTypeArgs(String sig) {
		List<String> out = new ArrayList<>();
		int i = 0;
		if (sig.charAt(0) == '<') i = skipBalanced(sig, 0);
		if (i >= sig.length() || sig.charAt(i) != 'L') return out;
		int j = i;
		while (sig.charAt(j) != '<' && sig.charAt(j) != ';') j++;
		if (sig.charAt(j) == ';') return out;
		int k = j + 1;
		while (sig.charAt(k) != '>') {
			int start = k;
			k = skipType(sig, k);
			out.add(sig.substring(start, k));
		}
		return out;
	}

	private static int skipBalanced(String s, int i) {
		int depth = 0;
		do {
			char c = s.charAt(i++);
			if (c == '<') depth++;
			else if (c == '>') depth--;
		} while (depth > 0);
		return i;
	}

	private static int skipType(String s, int k) {
		char c = s.charAt(k);
		switch (c) {
		case '*': return k + 1;
		case '+': case '-': case '[': return skipType(s, k + 1);
		case 'T': return s.indexOf(';', k) + 1;
		case 'L': {
			int depth = 0;
			for (;; k++) {
				char ch = s.charAt(k);
				if (ch == '<') depth++;
				else if (ch == '>') depth--;
				else if (ch == ';' && depth == 0) return k + 1;
			}
		}
		default: return k + 1; // primitive (only inside arrays)
		}
	}

	/** Internal name (or array descriptor) for an INSTANCEOF check, or null for a type variable or wildcard. */
	static String erase(String arg) {
		if (arg.startsWith("+")) arg = arg.substring(1);
		if (arg.startsWith("*") || arg.startsWith("-")) return null;
		int dims = 0;
		while (arg.charAt(dims) == '[') dims++;
		if (arg.charAt(dims) == 'T') return null;
		StringBuilder sb = new StringBuilder();
		int depth = 0;
		for (char ch : arg.toCharArray()) {
			if (ch == '<') depth++;
			else if (ch == '>') depth--;
			else if (depth == 0) sb.append(ch == '.' ? '$' : ch);
		}
		String d = sb.toString();
		return dims > 0 ? d : d.substring(1, d.length() - 1);
	}
}
