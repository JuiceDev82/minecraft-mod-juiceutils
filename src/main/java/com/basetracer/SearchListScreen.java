package com.basetracer;

import java.util.Collection;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/**
 * A list with one row per named entry, and a box to search those names.
 */
abstract class SearchListScreen<T> extends OptionsSubScreen {
	private static final Component SEARCH_HINT = Component.translatable("options.basetracer.search").withStyle(EditBox.SEARCH_HINT_STYLE);
	private static final int SEARCH_BOX_HEIGHT = 15;

	private final Collection<T> entries;
	private EntryList entryList;
	protected EditBox search;

	SearchListScreen(Screen lastScreen, Component title, Collection<T> entries) {
		super(lastScreen, Minecraft.getInstance().options, title);
		this.entries = entries;
	}

	protected abstract Component name(T entry);

	protected abstract AbstractWidget createRow(Component name, T entry);

	@Override
	protected void addTitle() {
		LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(4));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new StringWidget(this.title, this.font));
		this.search = header.addChild(new EditBox(this.font, 0, 0, 200, SEARCH_BOX_HEIGHT, Component.empty()));
		this.search.setHint(SEARCH_HINT);
		this.layout.setHeaderHeight(12 + 9 + SEARCH_BOX_HEIGHT);
	}

	@Override
	protected void setInitialFocus() {
		this.setInitialFocus(this.search);
	}

	@Override
	protected void addContents() {
		this.entryList = this.layout.addToContents(new EntryList(this.minecraft, this.width, this));
		this.list = this.entryList;
		this.addOptions();
		this.search.setResponder(this::showRows);
	}

	@Override
	protected void addOptions() {
		this.showRows("");
	}

	protected void showRows(String filter) {
		String lowerCaseFilter = filter.toLowerCase(Locale.ROOT);
		this.entryList.clear();

		for (T entry : this.entries) {
			Component name = this.name(entry);

			if (name.getString().toLowerCase(Locale.ROOT).contains(lowerCaseFilter)) {
				this.entryList.addBig(this.createRow(name, entry));
			}
		}
	}

	private static class EntryList extends OptionsList {
		EntryList(Minecraft minecraft, int width, OptionsSubScreen screen) {
			super(minecraft, width, screen);
		}

		void clear() {
			this.clearEntries();
			this.setScrollAmount(0.0);
		}
	}
}
