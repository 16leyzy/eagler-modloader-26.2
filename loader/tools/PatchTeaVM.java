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
 * And TConstructor/TMethod (TeaVM's java.lang.reflect.Constructor/Method): in WebAssembly builds the
 * modifiers TeaVM records for a constructor or method can have the bit newInstance()/invoke() read as
 * "abstract" set for ordinary public ones, so Constructor.newInstance() threw InstantiationException
 * (config libraries such as Cloth Config and YACL create their settings objects this way). The
 * abstract check is dropped; creating an abstract class fails anyway.
 * And TFiles (java.nio.file.Files): createDirectories() of a one-level path that already exists
 * ("config") threw FileAlreadyExistsException; like the JDK it now returns when the directory exists.
 * And TCharset (java.nio.charset.Charset): forName/isSupported accept "UTF8" for UTF-8, as the JDK does
 * (TOML and other config libraries ask for it by that name).
 * Usage: java PatchTeaVM CLASS_FILE... (NullCheckInsertion, TeaVM, ReferenceResolver, TConstructor,
 * TMethod, TFiles, TCharset; patched in place)
 */
public class PatchTeaVM {
	static final String CLS = "org/teavm/model/transformation/NullCheckInsertion";
	static final String DESC = "(Lorg/teavm/model/Program;Lorg/teavm/model/MethodReference;)V";

	public static void main(String[] args) throws Exception {
		for (String a : args) {
			Path p = Paths.get(a);
			if (p.toString().endsWith("TeaVM.class")) patchOptimize(p);
			else if (p.toString().endsWith("ReferenceResolver.class")) patchMissingAsWarnings(p);
			else if (p.toString().endsWith("TConstructor.class") || p.toString().endsWith("TMethod.class")) patchAbstractCheck(p);
			else if (p.toString().endsWith("TFiles.class")) patchCreateDirectories(p);
			else if (p.toString().endsWith("TCharset.class")) patchCharsetAlias(p);
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

	/** In newInstance/invoke: "modifiers &amp; Flags.ABSTRACT" becomes "modifiers &amp; 0". */
	static void patchAbstractCheck(Path p) throws Exception {
		ClassReader cr = new ClassReader(Files.readAllBytes(p));
		ClassWriter cw = new ClassWriter(0);
		final int[] n = { 0 };
		cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
				MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
				if (!name.equals("newInstance") && !name.equals("invoke")) return mv;
				return new MethodVisitor(Opcodes.ASM9, mv) {
					boolean afterModifiers;
					@Override
					public void visitFieldInsn(int op, String owner, String fname, String fdesc) {
						super.visitFieldInsn(op, owner, fname, fdesc);
						afterModifiers = op == Opcodes.GETFIELD && fname.equals("modifiers");
					}
					@Override
					public void visitInsn(int op) {
						if (afterModifiers && op == Opcodes.ICONST_1) { op = Opcodes.ICONST_0; n[0]++; }
						afterModifiers = false;
						super.visitInsn(op);
					}
					@Override public void visitVarInsn(int op, int v) { afterModifiers = false; super.visitVarInsn(op, v); }
					@Override public void visitMethodInsn(int op, String o, String nm, String d, boolean itf) { afterModifiers = false; super.visitMethodInsn(op, o, nm, d, itf); }
					@Override public void visitIntInsn(int op, int v) { afterModifiers = false; super.visitIntInsn(op, v); }
					@Override public void visitLdcInsn(Object v) { afterModifiers = false; super.visitLdcInsn(v); }
				};
			}
		}, 0);
		if (n[0] == 0 && p.toString().endsWith("TConstructor.class")) throw new IllegalStateException("TConstructor: abstract check not found");
		Files.write(p, cw.toByteArray());
		System.out.println("patched " + p + ": abstract check in newInstance/invoke dropped (" + n[0] + ")");
	}

	/** TFiles.createDirectories: "if (isDirectory(dir)) return dir;" first. */
	static void patchCreateDirectories(Path p) throws Exception {
		ClassReader cr = new ClassReader(Files.readAllBytes(p));
		ClassWriter cw = new ClassWriter(0);
		final int[] n = { 0 };
		final String files = cr.getClassName();
		final String pathType = files.substring(0, files.lastIndexOf('/') + 1) + "TPath";
		final String linkType = files.substring(0, files.lastIndexOf('/') + 1) + "TLinkOption";
		cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
				MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
				if (!name.equals("createDirectories") || !desc.startsWith("(L" + pathType + ";")) return mv;
				return new MethodVisitor(Opcodes.ASM9, mv) {
					@Override
					public void visitCode() {
						super.visitCode();
						org.objectweb.asm.Label go = new org.objectweb.asm.Label();
						super.visitVarInsn(Opcodes.ALOAD, 0);
						super.visitInsn(Opcodes.ICONST_0);
						super.visitTypeInsn(Opcodes.ANEWARRAY, linkType);
						super.visitMethodInsn(Opcodes.INVOKESTATIC, files, "isDirectory", "(L" + pathType + ";[L" + linkType + ";)Z", false);
						super.visitJumpInsn(Opcodes.IFEQ, go);
						super.visitVarInsn(Opcodes.ALOAD, 0);
						super.visitInsn(Opcodes.ARETURN);
						super.visitLabel(go);
						super.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
						n[0]++;
					}
					@Override
					public void visitMaxs(int stack, int locals) {
						super.visitMaxs(Math.max(stack, 2), locals);
					}
				};
			}
		}, 0);
		if (n[0] == 0) throw new IllegalStateException("TFiles.createDirectories not found");
		Files.write(p, cw.toByteArray());
		System.out.println("patched " + p + ": createDirectories returns when the directory exists");
	}

	/** TCharset.forName/isSupported(String): "UTF8" (any case) becomes "UTF-8" first. */
	static void patchCharsetAlias(Path p) throws Exception {
		ClassReader cr = new ClassReader(Files.readAllBytes(p));
		ClassWriter cw = new ClassWriter(0);
		final int[] n = { 0 };
		cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
				MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
				if ((access & Opcodes.ACC_STATIC) == 0 || !(name.equals("forName") || name.equals("isSupported"))
						|| !desc.startsWith("(Ljava/lang/String;)")) return mv;
				return new MethodVisitor(Opcodes.ASM9, mv) {
					@Override
					public void visitCode() {
						super.visitCode();
						org.objectweb.asm.Label go = new org.objectweb.asm.Label();
						super.visitLdcInsn("UTF8");
						super.visitVarInsn(Opcodes.ALOAD, 0);
						super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equalsIgnoreCase", "(Ljava/lang/String;)Z", false);
						super.visitJumpInsn(Opcodes.IFEQ, go);
						super.visitLdcInsn("UTF-8");
						super.visitVarInsn(Opcodes.ASTORE, 0);
						super.visitLabel(go);
						super.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
						n[0]++;
					}
					@Override
					public void visitMaxs(int stack, int locals) {
						super.visitMaxs(Math.max(stack, 2), locals);
					}
				};
			}
		}, 0);
		if (n[0] == 0) throw new IllegalStateException("TCharset.forName not found");
		Files.write(p, cw.toByteArray());
		System.out.println("patched " + p + ": \"UTF8\" accepted for UTF-8 (" + n[0] + " methods)");
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
