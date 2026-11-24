package com.juiceclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int juiceclient$leftPos();

	@Accessor("topPos")
	int juiceclient$topPos();

	@Accessor("imageWidth")
	int juiceclient$imageWidth();

	@Accessor("inventoryLabelY")
	int juiceclient$inventoryLabelY();
}
