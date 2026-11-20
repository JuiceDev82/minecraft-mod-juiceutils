package com.basetracer;

import java.io.IOException;
import java.nio.file.Path;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Lists the saved settings files, newest first. Clicking one loads it and goes back to the settings, which then show
 * what was loaded.
 */
public class SettingsImportScreen extends SearchListScreen<Path> {
	/** The screen the settings were opened from, since the settings screen itself is rebuilt after an import. */
	private final Screen settingsParent;

	public SettingsImportScreen(Screen settingsScreen, Screen settingsParent) {
		super(settingsScreen, Component.translatable("options.basetracer.import.title"), SettingsTransfer.exports());
		this.settingsParent = settingsParent;
	}

	@Override
	protected Component name(Path file) {
		String name = file.getFileName().toString();
		return Component.literal(name.substring(0, name.length() - ".properties".length()));
	}

	@Override
	protected AbstractWidget createRow(Component name, Path file) {
		return Button.builder(name, button -> this.load(file)).build();
	}

	private void load(Path file) {
		try {
			SettingsTransfer.importFrom(file);
			SystemToast.addOrUpdate(this.minecraft.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
					Component.translatable("options.basetracer.import.done"), this.name(file));
			this.minecraft.gui.setScreen(new BaseTracerConfigScreen(this.settingsParent));
		} catch (IOException e) {
			BaseTracerClient.LOGGER.error("Failed to import {}", file, e);
			SystemToast.addOrUpdate(this.minecraft.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
					Component.translatable("options.basetracer.import.failed").withStyle(ChatFormatting.RED), Component.literal(String.valueOf(e.getMessage())));
		}
	}

	@Override
	protected void addFooter() {
		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(Component.translatable("options.basetracer.import.openFolder"), button -> {
			try {
				java.nio.file.Files.createDirectories(SettingsTransfer.folder());
			} catch (IOException e) {
				BaseTracerClient.LOGGER.warn("Failed to create {}", SettingsTransfer.folder(), e);
			}

			Util.getPlatform().openPath(SettingsTransfer.folder());
		}).build());
		footer.addChild(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose()).build());
	}
}
