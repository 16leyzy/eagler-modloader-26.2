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
	 * A mod that fails to start is reported and skipped, so one broken mod doesn't stop the game (often
	 * it uses something the browser doesn't have).
	 */
	private static void runMain() {
		for (EmlEntrypoint e : GeneratedMods.entrypoints("main")) {
			if (e.ran) continue;
			e.ran = true;
			try {
				Object o = e.get();
				if (o instanceof ModInitializer) ((ModInitializer) o).onInitialize();
			} catch (Throwable t) {
				failed(e.modId, "main", t);
			}
		}
	}

	private static final java.util.Set<String> failedHandlers = new java.util.HashSet<>();

	/**
	 * A mod's @Inject handler threw; it was skipped (see loader/weaver HandlerGuard). Reported once
	 * per handler, since it may run every frame.
	 */
	public static void handlerFailed(Throwable t, String modId, String where) {
		synchronized (failedHandlers) {
			if (!failedHandlers.add(where)) return;
		}
		System.err.println("[eagler-modloader] mod '" + modId + "' hook in " + where + " failed and is skipped: " + t);
		t.printStackTrace();
	}

	/** Reports a mod that failed to start; the game carries on without it. */
	public static void failed(String modId, String stage, Throwable t) {
		System.err.println("[eagler-modloader] mod '" + modId + "' failed to start (" + stage + "), continuing without it: " + t);
		t.printStackTrace();
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
