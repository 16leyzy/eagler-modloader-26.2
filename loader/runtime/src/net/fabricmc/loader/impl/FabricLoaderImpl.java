package net.fabricmc.loader.impl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import eaglermodloader.runtime.EmlEntrypoint;
import eaglermodloader.runtime.EmlHooks;
import eaglermodloader.runtime.EmlModContainer;
import eaglermodloader.runtime.GeneratedMods;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.ObjectShare;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.impl.metadata.DependencyOverrides;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.ModMetadataParser;
import net.fabricmc.loader.impl.metadata.VersionOverrides;

/**
 * Fabric Loader for the browser (replaces the real FabricLoaderImpl in the TeaVM build only).
 *
 * The real loader discovers, resolves and transforms mods at startup. Here that already happened at
 * build time, so this only serves the loader API from the list of built-in mods (GeneratedMods):
 * metadata, entrypoints, the object share and an identity mapping resolver.
 */
public final class FabricLoaderImpl implements FabricLoader {
	public static final FabricLoaderImpl INSTANCE = new FabricLoaderImpl();
	public static final String VERSION = GeneratedMods.LOADER_VERSION;

	private final Map<String, EmlModContainer> mods = new LinkedHashMap<>();
	private final ObjectShare objectShare = new ObjectShareImpl();
	private Object gameInstance;
	private boolean loaded;

	private FabricLoaderImpl() {
	}

	private synchronized void load() {
		if (loaded) return;
		loaded = true;
		VersionOverrides vo = new VersionOverrides();
		DependencyOverrides dov = new DependencyOverrides(Paths.get("config"));
		for (String[] m : GeneratedMods.MODS) { // { id, parentId, fabric.mod.json }
			try {
				LoaderModMetadata md = ModMetadataParser.parseMetadata(
						new ByteArrayInputStream(m[2].getBytes(StandardCharsets.UTF_8)), m[0], Collections.emptyList(), vo, dov, false);
				mods.put(md.getId(), new EmlModContainer(md, m[1]));
			} catch (Throwable t) {
				System.err.println("[eagler-modloader] could not read metadata of " + m[0] + ": " + t);
			}
		}
		for (EmlModContainer c : mods.values()) {
			if (c.parentId != null) {
				EmlModContainer p = mods.get(c.parentId);
				if (p != null) { c.parent = p; p.children.add(c); }
			}
		}
	}

	public Collection<EmlModContainer> containers() {
		load();
		return mods.values();
	}

	@Override
	public <T> List<T> getEntrypoints(String key, Class<T> type) {
		List<T> ret = new ArrayList<>();
		for (EntrypointContainer<T> c : getEntrypointContainers(key, type)) ret.add(c.getEntrypoint());
		return ret;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
		load();
		List<EntrypointContainer<T>> ret = new ArrayList<>();
		for (EmlEntrypoint e : GeneratedMods.entrypoints(key)) {
			EmlModContainer mod = mods.get(e.modId);
			if (mod == null) continue;
			try {
				Object o = e.get();
				if (o == null) continue;
				if (type != null && !type.isInstance(o)) {
					throw new ClassCastException(e.value + " is not a " + type.getName());
				}
				final T t = (T) o;
				ret.add(new EntrypointContainer<T>() {
					@Override public T getEntrypoint() { return t; }
					@Override public ModContainer getProvider() { return mod; }
					@Override public String getDefinition() { return e.value; }
				});
			} catch (Throwable t) {
				// skipped, not fatal: one broken mod shouldn't stop the game (see EmlHooks.failed)
				EmlHooks.failed(e.modId, "entrypoint '" + key + "' " + e.value, t);
			}
		}
		return ret;
	}

	@Override
	public <T> void invokeEntrypoints(String key, Class<T> type, Consumer<? super T> invoker) {
		for (EntrypointContainer<T> c : getEntrypointContainers(key, type)) {
			try {
				invoker.accept(c.getEntrypoint());
			} catch (Throwable t) {
				EmlHooks.failed(c.getProvider().getMetadata().getId(), key, t);
			}
		}
	}

	@Override public ObjectShare getObjectShare() { return objectShare; }
	@Override public MappingResolver getMappingResolver() { return IdentityMappingResolver.INSTANCE; }

	@Override
	public Optional<ModContainer> getModContainer(String id) {
		load();
		return Optional.ofNullable(mods.get(id));
	}

	@Override
	public Collection<ModContainer> getAllMods() {
		load();
		return Collections.unmodifiableCollection(new ArrayList<>(mods.values()));
	}

	@Override
	public boolean isModLoaded(String id) {
		load();
		return mods.containsKey(id);
	}

	@Override public boolean isDevelopmentEnvironment() { return false; }
	@Override public EnvType getEnvironmentType() { return EnvType.CLIENT; }
	@Override public String getRawGameVersion() { return GeneratedMods.GAME_VERSION; }
	@Override public Object getGameInstance() { return gameInstance; }
	public void setGameInstance(Object game) { this.gameInstance = game; }
	@Override public Path getGameDir() { return Paths.get("."); }
	@Override public File getGameDirectory() { return new File("."); }
	@Override public Path getConfigDir() { return Paths.get("config"); }
	@Override public File getConfigDirectory() { return new File("config"); }
	@Override public String[] getLaunchArguments(boolean sanitize) { return new String[0]; }

	private static final class IdentityMappingResolver implements MappingResolver {
		static final IdentityMappingResolver INSTANCE = new IdentityMappingResolver();
		@Override public Collection<String> getNamespaces() { return Collections.singletonList("official"); }
		@Override public String getCurrentRuntimeNamespace() { return "official"; }
		@Override public String mapClassName(String namespace, String className) { return className; }
		@Override public String unmapClassName(String targetNamespace, String className) { return className; }
		@Override public String mapFieldName(String namespace, String owner, String name, String descriptor) { return name; }
		@Override public String mapMethodName(String namespace, String owner, String name, String descriptor) { return name; }
	}
}
