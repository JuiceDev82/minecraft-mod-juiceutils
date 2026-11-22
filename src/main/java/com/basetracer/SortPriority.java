package com.basetracer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Where Sort puts an item, ahead of the sort order: at the front, by the order, or at the back. */
public enum SortPriority {
	FRONT("front", ChatFormatting.GREEN),
	NORMAL("normal", ChatFormatting.GRAY),
	BACK("back", ChatFormatting.GOLD);

	private final Component label;

	SortPriority(String key, ChatFormatting color) {
		this.label = Component.translatable("options.basetracer.sortPriority." + key).withStyle(color);
	}

	public Component label() {
		return this.label;
	}
}
