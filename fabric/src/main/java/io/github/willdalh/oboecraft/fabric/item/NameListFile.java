package io.github.willdalh.oboecraft.fabric.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.willdalh.oboecraft.core.Oboecraft;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.JapaneseName;
import io.github.willdalh.oboecraft.core.item.NameList;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.item.NamePart;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Loads the name list that ships in the mod's resources. It is read through Fabric's mod container, so it works the
 * same on a dedicated server, which has no client assets.
 */
public final class NameListFile {
    /** Where the name list is, relative to the root of the mod jar. */
    public static final String PATH = "oboecraft/name_list.json";

    private NameListFile() {
    }

    /**
     * Loads the name list from the mod's resources.
     *
     * @throws IllegalStateException if the file is missing or invalid. It ships with the mod, so this is a bug.
     */
    public static NameList load() {
        Path path = FabricLoader.getInstance()
                .getModContainer(Oboecraft.MOD_ID)
                .flatMap(mod -> mod.findPath(PATH))
                .orElseThrow(() -> new IllegalStateException("The mod has no " + PATH));
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(JsonParser.parseReader(reader));
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Couldn't load the name list from " + PATH, e);
        }
    }

    /**
     * Parses a name list: an array of entries, each with an {@code id}, its {@code translationKeys} and the
     * {@code japaneseName} as an array of parts. A part in kana is a string, like {@code "オーク"}, and any other part is
     * an array of its text and its reading, like {@code ["原", "げん"]}. See the name list file doc.
     *
     * @throws IllegalArgumentException if the name list is invalid, saying which entry is wrong: its id, or its place
     *     in the list if it has no id
     */
    public static NameList parse(JsonElement json) {
        JsonArray array = requireArray(json, "The name list");
        List<NameListEntry> entries = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            JsonElement entry = array.get(index);
            try {
                entries.add(parseEntry(entry));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(describe(entry, index) + " is invalid: " + e.getMessage(), e);
            }
        }
        return new NameList(entries);
    }

    private static NameListEntry parseEntry(JsonElement json) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("it isn't a JSON object");
        }
        JsonObject entry = json.getAsJsonObject();
        List<String> translationKeys = new ArrayList<>();
        for (JsonElement key : requireArray(entry.get("translationKeys"), "translationKeys")) {
            translationKeys.add(requireString(key, "A translation key"));
        }
        return new NameListEntry(
                new ItemId(requireString(entry.get("id"), "id")),
                translationKeys,
                parseJapaneseName(requireArray(entry.get("japaneseName"), "japaneseName")));
    }

    private static JapaneseName parseJapaneseName(JsonArray json) {
        List<NamePart> parts = new ArrayList<>();
        for (JsonElement part : json) {
            parts.add(parsePart(part));
        }
        return new JapaneseName(parts);
    }

    private static NamePart parsePart(JsonElement json) {
        if (json.isJsonPrimitive()) {
            return NamePart.kana(requireString(json, "A part"));
        }
        JsonArray textAndReading = requireArray(json, "A part");
        if (textAndReading.size() != 2) {
            throw new IllegalArgumentException("A part with a reading is an array of its text and reading: " + json);
        }
        return new NamePart(
                requireString(textAndReading.get(0), "A part's text"),
                requireString(textAndReading.get(1), "A part's reading"));
    }

    /** The entry's id if it has one, so a mistake can be found in the file, or else its place in the list. */
    private static String describe(JsonElement entry, int index) {
        if (entry.isJsonObject()) {
            JsonElement id = entry.getAsJsonObject().get("id");
            if (id != null && id.isJsonPrimitive() && id.getAsJsonPrimitive().isString()) {
                return "The entry " + id.getAsString();
            }
        }
        return "The entry " + index + " without an id";
    }

    private static String requireString(JsonElement json, String what) {
        if (json == null || !json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(what + " is missing or isn't a string");
        }
        return json.getAsString();
    }

    private static JsonArray requireArray(JsonElement json, String what) {
        if (json == null || !json.isJsonArray()) {
            throw new IllegalArgumentException(what + " is missing or isn't an array");
        }
        return json.getAsJsonArray();
    }
}
