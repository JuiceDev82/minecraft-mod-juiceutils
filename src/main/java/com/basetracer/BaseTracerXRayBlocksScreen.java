package com.basetracer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/**
 * Chooses which blocks X-Ray leaves visible, from everything in the registry including modded blocks. With X-Ray on,
 * the world behind updates as rows are clicked.
 */
public class BaseTracerXRayBlocksScreen extends BlockListScreen {
	private static final Component SHOWN = Component.translatable("options.basetracer.xray.shown").withStyle(ChatFormatting.GREEN);
	private static final Component HIDDEN = Component.translatable("options.basetracer.xray.hidden").withStyle(ChatFormatting.GRAY);

	public BaseTracerXRayBlocksScreen(Screen lastScreen) {
		super(lastScreen, Component.translatable("options.basetracer.xrayBlocks.title"), shownFirst());
	}

	// Sorted once on opening, so that rows don't jump around while they're being clicked.
	private static List<List<Block>> shownFirst() {
		List<List<Block>> groups = new ArrayList<>(blocksByName(block -> !block.defaultBlockState().isAir()).values());
		groups.sort(Comparator.comparing(group -> !isShown(group)));
		return groups;
	}

	private static boolean isShown(List<Block> group) {
		return group.stream().allMatch(BaseTracerClient.CONFIG.xrayBlocks()::contains);
	}

	@Override
	protected AbstractWidget createRow(Component name, List<Block> group) {
		return CycleButton.booleanBuilder(SHOWN, HIDDEN, isShown(group)).create(name, (button, value) -> {
			group.forEach(block -> BaseTracerClient.CONFIG.setXrayBlock(block, value));
		});
	}

	@Override
	protected void addFooter() {
		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(Component.translatable("options.basetracer.resetDefaults"), button -> {
			BaseTracerClient.CONFIG.resetXrayBlocks();
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
