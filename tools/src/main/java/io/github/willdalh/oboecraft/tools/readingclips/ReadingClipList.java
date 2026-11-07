package io.github.willdalh.oboecraft.tools.readingclips;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.willdalh.oboecraft.core.item.ItemId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What each reading clip was made from: the voice and format of every clip, and for each clip the Japanese name and
 * reading it says. The reading clip tool reads it to make only the clips whose name or reading changed. It is kept in
 * the repository next to the tool and isn't part of the mod.
 *
 * @param voice         the voice every clip is said in
 * @param format        how every clip is encoded
 * @param engineVersion the version of the VOICEVOX engine the clips were last made with
 * @param clips         one per clip, in the name list's order
 */
record ReadingClipList(Voice voice, String format, String engineVersion, List<Clip> clips) {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /**
     * A VOICEVOX voice.
     *
     * @param speaker     the voice's name, as in its credit
     * @param speakerUuid the id VOICEVOX knows the voice by
     * @param style       the name of the voice's style, like ノーマル
     */
    record Voice(String speaker, String speakerUuid, String style) {
        Voice {
            requireText(speaker, "speaker");
            requireText(speakerUuid, "speakerUuid");
            requireText(style, "style");
        }
    }

    /**
     * One clip.
     *
     * @param item    the item whose reading it says
     * @param name    the Japanese name, as it is written
     * @param reading the reading
     * @param spoken  the text VOICEVOX was given: the name, or one made from its reading
     */
    record Clip(ItemId item, String name, String reading, String spoken) {
        Clip {
            Objects.requireNonNull(item, "item");
            requireText(name, "name");
            requireText(reading, "reading");
            requireText(spoken, "spoken");
        }
    }

    /**
     * @throws IllegalArgumentException if two clips are for the same item
     */
    ReadingClipList {
        Objects.requireNonNull(voice, "voice");
        requireText(format, "format");
        requireText(engineVersion, "engineVersion");
        clips = List.copyOf(clips);
        Set<ItemId> items = new HashSet<>();
        for (Clip clip : clips) {
            if (!items.add(clip.item())) {
                throw new IllegalArgumentException("Two clips are for the item " + clip.item());
            }
        }
    }

    /**
     * @throws IllegalArgumentException if the file isn't a valid clip list
     */
    static ReadingClipList parse(JsonElement json) {
        JsonObject list = object(json, "The clip list");
        JsonObject voice = object(list.get("voice"), "voice");
        JsonElement clipArray = list.get("clips");
        if (clipArray == null || !clipArray.isJsonArray()) {
            throw new IllegalArgumentException("clips is missing or isn't an array");
        }
        List<Clip> clips = new ArrayList<>();
        for (JsonElement element : clipArray.getAsJsonArray()) {
            JsonObject clip = object(element, "A clip");
            clips.add(new Clip(new ItemId(string(clip, "id")), string(clip, "name"), string(clip, "reading"),
                    string(clip, "spoken")));
        }
        return new ReadingClipList(
                new Voice(string(voice, "speaker"), string(voice, "speakerUuid"), string(voice, "style")),
                string(list, "format"), string(list, "engineVersion"), clips);
    }

    /** The file's text, with one clip per line. */
    String fileText() {
        JsonObject voiceJson = new JsonObject();
        voiceJson.addProperty("speaker", voice.speaker());
        voiceJson.addProperty("speakerUuid", voice.speakerUuid());
        voiceJson.addProperty("style", voice.style());
        String clipLines = clips.stream()
                .map(clip -> {
                    JsonObject json = new JsonObject();
                    json.addProperty("id", clip.item().value());
                    json.addProperty("name", clip.name());
                    json.addProperty("reading", clip.reading());
                    json.addProperty("spoken", clip.spoken());
                    return "    " + GSON.toJson(json);
                })
                .collect(Collectors.joining(",\n"));
        return "{\n"
                + "  \"voice\": " + GSON.toJson(voiceJson) + ",\n"
                + "  \"format\": " + GSON.toJson(format) + ",\n"
                + "  \"engineVersion\": " + GSON.toJson(engineVersion) + ",\n"
                + "  \"clips\": [" + (clips.isEmpty() ? "" : "\n" + clipLines + "\n  ") + "]\n"
                + "}\n";
    }

    private static JsonObject object(JsonElement json, String name) {
        if (json == null || !json.isJsonObject()) {
            throw new IllegalArgumentException(name + " is missing or isn't an object");
        }
        return json.getAsJsonObject();
    }

    private static String string(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("The field " + field + " is missing or isn't a string");
        }
        return value.getAsString();
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " can't be blank");
        }
    }
}
