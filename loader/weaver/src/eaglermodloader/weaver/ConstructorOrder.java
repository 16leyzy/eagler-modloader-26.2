package eaglermodloader.weaver;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Mixins often hook a constructor by name only (method = "&lt;init&gt;"), written for a vanilla class
 * that has one constructor. Mixin then uses the first constructor in the class file. Eaglercraft adds
 * constructors to some classes (ClientHandshakePacketListenerImpl has three, each handing off to the
 * next with this(...)), so that first one may only be used by some callers, and the hook silently
 * doesn't run for the rest (Fabric API's networking add-on was missing when joining a server).
 *
 * Before the mods are applied, the constructors that do the actual work (the ones that don't hand
 * off to another constructor of the same class) are moved first. Every way of constructing the
 * object goes through one of them, so a name-only hook runs whichever constructor was called.
 * Classes whose first constructor already does the work are left untouched.
 */
final class ConstructorOrder {
	private ConstructorOrder() {
	}

	static int reordered;

	static byte[] apply(byte[] b) {
		ClassNode cn = new ClassNode();
		new ClassReader(b).accept(cn, 0);
		List<MethodNode> ctors = new ArrayList<>();
		for (MethodNode m : cn.methods) if (m.name.equals("<init>")) ctors.add(m);
		if (ctors.size() < 2 || !delegates(cn.name, ctors.get(0))) return b;
		List<MethodNode> working = new ArrayList<>(), delegating = new ArrayList<>();
		for (MethodNode m : ctors) (delegates(cn.name, m) ? delegating : working).add(m);
		if (working.isEmpty()) return b;
		// keep everything else in place; the constructors fill the slots the constructors had, working ones first
		List<MethodNode> order = new ArrayList<>(working);
		order.addAll(delegating);
		int k = 0;
		for (int i = 0; i < cn.methods.size(); i++) {
			if (cn.methods.get(i).name.equals("<init>")) cn.methods.set(i, order.get(k++));
		}
		ClassWriter cw = new ClassWriter(0);
		cn.accept(cw);
		reordered++;
		return cw.toByteArray();
	}

	/** True if the constructor calls another constructor of the same class on itself (this(...)). */
	private static boolean delegates(String owner, MethodNode m) {
		int news = 0;
		for (AbstractInsnNode in = m.instructions.getFirst(); in != null; in = in.getNext()) {
			if (in.getOpcode() == Opcodes.NEW && ((TypeInsnNode) in).desc.equals(owner)) news++;
			if (in.getOpcode() == Opcodes.INVOKESPECIAL) {
				MethodInsnNode mi = (MethodInsnNode) in;
				if (mi.name.equals("<init>") && mi.owner.equals(owner)) {
					if (news == 0) return true;
					news--; // "new Owner(...)" inside the constructor, not this(...)
				}
			}
		}
		return false;
	}
}
