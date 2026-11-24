package com.juiceclient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.ChatFormatting;
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
 * Chooses the items on the Put All list, from everything in the registry including modded items. Whether the list is
 * what Put All moves, or what it leaves, is the Put All Filter setting.
 */
public class PutAllItemsScreen extends SearchListScreen<Item> {
	private static final Component LISTED = Component.translatable("options.juiceclient.putAllItems.listed").withStyle(ChatFormatting.GREEN);
	private static final Component NOT_LISTED = Component.translatable("options.juiceclient.putAllItems.notListed").withStyle(ChatFormatting.GRAY);

	public PutAllItemsScreen(Screen lastScreen) {
		super(lastScreen, Component.translatable("options.juiceclient.putAllItems.title"), listedFirst());
	}

	// Sorted once on opening, so that rows don't jump around while they're being clicked.
	private static List<Item> listedFirst() {
		List<Item> items = new ArrayList<>();

		for (Item item : BuiltInRegistries.ITEM) {
			if (item != Items.AIR) {
				items.add(item);
			}
		}

		items.sort(Comparator.comparing((Item item) -> !JuiceClient.CONFIG.isPutAllListed(item))
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
		return CycleButton.booleanBuilder(LISTED, NOT_LISTED, JuiceClient.CONFIG.isPutAllListed(item))
				.create(name, (button, value) -> JuiceClient.CONFIG.setPutAllItem(item, value));
	}

	@Override
	protected void addFooter() {
		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(Component.translatable("options.juiceclient.putAllItems.clear"), button -> {
			JuiceClient.CONFIG.clearPutAllItems();
			this.showRows(this.search.getValue());
		}).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).build());
	}

	@Override
	public void removed() {
		super.removed();
		JuiceClient.CONFIG.save();
	}
}
