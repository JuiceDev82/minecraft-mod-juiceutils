package com.juiceclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.BlockAndLightGetter;

import com.juiceclient.XRay;

/**
 * Everything is fully lit with X-Ray on, since what it shows is mostly deep in the dark. That covers chunk meshes, and
 * the world for chests and other blocks drawn on their own, along with entities. Sodium builds meshes from a
 * BlockAndTintGetter of its own, so that covers it as well.
 */
@Mixin(LightCoordsUtil.class)
public abstract class LightCoordsUtilMixin {
	@ModifyReturnValue(method = "getLightCoords(Lnet/minecraft/util/LightCoordsUtil$BrightnessGetter;Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"))
	private static int juiceclient$xrayFullBright(int light, @Local(argsOnly = true) BlockAndLightGetter level) {
		return XRay.isActive() && (level instanceof BlockAndTintGetter || level instanceof ClientLevel) ? LightCoordsUtil.pack(15, 15) : light;
	}
}
