package com.basetracer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;

import com.basetracer.BaseTracerClient;
import com.basetracer.BaseTracerRenderer;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
	// The outline of a highlighted mob takes the mob highlight color, unless it's really glowing, which keeps its own.
	@ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getTeamColor()I"))
	private int basetracer$outlineColor(int teamColor, @Local(argsOnly = true) Entity entity) {
		if (entity.isCurrentlyGlowing() || !BaseTracerRenderer.isOutlined(entity)) {
			return teamColor;
		}

		return BaseTracerClient.CONFIG.hostileMobArgb() & 0xFFFFFF;
	}
}
