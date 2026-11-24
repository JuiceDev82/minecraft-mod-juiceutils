package com.juiceclient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;

/**
 * Stacks boss bars with the same name into one, such as three withers into a single "Wither x3" bar, so a crowd of
 * bosses doesn't fill the top of the screen. The bar shown is the first of its kind, in the order the game had them.
 */
public final class BossBarStack {
	/** How many bars each one shown stands for, from the last time they were stacked. */
	private static Map<BossEvent, Integer> counts = new IdentityHashMap<>();

	private BossBarStack() {
	}

	/** One bar per name, in the order they first appear, or all of them when stacking is off. */
	public static <T extends BossEvent> Collection<T> stack(Collection<T> bars) {
		if (!JuiceClient.CONFIG.enabled || !JuiceClient.CONFIG.stackBossBars) {
			counts = new IdentityHashMap<>();
			return bars;
		}

		Map<String, T> first = new LinkedHashMap<>();
		Map<BossEvent, Integer> stacked = new IdentityHashMap<>();

		for (T bar : bars) {
			T shown = first.computeIfAbsent(bar.getName().getString(), name -> bar);
			stacked.merge(shown, 1, Integer::sum);
		}

		counts = stacked;
		return new ArrayList<>(first.values());
	}

	/** A bar's name, with how many it stands for when that's more than one. */
	public static Component name(BossEvent bar, Component name) {
		int count = counts.getOrDefault(bar, 1);
		return count > 1 ? Component.translatable("options.juiceclient.bossBarCount", name, count) : name;
	}

	/** The names shown, with counts, for tests. */
	public static List<String> shownNames(Collection<? extends BossEvent> bars) {
		List<String> names = new ArrayList<>();

		for (BossEvent bar : stack(bars)) {
			names.add(name(bar, bar.getName()).getString());
		}

		return names;
	}
}
