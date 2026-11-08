package eaglermodloader.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import eaglermodloader.runtime.EmlHooks;

/**
 * After the game bootstraps its registries: in the singleplayer server worker (its own program, which
 * calls Bootstrap.bootStrap() but has no Minecraft constructor), this is where the mods start.
 * On the client it does nothing; the mods start in the Minecraft constructor, as with Fabric.
 */
@Mixin(targets = "net.minecraft.server.Bootstrap")
public abstract class BootstrapMixin {
	@Inject(method = "bootStrap", at = @At("TAIL"))
	private static void eml$afterBootstrap(CallbackInfo ci) {
		EmlHooks.afterBootstrap();
	}
}
