package com.juiceclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.player.LocalPlayer;

import com.juiceclient.AntiDrop;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
	// The drop key outside a screen, before anything is predicted or sent.
	@Inject(method = "drop", at = @At("HEAD"), cancellable = true)
	private void juiceclient$antiDrop(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
		if (AntiDrop.blocksDrop((LocalPlayer) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
