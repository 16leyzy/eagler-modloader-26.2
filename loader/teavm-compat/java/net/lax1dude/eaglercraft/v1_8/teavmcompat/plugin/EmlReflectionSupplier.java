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

/** Added by eagler-modloader: fields of the classes mods use reflection on (eml-reflect.txt). */
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
			System.out.println("[eagler-modloader] reflection: fields of " + classes.size() + " mod classes kept readable");
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
}
