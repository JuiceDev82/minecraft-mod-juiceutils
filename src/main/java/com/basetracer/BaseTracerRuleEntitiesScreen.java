package com.basetracer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

/**
 * Chooses a rule's entities from everything in the registry, including modded ones, plus any named mob and any
 * tamed animal whatever their type. Entries are ids, as the rule stores them.
 */
public class BaseTracerRuleEntitiesScreen extends SearchListScreen<String> {
	private static final Component IN_RULE = Component.translatable("options.basetracer.block.inRule").withStyle(ChatFormatting.GREEN);
	private static final Component NOT_IN_RULE = Component.translatable("options.basetracer.block.notInRule").withStyle(ChatFormatting.GRAY);

	private final Screen rulesParent;
	private final BaseRule rule;

	public BaseTracerRuleEntitiesScreen(Screen lastScreen, Screen rulesParent, BaseRule rule) {
		super(lastScreen, Component.translatable("options.basetracer.ruleEntities.title", rule.name), chosenFirst(rule));
		this.rulesParent = rulesParent;
		this.rule = rule;
	}

	// Sorted once on opening, so that rows don't jump around while they're being clicked.
	private static List<String> chosenFirst(BaseRule rule) {
		List<String> types = new ArrayList<>();

		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			// Players are highlighted on their own, and never count toward a base.
			if (type != EntityTypes.PLAYER) {
				types.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
			}
		}

		types.sort(Comparator.<String, Boolean>comparing(id -> !rule.entityIds().contains(id)).thenComparing(id -> displayName(id).getString()));
		List<String> entries = new ArrayList<>(List.of(BaseRule.NAMED_MOB, BaseRule.TAMED_ANIMAL));
		entries.addAll(types);
		return entries;
	}

	public static Component displayName(String id) {
		return switch (id) {
			case BaseRule.NAMED_MOB -> Component.translatable("options.basetracer.entity.namedMob.choice");
			case BaseRule.TAMED_ANIMAL -> Component.translatable("options.basetracer.entity.tamedAnimal.choice");
			default -> BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id)).getDescription();
		};
	}

	@Override
	protected Component name(String id) {
		return displayName(id);
	}

	@Override
	protected AbstractWidget createRow(Component name, String id) {
		return CycleButton.booleanBuilder(IN_RULE, NOT_IN_RULE, this.rule.entityIds().contains(id)).create(name, (button, value) -> {
			this.rule.setEntity(id, value);
			BaseTracerClient.CONFIG.markRulesChanged();
		});
	}

	@Override
	public void onClose() {
		// A new screen, so it shows the new entity count.
		this.minecraft.gui.setScreen(new BaseTracerRuleScreen(this.rulesParent, this.rule));
	}

	@Override
	public void removed() {
		super.removed();
		BaseTracerClient.applyRuleChanges();
	}
}
