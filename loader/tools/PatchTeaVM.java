import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Makes TeaVM's NullCheckInsertion skip methods it can't handle instead of failing the whole build.
 * TeaVM's SSA update trips over some bytecode that mixins produce (e.g. a MixinExtras @Local written
 * back inside a try block) and reports only "Variable used before definition". Such methods are now
 * left without explicit null checks (a null dereference there traps instead of throwing an NPE) and
 * named in the build log.
 * Same for TeaVM.optimizeMethodCacheMiss (the per-method optimizer).
 * And ReferenceResolver: a class, method or field the browser doesn't have (used by a mod) is reported
 * as a warning instead of an error. TeaVM already compiles that spot to throw NoClassDefFoundError /
 * NoSuchMethodError / NoSuchFieldError when it runs, but writes no output at all if any error remains.
 * Usage: java PatchTeaVM path/to/NullCheckInsertion.class path/to/TeaVM.class path/to/ReferenceResolver.class
 * (patched in place)
 */
public class PatchTeaVM {
	static final String CLS = "org/teavm/model/transformation/NullCheckInsertion";
	static final String DESC = "(Lorg/teavm/model/Program;Lorg/teavm/model/MethodReference;)V";

	public static void main(String[] args) throws Exception {
		for (String a : args) {
			Path p = Paths.get(a);
			if (p.toString().endsWith("TeaVM.class")) patchOptimize(p);
			else if (p.toString().endsWith("ReferenceResolver.class")) patchMissingAsWarnings(p);
			else patchNullChecks(p);
		}
	}

