package com.basetracer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Lists the rules that mark a chunk as a base, each with its own on/off switch, highest priority first so that the
 * order bases are picked in can be seen at a glance.
 */
public class BaseTracerRulesScreen extends OptionsSubScreen {
	private static final Component ON = Component.translatable("options.basetracer.rule.on").withStyle(ChatFormatting.GREEN);
	private static final Component OFF = Component.translatable("options.basetracer.rule.off").withStyle(ChatFormatting.RED);
	private static final int FOOTER_BUTTON_WIDTH = 100;

	public BaseTracerRulesScreen(Screen lastScreen) {
		super(lastScreen, Minecraft.getInstance().options, Component.translatable("options.basetracer.rules.title"));
	}

	@Override
	protected void addOptions() {
		BaseTracerConfig config = BaseTracerClient.CONFIG;

		for (BaseRule rule : byPriority(config.rules)) {
			this.list.addSmall(
					CycleButton.booleanBuilder(ON, OFF, rule.enabled).create(Component.literal(rule.name), (button, value) -> {
						rule.enabled = value;
						config.markRulesChanged();
					}),
					Button.builder(
							Component.translatable("options.basetracer.rule.editWithPriority", rule.priority),
							button -> this.minecraft.gui.setScreen(new BaseTracerRuleScreen(this.lastScreen, rule))
					).build()
			);
		}
	}

	/** Highest priority first, keeping the rules' own order among equals. */
	public static List<BaseRule> byPriority(List<BaseRule> rules) {
		List<BaseRule> sorted = new ArrayList<>(rules);
		sorted.sort(Comparator.comparingInt((BaseRule rule) -> rule.priority).reversed());
		return sorted;
	}

	@Override
	protected void addFooter() {
		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(Component.translatable("options.basetracer.rules.add"), button -> {
			BaseRule rule = new BaseRule("New Rule", List.of(), 1, 1);
			BaseTracerClient.CONFIG.rules.add(rule);
			BaseTracerClient.CONFIG.markRulesChanged();
			this.minecraft.gui.setScreen(new BaseTracerRuleScreen(this.lastScreen, rule));
		}).width(FOOTER_BUTTON_WIDTH).build());
		footer.addChild(Button.builder(Component.translatable("options.basetracer.resetDefaults"), button -> this.confirmReset()).width(FOOTER_BUTTON_WIDTH).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(FOOTER_BUTTON_WIDTH).build());
	}

	private void confirmReset() {
		this.minecraft.gui.setScreen(new ConfirmScreen(
				confirmed -> {
					if (confirmed) {
						BaseTracerClient.CONFIG.resetRules();
					}

					this.minecraft.gui.setScreen(new BaseTracerRulesScreen(this.lastScreen));
				},
				Component.translatable("options.basetracer.rules.reset.title"),
				Component.translatable("options.basetracer.rules.reset.message")
		));
	}

	@Override
	public void removed() {
		super.removed();
		BaseTracerClient.applyRuleChanges();
	}
}
