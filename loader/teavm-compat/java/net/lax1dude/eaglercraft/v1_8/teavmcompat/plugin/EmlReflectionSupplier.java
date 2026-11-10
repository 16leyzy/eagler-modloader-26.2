package net.lax1dude.eaglercraft.v1_8.teavmcompat.plugin;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.teavm.classlib.ReflectionContext;
import org.teavm.classlib.ReflectionSupplier;
import org.teavm.model.ClassReader;
import org.teavm.model.FieldReader;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;

/**
 * Added by eagler-modloader: the fields and constructors of the classes mods use reflection on
 * (eml-reflect.txt, written by the weave), so Field.get/set, Constructor.newInstance and Gson work for them.
 */
public class EmlReflectionSupplier implements ReflectionSupplier {
	private Set<String> classes;

	private synchronized Set<String> classes(ReflectionContext context) {
		if (classes == null) {
			classes = new HashSet<>();
			try (InputStream in = context.getClassLoader().getResourceAsStream("eml-reflect.txt")) {
				if (in != null) {
					for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
						if (line.startsWith("C ")) classes.add(line.substring(2).trim());
					}
				}
			} catch (Exception e) {
				System.err.println("[eagler-modloader] could not read eml-reflect.txt: " + e);
			}
			System.out.println("[eagler-modloader] reflection: fields and constructors of " + classes.size() + " mod classes kept available to reflection");
		}
		return classes;
	}

	@Override
	public Collection<String> getAccessibleFields(ReflectionContext context, String className) {
		if (!classes(context).contains(className)) return Collections.emptyList();
		ClassReader cls = context.getClassSource().get(className);
		if (cls == null) return Collections.emptyList();
		List<String> out = new ArrayList<>();
		for (FieldReader f : cls.getFields()) out.add(f.getName());
		return out;
	}

	@Override
	public Collection<MethodDescriptor> getAccessibleMethods(ReflectionContext context, String className) {
		if (!classes(context).contains(className)) return Collections.emptyList();
		ClassReader cls = context.getClassSource().get(className);
		if (cls == null) return Collections.emptyList();
		List<MethodDescriptor> out = new ArrayList<>();
		// constructors only: settings libraries and Gson create objects this way. Keeping every method
		// would keep everything those methods call, which is too much for the compiler with many mods.
		for (MethodReader m : cls.getMethods()) {
			if (m.getName().equals("<init>")) out.add(m.getDescriptor());
		}
		return out;
	}

	/** Lets Class.forName find them (see EmlHooks.reflectionRoots); listed up front so it covers all of them. */
	@Override
	public Collection<String> getClassesFoundByName(ReflectionContext context) {
		List<String> out = new ArrayList<>();
		for (String c : classes(context)) if (context.getClassSource().get(c) != null) out.add(c);
		return out;
	}

	/** Lets Class.forName find them (see EmlHooks.reflectionRoots). */
	@Override
	public boolean isClassFoundByName(ReflectionContext context, String name) {
		return classes(context).contains(name);
	}
}
