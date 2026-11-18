package com.basetracer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import com.basetracer.BaseTracerRenderer;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	// Highlighted mobs get the same outline as the glowing effect, which follows the mob's shape and shows through walls.
	@ModifyReturnValue(method = "shouldEntityAppearGlowing", at = @At("RETURN"))
	private boolean basetracer$outlineMobs(boolean glowing, Entity entity) {
		return glowing || BaseTracerRenderer.isOutlined(entity);
	}
}
