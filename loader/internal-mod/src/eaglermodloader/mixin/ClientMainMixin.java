package eaglermodloader.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import eaglermodloader.runtime.EmlHooks;

/** Marks the client program, so Bootstrap leaves starting the mods to the Minecraft constructor. */
@Mixin(targets = "net.minecraft.client.main.Main")
public abstract class ClientMainMixin {
	@Inject(method = "main", at = @At("HEAD"))
	private static void eml$markClient(CallbackInfo ci) {
		EmlHooks.markClient();
	}
}
