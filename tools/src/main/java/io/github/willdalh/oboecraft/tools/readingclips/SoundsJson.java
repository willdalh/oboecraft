package io.github.willdalh.oboecraft.tools.readingclips;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.willdalh.oboecraft.core.Oboecraft;
import io.github.willdalh.oboecraft.core.item.ItemId;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The mod's {@code sounds.json}, which tells Minecraft about the reading clips: one sound event per item,
 * {@code oboecraft:reading.<item id>}, playing the clip {@code sounds/reading/<item id>.ogg}. The mod plays an item's
 * clip by that name. The file's other sound events, like the tsukumogami's, are written by hand and kept as they are.
 */
final class SoundsJson {
    /** The folder of the clips, under {@code assets/oboecraft/sounds/}, and the start of each sound event's name. */
    static final String FOLDER = "reading";
    static final String SUBTITLE = "subtitles.oboecraft.reading";

    /** The characters Minecraft allows in a resource path, except {@code /}, which an item id never has. */
    private static final Pattern RESOURCE_PATH = Pattern.compile("[a-z0-9_.-]+");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private SoundsJson() {
    }

    /**
     * The file's text, with one sound event per line: the sound events of {@code existing} that aren't reading clips,
     * in their order, then a reading clip for each of {@code items}, in their order.
     *
     * @param existing the file as it is, or an empty object if there is none
     * @throws IllegalArgumentException if an item id can't be a file name in Minecraft's resources
     */
    static String format(List<ItemId> items, JsonObject existing) {
        Stream<String> kept = existing.entrySet().stream()
                .filter(event -> !isReadingClip(event.getKey()))
                .map(event -> line(event.getKey(), event.getValue()));
        Stream<String> clips = items.stream().map(item -> line(FOLDER + "." + checkedFileName(item), event(item)));
        return Stream.concat(kept, clips).collect(Collectors.joining(",\n", "{\n", "\n}\n"));
    }

    /** Whether a sound event of the file is a reading clip, which this tool writes. */
    private static boolean isReadingClip(String eventName) {
        return eventName.startsWith(FOLDER + ".");
    }

    private static String line(String eventName, JsonElement event) {
        return "  " + GSON.toJson(eventName) + ": " + GSON.toJson(event);
    }

    /**
     * The item's id, which is the file name of its clip, without {@code .ogg}.
     *
     * @throws IllegalArgumentException if it can't be a file name in Minecraft's resources
     */
    static String checkedFileName(ItemId item) {
        if (!RESOURCE_PATH.matcher(item.value()).matches()) {
            throw new IllegalArgumentException("The item id " + item + " has characters that Minecraft doesn't allow"
                    + " in a resource path, so it can't have a reading clip");
        }
        return item.value();
    }

    private static JsonObject event(ItemId item) {
        JsonObject sound = new JsonObject();
        sound.addProperty("name", Oboecraft.MOD_ID + ":" + FOLDER + "/" + item.value());
        JsonArray sounds = new JsonArray();
        sounds.add(sound);
        JsonObject event = new JsonObject();
        event.add("sounds", sounds);
        event.addProperty("subtitle", SUBTITLE);
        return event;
    }
}
