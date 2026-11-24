package com.juiceclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import com.juiceclient.JuiceClient;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {
	// Level#setBlock calls this for every real state change, covering block update packets and client prediction.
	@Inject(method = "setBlocksDirty", at = @At("HEAD"))
	private void juiceclient$onBlockChanged(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
		JuiceClient.TRACKER.blockChanged((ClientLevel) (Object) this, pos, oldState, newState);
	}
}
