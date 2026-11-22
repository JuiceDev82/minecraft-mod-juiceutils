package com.basetracer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Chooses the items Sort puts at the front or the back, from everything in the registry including modded items. The
 * rest go by the sort order between them.
 */
public class SortPriorityScreen extends SearchListScreen<Item> {
	public SortPriorityScreen(Screen lastScreen) {
		super(lastScreen, Component.translatable("options.basetracer.sortPriorities.title"), chosenFirst());
	}

	// Sorted once on opening, so that rows don't jump around while they're being clicked: front, back, then the rest.
	private static List<Item> chosenFirst() {
		List<Item> items = new ArrayList<>();

		for (Item item : BuiltInRegistries.ITEM) {
			if (item != Items.AIR) {
				items.add(item);
			}
		}

		items.sort(Comparator.comparingInt((Item item) -> switch (BaseTracerClient.CONFIG.sortPriority(item)) {
					case FRONT -> 0;
					case BACK -> 1;
					case NORMAL -> 2;
				})
				.thenComparing(item -> itemName(item).getString()));
		return items;
	}

	// From the item's translation rather than an ItemStack, whose data components aren't there yet on the title screen.
	private static Component itemName(Item item) {
		return Component.translatable(item.getDescriptionId());
	}

	@Override
	protected Component name(Item item) {
		return itemName(item);
	}

	@Override
	protected AbstractWidget createRow(Component name, Item item) {
		return CycleButton.builder(SortPriority::label, BaseTracerClient.CONFIG.sortPriority(item))
				.withValues(SortPriority.values())
				.create(name, (button, value) -> BaseTracerClient.CONFIG.setSortPriority(item, value));
	}

	@Override
	protected void addFooter() {
		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(Component.translatable("options.basetracer.putAllItems.clear"), button -> {
			BaseTracerClient.CONFIG.clearSortPriorities();
			this.showRows(this.search.getValue());
		}).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).build());
	}

	@Override
	public void removed() {
		super.removed();
		BaseTracerClient.CONFIG.save();
	}
}
