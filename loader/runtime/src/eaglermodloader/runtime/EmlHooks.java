package eaglermodloader.runtime;

import java.io.File;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.impl.FabricLoaderImpl;

/**
 * Where the game hands control to the mods. Called from mixins in the built-in "eaglermodloader" mod:
 * startClient() in the Minecraft constructor, the same place Fabric Loader starts mods on the client
 * (after Bootstrap.bootStrap(), with the registries not yet frozen: Fabric API freezes them later), and
 * afterBootstrap() at the end of Bootstrap.bootStrap(), which starts the mods in the singleplayer server worker.
 */
public final class EmlHooks {
	private static boolean common, client, clientProgram;

	private EmlHooks() {
	}

	/** Called at the start of the client's main(); the server worker never calls it. */
	public static void markClient() {
		clientProgram = true;
	}

	public static void afterBootstrap() {
		if (clientProgram || common) return;
		startCommon();
		GeneratedMods.freezeRegistries();
	}

	public static void startCommon() {
		if (common) return;
		common = true;
		System.out.println("[eagler-modloader] " + FabricLoaderImpl.INSTANCE.containers().size() + " built-in mods (Fabric Loader " + FabricLoaderImpl.VERSION + ")");
		runMain();
	}

	/**
	 * Runs every "main" entrypoint that hasn't run yet. The client bootstraps (and so runs the common
	 * ones) before the Minecraft constructor adds the client-only mods, whose "main" entrypoints then run here.
	 */
	private static void runMain() {
		// (TeaVM's Throwable.addSuppressed isn't usable here, so each failure is reported on its own)
		RuntimeException err = null;
		for (EmlEntrypoint e : GeneratedMods.entrypoints("main")) {
			if (e.ran) continue;
			e.ran = true;
			try {
				Object o = e.get();
				if (o instanceof ModInitializer) ((ModInitializer) o).onInitialize();
			} catch (Throwable t) {
				System.err.println("[eagler-modloader] main entrypoint " + e.value + " of mod '" + e.modId + "' failed: " + t);
				t.printStackTrace();
				if (err == null) err = new RuntimeException("Could not execute entrypoint stage 'main' due to errors, provided by '" + e.modId + "' (" + e.value + ")", t);
			}
		}
		if (err != null) throw err;
	}

	public static void startClient(File runDir, Object game) {
		if (client) return;
		client = true;
		FabricLoaderImpl.INSTANCE.setGameInstance(game);
		GeneratedMods.enableClient();
		startCommon();
		runMain(); // client-only mods' "main" entrypoints
		FabricLoaderImpl.INSTANCE.invokeEntrypoints("client", ClientModInitializer.class, ClientModInitializer::onInitializeClient);
		GeneratedMods.afterClientStart();
	}
}
