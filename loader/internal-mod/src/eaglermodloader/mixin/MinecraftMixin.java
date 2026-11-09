package eaglermodloader.mixin;

import java.io.File;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import eaglermodloader.runtime.EmlHooks;
import eaglermodloader.runtime.GeneratedMods;

/**
 * Starts client mods in the Minecraft constructor, right after the game directory is set (as Fabric does),
 * and lets mods' screen hooks see Eaglercraft's "starting integrated server" screen while a world loads.
 */
@Mixin(targets = "net.minecraft.client.Minecraft")
public abstract class MinecraftMixin {
	@Shadow @Final public File gameDirectory;

	@Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;gameDirectory:Ljava/io/File;", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
	private void eml$startClient(CallbackInfo ci) {
		EmlHooks.startClient(this.gameDirectory, this);
	}

	/**
	 * While the singleplayer worker starts, Eaglercraft ticks its own busy screen instead of the
	 * level loading screen, so injections before LevelLoadingScreen.tick() never run but those before
	 * renderFrame() do (Fabric API's screen events then get a null screen). Run the "before" part here.
	 */
	@Inject(method = "doWorldLoad", at = @At(value = "INVOKE", target = "Lnet/lax1dude/eaglercraft/v1_8/sp/gui/EaglerIntegratedServerBusyScreen;tick()V"))
	private void eml$beforeBusyScreenTick(CallbackInfo ci) {
		GeneratedMods.beforeBusyScreenTick(this);
	}
}
