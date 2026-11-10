package eaglermodloader.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;

/**
 * Reflection help for mods (see loader/weaver ReflectionScan). The browser build has no generic types,
 * so mods' Field.getGenericType() calls are pointed here and answered from the table built from the
 * class files at build time.
 */
public final class EmlReflect {
	private EmlReflect() {
	}

	public static Type getGenericType(Field f) {
		Type t = GeneratedMods.genericFieldType(f.getDeclaringClass().getName() + "#" + f.getName());
		return t != null ? t : f.getType();
	}

	/** A parameterized type such as EnumConfigOption&lt;Sorting&gt;. */
	public static final class Parameterized implements ParameterizedType {
		private final Class<?> raw;
		private final Type[] args;

		public Parameterized(Class<?> raw, Type[] args) {
			this.raw = raw;
			this.args = args;
		}

		@Override public Type[] getActualTypeArguments() { return args.clone(); }
		@Override public Type getRawType() { return raw; }
		@Override public Type getOwnerType() { return null; }

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder(raw.getName()).append('<');
			for (int i = 0; i < args.length; i++) {
				if (i > 0) sb.append(", ");
				sb.append(args[i] instanceof Class<?> c ? c.getName() : String.valueOf(args[i]));
			}
			return sb.append('>').toString();
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Parameterized p && p.raw == raw && Arrays.equals(p.args, args);
		}

		@Override
		public int hashCode() {
			return raw.hashCode() * 31 + Arrays.hashCode(args);
		}
	}
}
