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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

/**
 * Chooses which kinds of entity get a name tag, from everything in the registry: mobs, but also boats, minecarts,
 * item frames and the like, including modded ones.
 */
public class NameTagEntitiesScreen extends SearchListScreen<EntityType<?>> {
	private static final Component TAGGED = Component.translatable("options.juiceclient.nameTag.tagged").withStyle(ChatFormatting.GREEN);
	private static final Component NOT_TAGGED = Component.translatable("options.juiceclient.nameTag.notTagged").withStyle(ChatFormatting.GRAY);

	public NameTagEntitiesScreen(Screen lastScreen) {
		super(lastScreen, Component.translatable("options.juiceclient.nameTagEntities.title"), taggedFirst());
	}

	// Sorted once on opening, so that rows don't jump around while they're being clicked.
	private static List<EntityType<?>> taggedFirst() {
		JuiceClientConfig config = JuiceClient.CONFIG;
		List<EntityType<?>> types = new ArrayList<>();

		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			if (type != EntityTypes.PLAYER) {
				types.add(type);
			}
		}

		types.sort(Comparator.<EntityType<?>, Boolean>comparing(type -> !config.isNameTagged(type))
				.thenComparing(type -> type.getDescription().getString()));
		return types;
	}

	@Override
	protected Component name(EntityType<?> type) {
		return type.getDescription();
	}

	@Override
	protected AbstractWidget createRow(Component name, EntityType<?> type) {
		JuiceClientConfig config = JuiceClient.CONFIG;
		return CycleButton.booleanBuilder(TAGGED, NOT_TAGGED, config.isNameTagged(type))
				.create(name, (button, value) -> config.setNameTagged(type, value));
	}

	@Override
	protected void addFooter() {
		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(Component.translatable("options.juiceclient.putAllItems.clear"), button -> {
			JuiceClient.CONFIG.clearNameTagEntities();
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
