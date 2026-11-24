package com.juiceclient;

import java.util.List;
import java.util.SortedMap;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lists every light-emitting block in the registry, including modded ones. Loaded chunks are rescanned on close.
 */
public class JuiceClientExcludedBlocksScreen extends BlockListScreen {
	private static final Component EXCLUDED = Component.translatable("options.juiceclient.block.excluded").withStyle(ChatFormatting.RED);
	private static final Component TRACKED = Component.translatable("options.juiceclient.block.tracked").withStyle(ChatFormatting.GREEN);

	private boolean changed;

	public JuiceClientExcludedBlocksScreen(Screen lastScreen) {
		super(lastScreen, Component.translatable("options.juiceclient.excludedBlocks.title"), lightEmittingBlocksByName().values());
	}

	public static SortedMap<String, List<Block>> lightEmittingBlocksByName() {
		return blocksByName(block -> {
			for (BlockState state : block.getStateDefinition().getPossibleStates()) {
				if (state.getLightEmission() > 0) {
					return true;
				}
			}

			return false;
		});
	}

	@Override
	protected AbstractWidget createRow(Component name, List<Block> group) {
		JuiceClientConfig config = JuiceClient.CONFIG;
		boolean excluded = group.stream().allMatch(config::isExcluded);
		return CycleButton.booleanBuilder(EXCLUDED, TRACKED, excluded).create(name, (button, value) -> {
			group.forEach(block -> config.setExcluded(block, value));
			this.changed = true;
		});
	}

	@Override
	protected void addFooter() {
		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(Component.translatable("options.juiceclient.resetDefaults"), button -> {
			JuiceClient.CONFIG.resetExcludedBlocks();
			this.changed = true;
			this.showRows(this.search.getValue());
		}).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).build());
	}

	@Override
	public void removed() {
		super.removed();

		if (this.changed) {
			this.changed = false;
			JuiceClient.CONFIG.save();
			JuiceClient.rescanLoadedChunks();
		}
	}
}
