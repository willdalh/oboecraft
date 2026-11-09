package io.github.willdalh.oboecraft.tools.readingclips;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.willdalh.oboecraft.core.Oboecraft;
import io.github.willdalh.oboecraft.core.item.ItemId;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The mod's {@code sounds.json}, which tells Minecraft about the reading clips: one sound event per item,
 * {@code oboecraft:reading.<item id>}, playing the clip {@code sounds/reading/<item id>.ogg}. The mod plays an item's
 * clip by that name.
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
     * The file's text, with one sound event per line, in the order of {@code items}.
     *
     * @throws IllegalArgumentException if an item id can't be a file name in Minecraft's resources
     */
    static String format(List<ItemId> items) {
        return items.stream()
                .map(item -> "  " + GSON.toJson(FOLDER + "." + checkedFileName(item)) + ": " + GSON.toJson(event(item)))
                .collect(Collectors.joining(",\n", "{\n", "\n}\n"));
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
