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
     */
    static NameList parse(JsonElement json) {
        List<NameListEntry> entries = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray()) {
            JsonObject entry = element.getAsJsonObject();
            List<String> translationKeys = new ArrayList<>();
            JsonArray keys = entry.getAsJsonArray("translationKeys");
            keys.forEach(key -> translationKeys.add(key.getAsString()));
            entries.add(new NameListEntry(
                    new ItemId(entry.get("id").getAsString()),
                    translationKeys,
                    parseJapaneseName(entry.getAsJsonArray("japaneseName"))));
        }
        return new NameList(entries);
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
            return NamePart.kana(json.getAsString());
        }
        JsonArray textAndReading = json.getAsJsonArray();
        if (textAndReading.size() != 2) {
            throw new IllegalArgumentException("A part with a reading is an array of its text and reading: " + json);
        }
        return new NamePart(textAndReading.get(0).getAsString(), textAndReading.get(1).getAsString());
    }
}
