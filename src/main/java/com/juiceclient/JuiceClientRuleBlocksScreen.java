package com.juiceclient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/**
 * Chooses a rule's blocks from everything in the registry, including modded blocks.
 */
public class JuiceClientRuleBlocksScreen extends BlockListScreen {
	private static final Component IN_RULE = Component.translatable("options.juiceclient.block.inRule").withStyle(ChatFormatting.GREEN);
	private static final Component NOT_IN_RULE = Component.translatable("options.juiceclient.block.notInRule").withStyle(ChatFormatting.GRAY);

	private final Screen rulesParent;
	private final BaseRule rule;

	public JuiceClientRuleBlocksScreen(Screen lastScreen, Screen rulesParent, BaseRule rule) {
		super(lastScreen, Component.translatable("options.juiceclient.ruleBlocks.title", rule.name), chosenFirst(rule));
		this.rulesParent = rulesParent;
		this.rule = rule;
	}

	// Sorted once on opening, so that rows don't jump around while they're being clicked.
	private static List<List<Block>> chosenFirst(BaseRule rule) {
		List<List<Block>> groups = new ArrayList<>(blocksByName(block -> !block.defaultBlockState().isAir()).values());
		groups.sort(Comparator.comparing(group -> !isChosen(rule, group)));
		return groups;
	}

	private static boolean isChosen(BaseRule rule, List<Block> group) {
		return group.stream().allMatch(rule.blocks()::contains);
	}

	@Override
	protected AbstractWidget createRow(Component name, List<Block> group) {
		return CycleButton.booleanBuilder(IN_RULE, NOT_IN_RULE, isChosen(this.rule, group)).create(name, (button, value) -> {
			group.forEach(block -> this.rule.setBlock(block, value));
			JuiceClient.CONFIG.markRulesChanged();
		});
	}

	@Override
	public void onClose() {
		// A new screen, so it shows the new block count.
		this.minecraft.gui.setScreen(new JuiceClientRuleScreen(this.rulesParent, this.rule));
	}

	@Override
	public void removed() {
		super.removed();
		JuiceClient.applyRuleChanges();
	}
}
