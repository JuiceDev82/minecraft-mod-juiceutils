package com.juiceclient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

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

/** Chooses a list of items, such as the ones Auto Drop throws out, from everything in the registry including modded items. */
public class ItemListScreen extends SearchListScreen<Item> {
	private final Component on;
	private final Component off;
	private final Predicate<Item> listed;
	private final BiConsumer<Item, Boolean> setListed;
	private final Runnable clear;

	private ItemListScreen(Screen lastScreen, Component title, Component on, Component off, Predicate<Item> listed, BiConsumer<Item, Boolean> setListed, Runnable clear) {
		super(lastScreen, title, listedFirst(listed));
		this.on = on;
		this.off = off;
		this.listed = listed;
		this.setListed = setListed;
		this.clear = clear;
	}

	public static ItemListScreen autoDrop(Screen lastScreen) {
		JuiceClientConfig config = JuiceClient.CONFIG;
		return new ItemListScreen(lastScreen, Component.translatable("options.juiceclient.autoDropItems.title"),
				Component.translatable("options.juiceclient.autoDropItems.drop").withStyle(net.minecraft.ChatFormatting.RED),
				Component.translatable("options.juiceclient.autoDropItems.keep").withStyle(net.minecraft.ChatFormatting.GRAY),
				config::isAutoDropListed, config::setAutoDropItem, config::clearAutoDropItems);
	}

	public static ItemListScreen antiDrop(Screen lastScreen) {
		JuiceClientConfig config = JuiceClient.CONFIG;
		return new ItemListScreen(lastScreen, Component.translatable("options.juiceclient.antiDropItems.title"),
				Component.translatable("options.juiceclient.antiDropItems.protect").withStyle(net.minecraft.ChatFormatting.GREEN),
				Component.translatable("options.juiceclient.antiDropItems.allow").withStyle(net.minecraft.ChatFormatting.GRAY),
				config::isAntiDropListed, config::setAntiDropItem, config::clearAntiDropItems);
	}

	// Sorted once on opening, so that rows don't jump around while they're being clicked.
	private static List<Item> listedFirst(Predicate<Item> listed) {
		List<Item> items = new ArrayList<>();

		for (Item item : BuiltInRegistries.ITEM) {
			if (item != Items.AIR) {
				items.add(item);
			}
		}

		items.sort(Comparator.comparing((Item item) -> !listed.test(item))
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
		return CycleButton.booleanBuilder(this.on, this.off, this.listed.test(item))
				.create(name, (button, value) -> this.setListed.accept(item, value));
	}

	@Override
	protected void addFooter() {
		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(Component.translatable("options.juiceclient.putAllItems.clear"), button -> {
			this.clear.run();
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
