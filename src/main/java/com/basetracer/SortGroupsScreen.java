package com.basetracer;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The order Sort by Type puts its groups in. A group is moved like an item in the inventory: click it to pick it up,
 * then click the place it goes.
 */
public class SortGroupsScreen extends OptionsSubScreen {
	private static final int FOOTER_BUTTON_WIDTH = 150;

	/** The group picked up to move, or null. */
	private final ItemGroup picked;

	public SortGroupsScreen(Screen lastScreen) {
		this(lastScreen, null);
	}

	private SortGroupsScreen(Screen lastScreen, ItemGroup picked) {
		super(lastScreen, Minecraft.getInstance().options, Component.translatable("options.basetracer.sortGroups.title"));
		this.picked = picked;
	}

	@Override
	protected void addOptions() {
		BaseTracerConfig config = BaseTracerClient.CONFIG;
		this.list.addHeader(Component.translatable(this.picked == null ? "options.basetracer.sortGroups.pick" : "options.basetracer.sortGroups.place",
				this.picked == null ? "" : this.picked.label()).withStyle(ChatFormatting.GRAY));
		List<ItemGroup> order = config.sortGroupOrder();

		for (int i = 0; i < order.size(); i++) {
			ItemGroup group = order.get(i);
			int place = i;
			Component label = Component.literal((i + 1) + ". ").append(group.label());

			if (group == this.picked) {
				label = Component.literal("> ").append(label).append(" <").withStyle(ChatFormatting.YELLOW);
			}

			this.list.addBig(Button.builder(label, button -> {
				if (this.picked == null) {
					this.reopen(group);
				} else {
					config.moveSortGroup(this.picked, place);
					this.reopen(null);
				}
			}).build());
		}
	}

	private void reopen(ItemGroup picked) {
		double scroll = this.list.scrollAmount();
		SortGroupsScreen screen = new SortGroupsScreen(this.lastScreen, picked);
		this.minecraft.gui.setScreen(screen);
		screen.list.setScrollAmount(scroll);
	}

	@Override
	protected void addFooter() {
		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(Component.translatable("options.basetracer.resetDefaults"), button -> {
			BaseTracerClient.CONFIG.resetSortGroupOrder();
			this.reopen(null);
		}).width(FOOTER_BUTTON_WIDTH).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(FOOTER_BUTTON_WIDTH).build());
	}

	@Override
	public void removed() {
		super.removed();
		BaseTracerClient.CONFIG.save();
	}
}
