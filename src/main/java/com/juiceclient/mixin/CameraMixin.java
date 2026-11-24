package com.juiceclient.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.juiceclient.FreeLook;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private boolean detached;
	@Shadow
	private Level level;
	@Shadow
	@Final
	private BlockPos.MutableBlockPos blockPosition;
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Inject(method = "alignWithEntity", at = @At("RETURN"))
	private void juiceclient$applyFreeLook(float partialTicks, CallbackInfo ci) {
		if (FreeLook.isActive()) {
			FreeLook.advance(this.minecraft);
			this.setRotation(FreeLook.yaw(), FreeLook.pitch());
			this.setPosition(FreeLook.position());
			// Makes the game draw the player's own body.
			this.detached = true;
		}
	}

	// The same check vanilla makes for spectators, so that nothing vanishes while flying through a solid block.
	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void juiceclient$disableCullingInsideBlocks(CameraRenderState cameraState, float partialTicks, CallbackInfo ci) {
		if (FreeLook.isActive() && this.level.getBlockState(this.blockPosition).isSolidRender()) {
			cameraState.smartCull = false;
		}
	}
}