	/** ReferenceResolver.reportError: Diagnostics.error -> Diagnostics.warning. */
	static void patchMissingAsWarnings(Path p) throws Exception {
		ClassReader cr = new ClassReader(Files.readAllBytes(p));
		ClassWriter cw = new ClassWriter(0);
		final int[] n = { 0 };
		cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
				MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
				if (!name.equals("reportError")) return mv;
				return new MethodVisitor(Opcodes.ASM9, mv) {
					@Override
					public void visitMethodInsn(int op, String owner, String mname, String mdesc, boolean itf) {
						if (owner.equals("org/teavm/diagnostics/Diagnostics") && mname.equals("error")) { mname = "warning"; n[0]++; }
						super.visitMethodInsn(op, owner, mname, mdesc, itf);
					}
				};
			}
		}, 0);
		if (n[0] == 0) throw new IllegalStateException("ReferenceResolver.reportError: no Diagnostics.error call found");
		Files.write(p, cw.toByteArray());
		System.out.println("patched " + p + ": missing classes/methods/fields are warnings (" + n[0] + " calls)");
	}

	static final String TEAVM = "org/teavm/vm/TeaVM";
	static final String OPT_DESC = "(Lorg/teavm/model/MethodHolder;Lorg/teavm/model/Program;)Lorg/teavm/model/Program;";

	/**
	 * TeaVM.optimizeMethodCacheMiss(method, program): if TeaVM's optimizer fails on one method, use the
	 * method unoptimized (and name it in the log) instead of failing the whole build.
	 */
	static void patchOptimize(Path p) throws Exception {
		ClassReader cr = new ClassReader(Files.readAllBytes(p));
		ClassWriter cw = new ClassWriter(0);
		cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
				if (name.equals("optimizeMethodCacheMiss") && desc.equals(OPT_DESC)) name = "optimizeMethodCacheMiss$orig";
				return super.visitMethod(access, name, desc, sig, ex);
			}

			@Override
			public void visitEnd() {
				final String MH = "org/teavm/model/MethodHolder", PROGRAM = "org/teavm/model/Program";
				MethodVisitor mv = super.visitMethod(Opcodes.ACC_PRIVATE, "optimizeMethodCacheMiss", OPT_DESC, null, null);
				mv.visitCode();
				Label start = new Label(), end = new Label(), handler = new Label();
				mv.visitTryCatchBlock(start, end, handler, "java/lang/Throwable");
				// keep a copy of the program as it was before optimizing
				mv.visitVarInsn(Opcodes.ALOAD, 2);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/teavm/model/util/ProgramUtils", "copy", "(Lorg/teavm/model/ProgramReader;)Lorg/teavm/model/Program;", false);
				mv.visitVarInsn(Opcodes.ASTORE, 3);
				mv.visitLabel(start);
				mv.visitVarInsn(Opcodes.ALOAD, 0);
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitVarInsn(Opcodes.ALOAD, 2);
				mv.visitMethodInsn(Opcodes.INVOKESPECIAL, TEAVM, "optimizeMethodCacheMiss$orig", OPT_DESC, false);
				mv.visitLabel(end);
				mv.visitInsn(Opcodes.ARETURN);
				mv.visitLabel(handler);
				mv.visitFrame(Opcodes.F_FULL, 4, new Object[] { TEAVM, MH, PROGRAM, PROGRAM }, 1, new Object[] { "java/lang/Throwable" });
				mv.visitVarInsn(Opcodes.ASTORE, 4);
				mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "err", "Ljava/io/PrintStream;");
				mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
				mv.visitInsn(Opcodes.DUP);
				mv.visitLdcInsn("[eagler-modloader] TeaVM can't optimize ");
				mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false);
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MH, "getReference", "()Lorg/teavm/model/MethodReference;", false);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);
				mv.visitLdcInsn(" (left unoptimized): ");
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
				mv.visitVarInsn(Opcodes.ALOAD, 4);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
				mv.visitVarInsn(Opcodes.ALOAD, 3);
				mv.visitInsn(Opcodes.ARETURN);
				mv.visitMaxs(4, 5);
				mv.visitEnd();
				super.visitEnd();
			}
		}, 0);
		Files.write(p, cw.toByteArray());
		System.out.println("[patch-teavm] the optimizer skips (and names) methods it can't handle");
	}

	static void patchNullChecks(Path p) throws Exception {
		ClassReader cr = new ClassReader(Files.readAllBytes(p));
		ClassWriter cw = new ClassWriter(0);
		cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
				if (name.equals("transformProgram") && desc.equals(DESC)) name = "transformProgram$orig";
				return super.visitMethod(access, name, desc, sig, ex);
			}

			@Override
			public void visitEnd() {
				// transformProgram(p, m): try the real pass on a copy first; if TeaVM's own SSA update fails
				// on it, leave this method without explicit null checks instead of failing the whole build
				final String PROGRAM = "org/teavm/model/Program", REF = "org/teavm/model/MethodReference";
				MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "transformProgram", DESC, null, null);
				mv.visitCode();
				Label start = new Label(), end = new Label(), handler = new Label(), after = new Label();
				mv.visitTryCatchBlock(start, end, handler, "java/lang/Throwable");
				mv.visitLabel(start);
				mv.visitVarInsn(Opcodes.ALOAD, 0);
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/teavm/model/util/ProgramUtils", "copy", "(Lorg/teavm/model/ProgramReader;)Lorg/teavm/model/Program;", false);
				mv.visitVarInsn(Opcodes.ALOAD, 2);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CLS, "transformProgram$orig", DESC, false);
				mv.visitLabel(end);
				mv.visitJumpInsn(Opcodes.GOTO, after);
				mv.visitLabel(handler);
				mv.visitFrame(Opcodes.F_FULL, 3, new Object[] { CLS, PROGRAM, REF }, 1, new Object[] { "java/lang/Throwable" });
				mv.visitVarInsn(Opcodes.ASTORE, 3);
				mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "err", "Ljava/io/PrintStream;");
				mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
				mv.visitInsn(Opcodes.DUP);
				mv.visitLdcInsn("[eagler-modloader] TeaVM can't add null checks to ");
				mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false);
				mv.visitVarInsn(Opcodes.ALOAD, 2);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);
				mv.visitLdcInsn(" (left without them): ");
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
				mv.visitVarInsn(Opcodes.ALOAD, 3);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
				mv.visitInsn(Opcodes.RETURN);
				mv.visitLabel(after);
				mv.visitFrame(Opcodes.F_FULL, 3, new Object[] { CLS, PROGRAM, REF }, 0, new Object[0]);
				mv.visitVarInsn(Opcodes.ALOAD, 0);
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitVarInsn(Opcodes.ALOAD, 2);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CLS, "transformProgram$orig", DESC, false);
				mv.visitInsn(Opcodes.RETURN);
				mv.visitMaxs(4, 4);
				mv.visitEnd();
				super.visitEnd();
			}
		}, 0);
		Files.write(p, cw.toByteArray());
		System.out.println("[patch-teavm] NullCheckInsertion skips (and names) methods it can't handle");
	}
}
