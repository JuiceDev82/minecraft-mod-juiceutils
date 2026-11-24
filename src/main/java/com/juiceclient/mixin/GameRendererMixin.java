package com.juiceclient.mixin;

import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;

import com.juiceclient.FreeLook;
import com.juiceclient.JuiceClient;
import com.juiceclient.JuiceClientRenderer;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	// After the level is finished but before the hand. Shader packs composite their final image at the end of
	// the level render, which would overwrite anything drawn during it (including LevelRenderEvents).
	@Inject(method = "renderLevel", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = "ldc=hand"))
	private void juiceclient$afterLevel(DeltaTracker deltaTracker, CallbackInfo ci) {
		JuiceClient.RENDERER.render();
	}

	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void juiceclient$hideHandInFreeLook(CameraRenderState cameraState, float deltaPartialTick, Matrix4fc modelViewMatrix, CallbackInfo ci) {
		if (FreeLook.isActive()) {
			ci.cancel();
		}
	}

	// The outline would mark what the player's body is looking at, not what the camera sees.
	@Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
	private void juiceclient$hideOutlineInFreeLook(CallbackInfoReturnable<Boolean> cir) {
		if (FreeLook.isActive()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "close", at = @At("RETURN"))
	private void juiceclient$onClose(CallbackInfo ci) {
		JuiceClientRenderer.close();
	}
}
