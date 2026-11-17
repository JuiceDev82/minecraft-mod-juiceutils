package com.basetracer;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Only loaded by Mod Menu, so the mod still runs when Mod Menu isn't installed.
 */
public class BaseTracerModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return BaseTracerConfigScreen::new;
	}
}
