package net.lax1dude.eaglercraft.v1_8.teavmcompat.inject;

import java.util.Iterator;
import java.util.Optional;

/** Donor for java.util.ServiceLoader (see plugin.JdkMethodInjector; added by eagler-modloader). */
@SuppressWarnings({ "rawtypes", "unchecked" })
public final class ServiceLoaderInject {

	/** placeholder: the target already implements iterator (never copied) */
	public Iterator iterator() {
		return null;
	}

	public Optional findFirst() {
		Iterator it = iterator();
		return it.hasNext() ? Optional.of(it.next()) : Optional.empty();
	}
}
