package com.basetracer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int basetracer$leftPos();

	@Accessor("topPos")
	int basetracer$topPos();

	@Accessor("imageWidth")
	int basetracer$imageWidth();

	@Accessor("inventoryLabelY")
	int basetracer$inventoryLabelY();
}
