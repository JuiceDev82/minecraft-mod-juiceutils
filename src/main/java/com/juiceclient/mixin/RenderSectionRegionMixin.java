package com.juiceclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import com.juiceclient.XRay;

/** What chunk meshes are built from. Hidden blocks are air to it, so their neighbors' faces aren't culled either. */
@Mixin(RenderSectionRegion.class)
public abstract class RenderSectionRegionMixin {
	@ModifyReturnValue(method = "getBlockState", at = @At("RETURN"))
	private BlockState juiceclient$xrayBlock(BlockState state) {
		return XRay.hides(state) ? Blocks.AIR.defaultBlockState() : state;
	}

	@ModifyReturnValue(method = "getFluidState", at = @At("RETURN"))
	private FluidState juiceclient$xrayFluid(FluidState fluid, @Local(argsOnly = true) BlockPos pos) {
		return XRay.isActive() && !fluid.isEmpty() && ((RenderSectionRegion) (Object) this).getBlockState(pos).isAir() ? Fluids.EMPTY.defaultFluidState() : fluid;
	}
}
