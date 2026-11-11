package io.github.willdalh.oboecraft.fabric.settings;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.willdalh.oboecraft.core.Oboecraft;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.files.AtomicFiles;
import io.github.willdalh.oboecraft.json.SettingsJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The server settings file, {@code config/oboecraft/server.json}. On a dedicated server it is in the server's folder,
 * and in singleplayer in the game folder, so every singleplayer world uses the same settings. Its format is
 * {@link SettingsJson}.
 */
public final class ServerSettingsFile {
    private ServerSettingsFile() {
    }

    /** Where the server settings are saved. */
    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(Oboecraft.MOD_ID).resolve("server.json");
    }

    /**
     * Loads the server settings when the server starts. A missing file is written with the defaults, so the server
     * owner has a file to edit. Otherwise the file is read as by {@link #read}.
     */
    public static Settings load(Path file) {
        if (Files.notExists(file)) {
            writeDefaults(file);
            return Settings.DEFAULTS;
        }
        return read(file);
    }

    /**
     * Reads the server settings, or the defaults if there is no file. A file that can't be read or isn't valid
     * settings is logged and left untouched, and the defaults are used, so a typo never loses the owner's file.
     */
    public static Settings read(Path file) {
        return readIfValid(file).orElse(Settings.DEFAULTS);
    }

    /**
     * Reads the server settings, or the defaults if there is no file. A file that can't be read or isn't valid
     * settings is logged and gives nothing, so the settings screen knows not to save over it.
     */
    public static Optional<Settings> readIfValid(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return Optional.of(Settings.DEFAULTS);
        } catch (IOException e) {
            OboecraftMod.LOGGER.error("Couldn't read the settings file {}, so the default settings are used", file, e);
            return Optional.empty();
        }
        try {
            return Optional.of(SettingsJson.parse(JsonParser.parseString(text)));
        } catch (JsonParseException | IllegalArgumentException e) {
            OboecraftMod.LOGGER.error("The settings file {} isn't valid, so the default settings are used: {}",
                    file, e.getMessage());
            return Optional.empty();
        }
    }

    /** Saves the server settings, so that a crash while saving never leaves half a file. */
    public static void write(Path file, Settings settings) throws IOException {
        AtomicFiles.write(file, SettingsJson.format(settings));
    }

    private static void writeDefaults(Path file) {
        try {
            write(file, Settings.DEFAULTS);
            OboecraftMod.LOGGER.info("Wrote the default settings to {}", file);
        } catch (IOException e) {
            OboecraftMod.LOGGER.error("Couldn't write the default settings to {}", file, e);
        }
    }
}
