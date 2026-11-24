package com.juiceclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;

import com.juiceclient.AntiDrop;
import com.juiceclient.AutoWeapon;
import com.juiceclient.SmartTool;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Shadow
	private void ensureHasSentCarriedItem() {
	}

	// Runs for every new block mined, including the next one when the attack key is held.
	@Inject(method = "startDestroyBlock", at = @At("HEAD"))
	private void juiceclient$smartTool(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		Minecraft client = Minecraft.getInstance();

		if (client.player != null && client.level != null && SmartTool.startMining(client.player, client.level.getBlockState(pos), pos)) {
			// The server must know about the new tool before it's told mining has started.
			this.ensureHasSentCarriedItem();
		}
	}

	// Before the game sends the selected slot and then the hit.
	@Inject(method = "attack", at = @At("HEAD"))
	private void juiceclient$smartToolAttack(Player player, Entity target, CallbackInfo ci) {
		if (player instanceof LocalPlayer localPlayer) {
			SmartTool.attack(localPlayer, target);
			AutoWeapon.attack(localPlayer, target);
		}
	}

	// Before the click is predicted and sent, so a protected item stays where it was.
	@Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
	private void juiceclient$antiDrop(int containerId, int slotNum, int buttonNum, ContainerInput input, Player player, CallbackInfo ci) {
		if (player.containerMenu.containerId == containerId && AntiDrop.blocksClick(player.containerMenu, slotNum, input, player)) {
			ci.cancel();
		}
	}

	@Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
	private void juiceclient$smartToolWait(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		Minecraft client = Minecraft.getInstance();

		if (client.player != null && client.level != null && SmartTool.continueMining(client.player, client.level.getBlockState(pos), pos)) {
			cir.setReturnValue(true);
		}
	}
}
