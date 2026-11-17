package com.basetracer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import com.basetracer.HeldUse;

@Mixin(KeyMapping.class)
public class KeyMappingMixin {
	// While an item is used for the player, by auto eat or quick use, the use key reads as held, as eating needs it
	// to be. Pressing it for real would also use whatever block is being looked at.
	@ModifyReturnValue(method = "isDown", at = @At("RETURN"))
	private boolean basetracer$holdUseWhileEating(boolean down) {
		if (down || !HeldUse.isActive()) {
			return down;
		}

		Minecraft client = Minecraft.getInstance();
		return client != null && client.options != null && (Object) this == client.options.keyUse;
	}
}
