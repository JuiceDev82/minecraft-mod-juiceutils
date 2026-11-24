package com.juiceclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import com.juiceclient.XRay;

/**
 * X-Ray with Sodium, which builds chunk meshes from its own copy of the world instead of the game's. The same as
 * {@link RenderSectionRegionMixin}, plus full light, since Sodium reads light from here too. Skipped without Sodium,
 * and each part on its own if a Sodium update changes it, rather than stopping the game.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice")
public abstract class SodiumLevelSliceMixin {
	private static final int FULL_LIGHT = 15;

	@ModifyReturnValue(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"), require = 0)
	private BlockState juiceclient$xrayBlockAt(BlockState state) {
		return XRay.hides(state) ? Blocks.AIR.defaultBlockState() : state;
	}

	@ModifyReturnValue(method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"), require = 0)
	private BlockState juiceclient$xrayBlock(BlockState state) {
		return XRay.hides(state) ? Blocks.AIR.defaultBlockState() : state;
	}

	@ModifyReturnValue(method = "getFluidState", at = @At("RETURN"), require = 0)
	private FluidState juiceclient$xrayFluid(FluidState fluid, @Local(argsOnly = true) BlockPos pos) {
		return XRay.isActive() && !fluid.isEmpty() && ((BlockGetter) this).getBlockState(pos).isAir() ? Fluids.EMPTY.defaultFluidState() : fluid;
	}

	@ModifyReturnValue(method = "getBrightness", at = @At("RETURN"), require = 0)
	private int juiceclient$xrayBrightness(int light) {
		return XRay.isActive() ? FULL_LIGHT : light;
	}

	@ModifyReturnValue(method = "getRawBrightness", at = @At("RETURN"), require = 0)
	private int juiceclient$xrayRawBrightness(int light) {
		return XRay.isActive() ? FULL_LIGHT : light;
	}
}
