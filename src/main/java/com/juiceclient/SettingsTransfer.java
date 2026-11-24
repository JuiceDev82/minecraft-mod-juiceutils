package com.juiceclient;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Saves all of the mod's settings to a file, and loads them back: every option, the base rules and the lists chosen
 * in them, and the mod's keys. Files go in an exports folder next to the settings file, so they can also be copied to
 * another computer or shared.
 */
public final class SettingsTransfer {
	public static final String FOLDER_NAME = "juiceclient-exports";
	private static final String EXTENSION = ".properties";
	/** Keys are saved with this in front of their name, apart from the settings themselves. */
	private static final String KEY_PREFIX = "keybind.";
	private static final String MOD_KEY_PREFIX = "key.juiceclient.";
	/** The keys' names from when this mod was called Base Tracer, which older exports have. */
	private static final String OLD_MOD_KEY_PREFIX = "key.basetracer.";
	/** Where exports went when this mod was called Base Tracer. */
	private static final String OLD_FOLDER_NAME = "basetracer-exports";
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

	private SettingsTransfer() {
	}

	/**
	 * Carries everything over from when this mod was called Base Tracer: the exports folder, and the keys in the
	 * game's options file. Called as the mod starts, before the game reads its options, so the keys load as they were.
	 */
	public static void migrateOldName() {
		Path oldFolder = FabricLoader.getInstance().getConfigDir().resolve(OLD_FOLDER_NAME);

		try {
			if (Files.isDirectory(oldFolder) && !Files.exists(folder())) {
				Files.move(oldFolder, folder());
				JuiceClient.LOGGER.info("Moved {} to {}", oldFolder, folder());
			}
		} catch (IOException e) {
			JuiceClient.LOGGER.warn("Failed to move {}", oldFolder, e);
		}

		Path options = FabricLoader.getInstance().getGameDir().resolve("options.txt");

		try {
			if (!Files.isRegularFile(options)) {
				return;
			}

			List<String> lines = Files.readAllLines(options);
			String oldKey = "key_" + OLD_MOD_KEY_PREFIX;
			String newKey = "key_" + MOD_KEY_PREFIX;

			if (lines.stream().noneMatch(line -> line.startsWith(oldKey)) || lines.stream().anyMatch(line -> line.startsWith(newKey))) {
				return;
			}

			Files.write(options, lines.stream().map(line -> line.startsWith(oldKey) ? newKey + line.substring(oldKey.length()) : line).toList());
			JuiceClient.LOGGER.info("Carried the mod's keys over from its old name in {}", options);
		} catch (IOException e) {
			JuiceClient.LOGGER.warn("Failed to carry the mod's keys over in {}", options, e);
		}
	}

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve(FOLDER_NAME);
	}

	/** Writes the current settings and keys to a new file, named by the time, and returns it. */
	public static Path export(String prefix) throws IOException {
		Path folder = folder();
		Files.createDirectories(folder);
		String base = prefix + LocalDateTime.now().format(TIME);
		Path file = folder.resolve(base + EXTENSION);

		// Two in the same second get a number, rather than one replacing the other.
		for (int i = 2; Files.exists(file); i++) {
			file = folder.resolve(base + "_" + i + EXTENSION);
		}

		Properties properties = JuiceClient.CONFIG.toProperties();

		for (KeyMapping mapping : modKeys()) {
			properties.setProperty(KEY_PREFIX + mapping.getName(), mapping.saveString());
		}

		try (Writer writer = Files.newBufferedWriter(file)) {
			properties.store(writer, "Juice Client settings export. Load it through Import Settings in the mod's settings.");
		}

		return file;
	}

	public static Path export() throws IOException {
		return export("juiceclient-settings_");
	}

	/** The saved files, newest first. */
	public static List<Path> exports() {
		Path folder = folder();

		if (!Files.isDirectory(folder)) {
			return List.of();
		}

		try (Stream<Path> files = Files.list(folder)) {
			return files.filter(file -> file.getFileName().toString().endsWith(EXTENSION) && Files.isRegularFile(file))
					.sorted(Comparator.comparing(SettingsTransfer::modified).reversed())
					.toList();
		} catch (IOException e) {
			JuiceClient.LOGGER.warn("Failed to list {}", folder, e);
			return List.of();
		}
	}

	private static long modified(Path file) {
		try {
			return Files.getLastModifiedTime(file).toMillis();
		} catch (IOException e) {
			return 0;
		}
	}

	/**
	 * Replaces the current settings and keys with a file's. What's there now is exported first, so an import can be
	 * undone by importing that. Settings the file doesn't have, such as ones added in a later version, go back to
	 * their defaults.
	 */
	public static void importFrom(Path file) throws IOException {
		Properties properties = new Properties();

		try (Reader reader = Files.newBufferedReader(file)) {
			properties.load(reader);
		} catch (IllegalArgumentException e) {
			throw new IOException("Not a settings file: " + file.getFileName(), e);
		}

		if (!properties.containsKey("enabled") || !properties.containsKey("ruleCount")) {
			throw new IOException("Not a Juice Client settings file: " + file.getFileName());
		}

		export("before-import_");
		JuiceClientConfig config = JuiceClient.CONFIG;
		config.copyFrom(JuiceClientConfig.fromProperties(properties));
		config.save();

		boolean keysChanged = false;

		for (KeyMapping mapping : modKeys()) {
			String key = properties.getProperty(KEY_PREFIX + mapping.getName(),
					properties.getProperty(KEY_PREFIX + OLD_MOD_KEY_PREFIX + mapping.getName().substring(MOD_KEY_PREFIX.length())));

			if (key != null) {
				mapping.setKey(InputConstants.getKey(key));
				keysChanged = true;
			}
		}

		if (keysChanged) {
			KeyMapping.resetMapping();
			Minecraft.getInstance().options.save();
		}

		SectionToggle.forgetAll();
		JuiceClient.NAME_TAGS.rescanSoon();
		JuiceClient.applyRuleChanges();
	}

	private static List<KeyMapping> modKeys() {
		List<KeyMapping> keys = new ArrayList<>();

		for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
			if (mapping.getName().startsWith(MOD_KEY_PREFIX)) {
				keys.add(mapping);
			}
		}

		return keys;
	}
}
