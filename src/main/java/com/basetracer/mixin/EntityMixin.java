package com.basetracer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import com.basetracer.FreeLook;

@Mixin(Entity.class)
public class EntityMixin {
	// During free look the mouse turns the camera instead of the player.
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void basetracer$turnCamera(double mouseX, double mouseY, CallbackInfo ci) {
		if (FreeLook.isActive() && (Object) this == Minecraft.getInstance().player) {
			FreeLook.turn(mouseX, mouseY);
			ci.cancel();
		}
	}
}
