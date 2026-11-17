package com.basetracer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Predicate;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/**
 * A searchable list with one row per block name.
 */
abstract class BlockListScreen extends SearchListScreen<List<Block>> {
	BlockListScreen(Screen lastScreen, Component title, Collection<List<Block>> blockGroups) {
		super(lastScreen, title, blockGroups);
	}

	/** Variants such as wall torches share their block's name, so they're offered as a single row. */
	static SortedMap<String, List<Block>> blocksByName(Predicate<Block> filter) {
		SortedMap<String, List<Block>> blocksByName = new TreeMap<>();

		for (Block block : BuiltInRegistries.BLOCK) {
			if (filter.test(block)) {
				blocksByName.computeIfAbsent(block.getName().getString(), name -> new ArrayList<>()).add(block);
			}
		}

		return blocksByName;
	}

	@Override
	protected Component name(List<Block> group) {
		return group.getFirst().getName();
	}
}
