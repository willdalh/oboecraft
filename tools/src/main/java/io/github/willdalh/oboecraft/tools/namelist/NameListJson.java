package io.github.willdalh.oboecraft.tools.namelist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.JapaneseName;
import io.github.willdalh.oboecraft.core.item.NameList;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.item.NamePart;
import io.github.willdalh.oboecraft.core.text.JapaneseText;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reads and writes the name list file that ships with the mod, in the format of
 * {@code docs/architecture/name-list-file.md}.
 * The mod reads the file with its own {@code NameListFile}, which needs Fabric, so the tool can't share it.
 */
final class NameListJson {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private NameListJson() {
    }

    /**
     * Reads a name list file, or gives an empty list if there is no file yet.
     *
     * @throws IOException              if the file can't be read
     * @throws IllegalArgumentException if the file isn't a valid name list
     */
    static List<NameListEntry> read(Path file) throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return parse(JsonParser.parseReader(reader));
        }
    }

    static List<NameListEntry> parse(JsonElement json) {
        List<NameListEntry> entries = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray()) {
            JsonObject entry = element.getAsJsonObject();
            List<String> keys = new ArrayList<>();
            entry.getAsJsonArray("translationKeys").forEach(key -> keys.add(key.getAsString()));
            List<NamePart> parts = new ArrayList<>();
            for (JsonElement part : entry.getAsJsonArray("japaneseName")) {
                if (part.isJsonArray()) {
                    JsonArray textAndReading = part.getAsJsonArray();
                    if (textAndReading.size() != 2) {
                        throw new IllegalArgumentException("A part with a reading is its text and reading: " + part);
                    }
                    parts.add(new NamePart(textAndReading.get(0).getAsString(), textAndReading.get(1).getAsString()));
                } else {
                    parts.add(NamePart.kana(part.getAsString()));
                }
            }
            entries.add(new NameListEntry(new ItemId(entry.get("id").getAsString()), keys, new JapaneseName(parts)));
        }
        // Checks that no two entries share an item id or translation key.
        new NameList(entries);
        return List.copyOf(entries);
    }

    /**
     * Writes a name list file, replacing the old one only once the new one is written in full.
     *
     * @throws IllegalArgumentException if two entries share an item id or translation key
     */
    static void write(Path file, List<NameListEntry> entries) throws IOException {
        String json = format(entries);
        Path partial = file.resolveSibling(file.getFileName() + ".part");
        Files.writeString(partial, json, StandardCharsets.UTF_8);
        Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** The file's text: a JSON array with one entry per line. */
    static String format(List<NameListEntry> entries) {
        // Checks that no two entries share an item id or translation key.
        new NameList(entries);
        return entries.stream()
                .map(entry -> "  {\"id\": " + GSON.toJson(entry.id().value())
                        + ", \"translationKeys\": " + entry.translationKeys().stream().map(GSON::toJson)
                                .collect(Collectors.joining(", ", "[", "]"))
                        + ", \"japaneseName\": " + formatName(entry.japaneseName()) + "}")
                .collect(Collectors.joining(",\n", "[\n", "\n]\n"));
    }

    /** A part in kana is a string, and any other part an array of its text and reading. */
    private static String formatName(JapaneseName name) {
        return name.parts().stream()
                .map(part -> isReadAsWritten(part)
                        ? GSON.toJson(part.text())
                        : "[" + GSON.toJson(part.text()) + ", " + GSON.toJson(part.reading()) + "]")
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static boolean isReadAsWritten(NamePart part) {
        String normalized = JapaneseText.normalize(part.text());
        return JapaneseText.isKana(normalized) && normalized.equals(part.reading());
    }
}
