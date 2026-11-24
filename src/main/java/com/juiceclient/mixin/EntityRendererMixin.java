package com.juiceclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;

import com.juiceclient.JuiceClientRenderer;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
	// The outline of a highlighted mob, item or entity takes its highlight color, unless it's really glowing, which keeps its own.
	@ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getTeamColor()I"))
	private int juiceclient$outlineColor(int teamColor, @Local(argsOnly = true) Entity entity) {
		int color = JuiceClientRenderer.outlineColor(entity);
		return entity.isCurrentlyGlowing() || color < 0 ? teamColor : color;
	}
}
