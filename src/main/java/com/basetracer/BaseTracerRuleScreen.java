package com.basetracer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/**
 * Edits one rule. Loaded chunks are rescanned once it's left.
 */
public class BaseTracerRuleScreen extends OptionsSubScreen {
	private static final String DEFAULT_NAME = "Unnamed Rule";

	private final Screen rulesParent;
	private final BaseRule rule;

	/** @param rulesParent where the rule list goes back to, since closing this opens a fresh rule list. */
	public BaseTracerRuleScreen(Screen rulesParent, BaseRule rule) {
		super(rulesParent, Minecraft.getInstance().options, Component.literal(rule.name));
		this.rulesParent = rulesParent;
		this.rule = rule;
	}

	@Override
	protected void addOptions() {
		BaseTracerConfig config = BaseTracerClient.CONFIG;

		if (this.rule.lightSources) {
			this.list.addBig(Button.builder(
					Component.translatable("options.basetracer.excludedBlocks"),
					button -> this.minecraft.gui.setScreen(new BaseTracerExcludedBlocksScreen(this))
			).build());
		} else {
			Component nameLabel = Component.translatable("options.basetracer.rule.name");
			EditBox name = new EditBox(this.font, 310, 20, nameLabel);
			name.setMaxLength(40);
			name.setValue(this.rule.name);
			name.setResponder(value -> {
				this.rule.name = value;
				config.markRulesChanged();
			});
			this.list.addHeader(nameLabel);
			this.list.addBig(name);
			this.list.addBig(Button.builder(
					Component.translatable("options.basetracer.rule.chooseBlocks", this.rule.blockTypeCount()),
					button -> this.minecraft.gui.setScreen(new BaseTracerRuleBlocksScreen(this, this.rulesParent, this.rule))
			).build());
			this.list.addBig(Button.builder(
					Component.translatable("options.basetracer.rule.chooseEntities", this.rule.entityChoiceCount()),
					button -> this.minecraft.gui.setScreen(new BaseTracerRuleEntitiesScreen(this, this.rulesParent, this.rule))
			).build());
		}

		this.addThresholdSlider("options.basetracer.rule.priority", BaseRule.MAX_PRIORITY, this.rule.priority, value -> this.rule.priority = value);
		this.addThresholdSlider("options.basetracer.rule.minBlocks", BaseRule.MAX_MIN_BLOCKS, this.rule.minBlocks, value -> this.rule.minBlocks = value);
		this.addThresholdSlider("options.basetracer.rule.minTypes", BaseRule.MAX_MIN_TYPES, this.rule.minTypes, value -> this.rule.minTypes = value);
		this.list.addSmall(
				OptionInstance.createBoolean("options.basetracer.rule.showLabel", this.rule.showLabel, value -> {
					this.rule.showLabel = value;
					config.markRulesChanged();
				}),
				OptionInstance.createBoolean("options.basetracer.rule.showTracer", this.rule.showTracer, value -> {
					this.rule.showTracer = value;
					config.markRulesChanged();
				})
		);
		BaseTracerConfigScreen.addColorField(this.list, this.font, "options.basetracer.rule.color", this.rule.color(), value -> {
			this.rule.setColor(value);
			config.markRulesChanged();
		});
	}

	private void addThresholdSlider(String key, int max, int initialValue, IntConsumer setter) {
		this.list.addBig(new OptionInstance<>(
				key,
				OptionInstance.noTooltip(),
				(caption, value) -> Options.genericValueLabel(caption, Component.literal(value.toString())),
				new OptionInstance.IntRange(1, max, true),
				initialValue,
				value -> {
					setter.accept(value);
					BaseTracerClient.CONFIG.markRulesChanged();
				}
		));
	}

	@Override
	protected void addFooter() {
		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));

		if (!this.rule.lightSources) {
			footer.addChild(Button.builder(Component.translatable("options.basetracer.rule.delete"), button -> this.confirmDelete()).build());
		}

		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).build());
	}

	private void confirmDelete() {
		this.minecraft.gui.setScreen(new ConfirmScreen(
				confirmed -> {
					if (confirmed) {
						BaseTracerClient.CONFIG.rules.remove(this.rule);
						BaseTracerClient.CONFIG.markRulesChanged();
						this.minecraft.gui.setScreen(new BaseTracerRulesScreen(this.rulesParent));
					} else {
						this.minecraft.gui.setScreen(new BaseTracerRuleScreen(this.rulesParent, this.rule));
					}
				},
				Component.translatable("options.basetracer.rule.delete.title", this.rule.name),
				Component.translatable("options.basetracer.rule.delete.message")
		));
	}

	@Override
	public void onClose() {
		if (!this.rule.lightSources) {
			if (this.rule.name.isBlank()) {
				this.rule.name = DEFAULT_NAME;
			}

			// More types than the rule has blocks for could never match.
			int types = this.rule.typeCount();

			if (types > 0 && this.rule.minTypes > types) {
				this.rule.minTypes = types;
				BaseTracerClient.CONFIG.markRulesChanged();
			}
		}

		this.minecraft.gui.setScreen(new BaseTracerRulesScreen(this.rulesParent));
	}

	@Override
	public void removed() {
		super.removed();
		BaseTracerClient.applyRuleChanges();
	}
}
