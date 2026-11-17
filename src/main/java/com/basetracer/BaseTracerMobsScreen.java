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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Chooses which mobs get highlighted from everything in the registry, including modded mobs.
 */
public class BaseTracerMobsScreen extends SearchListScreen<EntityType<?>> {
	private static final Component HIGHLIGHTED = Component.translatable("options.basetracer.mob.highlighted").withStyle(ChatFormatting.GREEN);
	private static final Component NOT_HIGHLIGHTED = Component.translatable("options.basetracer.mob.notHighlighted").withStyle(ChatFormatting.GRAY);

	public BaseTracerMobsScreen(Screen lastScreen) {
		super(lastScreen, Component.translatable("options.basetracer.mobs.title"), highlightedFirst());
	}

	// Sorted once on opening, so that rows don't jump around while they're being clicked.
	private static List<EntityType<?>> highlightedFirst() {
		BaseTracerConfig config = BaseTracerClient.CONFIG;
		List<EntityType<?>> mobs = new ArrayList<>(mobTypes());
		mobs.sort(Comparator.<EntityType<?>, Boolean>comparing(type -> !config.isMobHighlighted(type))
				.thenComparing(type -> type.getDescription().getString()));
		return mobs;
	}

	/** Most of the misc category isn't alive, such as items and arrows. The mobs in it are the ones with spawn eggs. */
	public static List<EntityType<?>> mobTypes() {
		List<EntityType<?>> mobs = new ArrayList<>();

		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			if (type.getCategory() != MobCategory.MISC || SpawnEggItem.byId(type).isPresent()) {
				mobs.add(type);
			}
		}

		return mobs;
	}

	@Override
	protected Component name(EntityType<?> type) {
		return type.getDescription();
	}

	@Override
	protected AbstractWidget createRow(Component name, EntityType<?> type) {
		BaseTracerConfig config = BaseTracerClient.CONFIG;
		return CycleButton.booleanBuilder(HIGHLIGHTED, NOT_HIGHLIGHTED, config.isMobHighlighted(type))
				.create(name, (button, value) -> config.setMobHighlighted(type, value));
	}

	@Override
	protected void addFooter() {
		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(Component.translatable("options.basetracer.resetDefaults"), button -> {
			BaseTracerClient.CONFIG.resetMobs();
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
