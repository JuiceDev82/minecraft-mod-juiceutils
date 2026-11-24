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
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/**
 * Chooses which blocks get a name tag, from everything in the registry including modded blocks.
 */
public class NameTagBlocksScreen extends BlockListScreen {
	private static final Component TAGGED = Component.translatable("options.juiceclient.nameTag.tagged").withStyle(ChatFormatting.GREEN);
	private static final Component NOT_TAGGED = Component.translatable("options.juiceclient.nameTag.notTagged").withStyle(ChatFormatting.GRAY);

	public NameTagBlocksScreen(Screen lastScreen) {
		super(lastScreen, Component.translatable("options.juiceclient.nameTagBlocks.title"), taggedFirst());
	}

	// Sorted once on opening, so that rows don't jump around while they're being clicked.
	private static List<List<Block>> taggedFirst() {
		List<List<Block>> groups = new ArrayList<>(blocksByName(block -> !block.defaultBlockState().isAir()).values());
		groups.sort(Comparator.comparing(group -> !isTagged(group)));
		return groups;
	}

	private static boolean isTagged(List<Block> group) {
		return group.stream().allMatch(JuiceClient.CONFIG.nameTagBlocks()::contains);
	}

	@Override
	protected AbstractWidget createRow(Component name, List<Block> group) {
		return CycleButton.booleanBuilder(TAGGED, NOT_TAGGED, isTagged(group)).create(name, (button, value) -> {
			group.forEach(block -> JuiceClient.CONFIG.setNameTagBlock(block, value));
			JuiceClient.NAME_TAGS.rescanSoon();
		});
	}

	@Override
	protected void addFooter() {
		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(Component.translatable("options.juiceclient.putAllItems.clear"), button -> {
			JuiceClient.CONFIG.clearNameTagBlocks();
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
