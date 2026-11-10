package io.github.willdalh.oboecraft.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.JapaneseName;
import io.github.willdalh.oboecraft.core.item.Name;
import io.github.willdalh.oboecraft.core.item.PlainName;
import io.github.willdalh.oboecraft.core.item.NameList;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.item.NamePart;
import io.github.willdalh.oboecraft.core.text.JapaneseText;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The name list file format, see {@code docs/architecture/name-list-file.md}. The mod reads the file with it, and the
 * name list tool reads and writes it.
 */
public final class NameListJson {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private NameListJson() {
    }

    /**
     * Parses a name list: an array of entries, each with an {@code id}, its {@code translationKeys} and the
     * {@code name} as plain text or, for Japanese, an array of parts. A part in kana is a string, like {@code "オーク"}, and any other part is
     * an array of its text and its reading, like {@code ["原", "げん"]}.
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

    /**
     * The file's text: a JSON array with one entry per line, in order. A part in kana is a string, and any other part
     * an array of its text and reading.
     *
     * @throws IllegalArgumentException if two entries share an item id or translation key
     */
    public static String format(List<NameListEntry> entries) {
        // Checks that no two entries share an item id or translation key.
        new NameList(entries);
        return entries.stream()
                .map(entry -> "  {\"id\": " + GSON.toJson(entry.id().value())
                        + ", \"translationKeys\": " + entry.translationKeys().stream().map(GSON::toJson)
                                .collect(Collectors.joining(", ", "[", "]"))
                        + ", \"name\": " + formatName(entry.name())
                        + (entry.name() instanceof PlainName plain ? ", \"sourceName\": " + GSON.toJson(plain.sourceText()) : "") + "}")
                .collect(Collectors.joining(",\n", "[\n", "\n]\n"));
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
                parseName(entry));
    }

    private static Name parseName(JsonObject entry) {
        JsonElement json = entry.get("name");
        if (json != null && json.isJsonPrimitive()) {
            String text = requireString(json, "name");
            return new PlainName(text, entry.has("sourceName") ? requireString(entry.get("sourceName"), "sourceName") : text);
        }
        return parseJapaneseName(requireArray(json, "name"));
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

    private static String formatName(Name name) {
        if (name instanceof PlainName plain) return GSON.toJson(plain.text());
        return ((JapaneseName) name).parts().stream()
                .map(part -> isReadAsWritten(part)
                        ? GSON.toJson(part.text())
                        : "[" + GSON.toJson(part.text()) + ", " + GSON.toJson(part.reading()) + "]")
                .collect(Collectors.joining(", ", "[", "]"));
    }

    /** Whether a part is only kana, read as written, so the file writes it as a string. */
    private static boolean isReadAsWritten(NamePart part) {
        String normalized = JapaneseText.normalize(part.text());
        return JapaneseText.isKana(normalized) && normalized.equals(part.reading());
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
