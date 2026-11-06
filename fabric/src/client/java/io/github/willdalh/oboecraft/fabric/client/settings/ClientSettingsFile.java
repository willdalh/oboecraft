package io.github.willdalh.oboecraft.fabric.client.settings;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.willdalh.oboecraft.core.Oboecraft;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.files.AtomicFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The client settings file, {@code config/oboecraft/client.json} in the game folder: {@code {"version": 1,
 * "clickableButtons": false}}.
 */
public final class ClientSettingsFile {
    /** The format version this code reads and writes. */
    static final int VERSION = 1;
    private static final Set<String> FIELDS = Set.of("version", "clickableButtons");

    private ClientSettingsFile() {
    }

    /** Where the client settings are saved. */
    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(Oboecraft.MOD_ID).resolve("client.json");
    }

    /**
     * Reads the client settings. A missing file means the defaults. A file that can't be read or isn't valid client
     * settings is logged and left untouched, and the defaults are used.
     */
    public static ClientSettings read(Path file) {
        try {
            return parse(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)));
        } catch (NoSuchFileException e) {
            return ClientSettings.DEFAULTS;
        } catch (IOException e) {
            OboecraftMod.LOGGER.error("Couldn't read the client settings file {}, so the defaults are used", file, e);
        } catch (JsonParseException | IllegalArgumentException e) {
            OboecraftMod.LOGGER.error("The client settings file {} isn't valid, so the defaults are used: {}",
                    file, e.getMessage());
        }
        return ClientSettings.DEFAULTS;
    }

    /** Saves the client settings, so that a crash while saving never leaves half a file. */
    public static void write(Path file, ClientSettings settings) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("version", VERSION);
        json.addProperty("clickableButtons", settings.clickableButtons());
        AtomicFiles.write(file, new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n");
    }

    /**
     * Parses client settings. Every field must be there, with no others.
     *
     * @throws IllegalArgumentException if the file isn't valid client settings of this version
     */
    static ClientSettings parse(JsonElement json) {
        if (!(json instanceof JsonObject object) || !object.keySet().equals(FIELDS)) {
            throw new IllegalArgumentException("The client settings must have exactly the fields " + FIELDS);
        }
        if (!(object.get("version") instanceof JsonPrimitive version) || !version.isNumber()
                || version.getAsDouble() != VERSION) {
            throw new IllegalArgumentException("This version of Oboecraft reads client settings version " + VERSION);
        }
        if (!(object.get("clickableButtons") instanceof JsonPrimitive buttons) || !buttons.isBoolean()) {
            throw new IllegalArgumentException("clickableButtons must be true or false");
        }
        return new ClientSettings(buttons.getAsBoolean());
    }
}
