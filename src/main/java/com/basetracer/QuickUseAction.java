package com.basetracer;

import java.util.function.Predicate;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** What the Quick Use key uses. */
public enum QuickUseAction {
	ENDER_PEARL("ender_pearl", stack -> stack.is(Items.ENDER_PEARL)),
	POTION("potion", stack -> stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)),
	ENCHANTED_APPLE("enchanted_apple", stack -> stack.is(Items.ENCHANTED_GOLDEN_APPLE));

	private final Component label;
	private final Predicate<ItemStack> matches;

	QuickUseAction(String key, Predicate<ItemStack> matches) {
		this.label = Component.translatable("options.basetracer.quickUse." + key);
		this.matches = matches;
	}

	public Component label() {
		return this.label;
	}

	public boolean matches(ItemStack stack) {
		return !stack.isEmpty() && this.matches.test(stack);
	}
}
