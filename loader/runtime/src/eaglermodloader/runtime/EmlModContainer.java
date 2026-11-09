package eaglermodloader.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;

/**
 * A built-in mod. Its classes were compiled into the game and its files packed into assets.epk:
 * assets/ and data/ merged with the game's, everything else under eml-mods/<mod id>/ (see
 * loader/merge-assets.py). findPath() copies a file from there to the browser's game directory
 * (.eml-mods/<mod id>/<version>/...) the first time it's asked for, so mods can read it as a normal Path.
 * getRootPaths() is empty: the mod's files can be looked up but not listed.
 */
public final class EmlModContainer implements ModContainer {
	private final ModMetadata metadata;
	public final String parentId;
	public EmlModContainer parent;
	public final List<ModContainer> children = new ArrayList<>();

	public EmlModContainer(ModMetadata metadata, String parentId) {
		this.metadata = metadata;
		this.parentId = parentId;
	}

	@Override public ModMetadata getMetadata() { return metadata; }
	@Override public List<Path> getRootPaths() { return Collections.emptyList(); }

	@Override
	public synchronized Optional<Path> findPath(String file) {
		String rel = file.replace('\\', '/');
		while (rel.startsWith("/")) rel = rel.substring(1);
		if (rel.isEmpty() || rel.contains("..")) return Optional.empty();
		Path out = cachePath(rel);
		if (Files.isRegularFile(out)) return Optional.of(out);
		byte[] data = GeneratedMods.resource("eml-mods/" + metadata.getId() + "/" + rel);
		if (data == null && (rel.startsWith("assets/") || rel.startsWith("data/"))) data = GeneratedMods.resource(rel);
		if (data == null) return Optional.empty();
		try {
			Files.createDirectories(out.getParent());
			Files.write(out, data);
			return Optional.of(out);
		} catch (IOException | RuntimeException e) {
			System.err.println("[eagler-modloader] could not unpack " + rel + " of " + metadata.getId() + ": " + e);
			return Optional.empty();
		}
	}

	private Path cachePath(String rel) {
		return getRootPath().resolve(rel);
	}

	@Override
	public ModOrigin getOrigin() {
		return new ModOrigin() {
			@Override public Kind getKind() { return parent != null ? Kind.NESTED : Kind.UNKNOWN; }
			@Override public List<Path> getPaths() { throw new UnsupportedOperationException("built-in mod"); }
			@Override public String getParentModId() { return parentId; }
			@Override public String getParentSubLocation() { throw new UnsupportedOperationException("built-in mod"); }
			@Override public String toString() { return "built into the game"; }
		};
	}

	@Override public Optional<ModContainer> getContainingMod() { return Optional.ofNullable(parent); }
	@Override public Collection<ModContainer> getContainedMods() { return Collections.unmodifiableList(children); }
	/** Where this mod's files are unpacked; per version, so an updated mod never sees old files. */
	@Override @Deprecated public Path getRootPath() { return Paths.get(".eml-mods", metadata.getId(), metadata.getVersion().getFriendlyString()); }
	@Override @Deprecated public Path getPath(String file) {
		Optional<Path> p = findPath(file);
		return p.isPresent() ? p.get() : getRootPath().resolve(file.startsWith("/") ? file.substring(1) : file);
	}
	@Override public String toString() { return metadata.getId() + " " + metadata.getVersion(); }
}
