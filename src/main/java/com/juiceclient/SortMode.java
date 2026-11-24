package com.juiceclient;

import net.minecraft.network.chat.Component;

/** What the Sort buttons put first. Every order keeps the same items together. */
public enum SortMode {
	/** Weapons, armor, tools, food, potions, blocks, then everything else. */
	TYPE("type", "T"),
	/** The items there are most of first. */
	COUNT("count", "#"),
	/** The most valuable items first: netherite, diamonds, totems, enchanted gear and the like. */
	VALUE("value", "$");

	private final Component label;
	private final Component symbol;

	SortMode(String key, String symbol) {
		this.label = Component.translatable("options.juiceclient.sortMode." + key);
		this.symbol = Component.literal(symbol);
	}

	public Component label() {
		return this.label;
	}

	/** The short form shown on the small button beside Sort. */
	public Component symbol() {
		return this.symbol;
	}

	public SortMode next() {
		return values()[(this.ordinal() + 1) % values().length];
	}
}
