package io.github.willdalh.oboecraft.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.willdalh.oboecraft.core.settings.DurationText;
import io.github.willdalh.oboecraft.core.settings.LevelSettings;
import io.github.willdalh.oboecraft.core.settings.Settings;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The server settings file format, see {@code docs/architecture/settings-files.md}. Durations are written as
 * {@link DurationText}, like {@code "10m"} or {@code "1d 12h"}. Version 1, from before the tsukumogami, is still read,
 * with the default tsukumogami settings, and so is version 2, from before the fade threshold.
 */
public final class SettingsJson {
    /** The format version this code reads and writes. */
    public static final int VERSION = 3;
    /** The version with one tsukumogami threshold, before the fade threshold, which is still read. */
    private static final int VERSION_WITH_ONE_THRESHOLD = 2;
    /** The version before the tsukumogami settings, which is still read. */
    private static final int VERSION_WITHOUT_TSUKUMOGAMI = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Set<String> FIELDS_WITHOUT_TSUKUMOGAMI =
            Set.of("version", "levels", "banLength", "percentageFactor", "dropCountsAsInteraction");
    private static final Set<String> FIELDS_WITH_ONE_THRESHOLD = Stream.concat(FIELDS_WITHOUT_TSUKUMOGAMI.stream(),
            Stream.of("tsukumogamiThreshold", "tsukumogamiReviewFreeze")).collect(Collectors.toUnmodifiableSet());
    private static final Set<String> FIELDS = Stream.concat(FIELDS_WITHOUT_TSUKUMOGAMI.stream(),
            Stream.of("tsukumogamiAppearThreshold", "tsukumogamiFadeThreshold", "tsukumogamiReviewFreeze"))
            .collect(Collectors.toUnmodifiableSet());
    private static final Set<String> LEVEL_FIELDS = Set.of("interval", "demotion");

    private SettingsJson() {
    }

    /**
     * Parses a settings file. Every field of its version must be there, with no others, and the values must pass the
     * checks of {@link Settings}. A version 1 file has no tsukumogami settings, so it gets their defaults. A version 2
     * file's threshold is the appear threshold, and its fade threshold is the default, or one below the appear
     * threshold when the default isn't below it, as it faded before.
     *
     * @throws IllegalArgumentException if the file isn't valid settings of a version this code reads, saying what is
     *                                  wrong
     */
    public static Settings parse(JsonElement json) {
        JsonObject object = requireObject(json, "The settings");
        if (!object.has("version")) {
            throw new IllegalArgumentException("The settings have no version");
        }
        int version = requireInt(object.get("version"), "version");
        if (version < VERSION_WITHOUT_TSUKUMOGAMI || version > VERSION) {
            throw new IllegalArgumentException("The settings are version " + version + ", but this version of "
                    + "Oboecraft reads versions " + VERSION_WITHOUT_TSUKUMOGAMI + " to " + VERSION);
        }
        requireFields(object, switch (version) {
            case VERSION_WITHOUT_TSUKUMOGAMI -> FIELDS_WITHOUT_TSUKUMOGAMI;
            case VERSION_WITH_ONE_THRESHOLD -> FIELDS_WITH_ONE_THRESHOLD;
            default -> FIELDS;
        }, "The settings");
        JsonElement levelsJson = object.get("levels");
        if (!levelsJson.isJsonArray()) {
            throw new IllegalArgumentException("levels must be an array");
        }
        JsonArray levelsArray = levelsJson.getAsJsonArray();
        if (levelsArray.size() > Settings.MAX_LEVELS) {
            throw new IllegalArgumentException("There can be at most " + Settings.MAX_LEVELS + " levels, not "
                    + levelsArray.size());
        }
        List<LevelSettings> levels = new ArrayList<>();
        for (int index = 0; index < levelsArray.size(); index++) {
            String name = "Level " + (index + 1);
            try {
                JsonObject level = requireObject(levelsArray.get(index), name);
                requireFields(level, LEVEL_FIELDS, name);
                levels.add(new LevelSettings(requireDuration(level.get("interval"), "interval"),
                        requireInt(level.get("demotion"), "demotion")));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(name + " is invalid: " + e.getMessage(), e);
            }
        }
        int appearThreshold = switch (version) {
            case VERSION_WITHOUT_TSUKUMOGAMI -> Settings.DEFAULTS.tsukumogamiAppearThreshold();
            case VERSION_WITH_ONE_THRESHOLD -> requireInt(object.get("tsukumogamiThreshold"), "tsukumogamiThreshold");
            default -> requireInt(object.get("tsukumogamiAppearThreshold"), "tsukumogamiAppearThreshold");
        };
        int fadeThreshold = switch (version) {
            case VERSION_WITHOUT_TSUKUMOGAMI -> Settings.DEFAULTS.tsukumogamiFadeThreshold();
            case VERSION_WITH_ONE_THRESHOLD -> fadeThresholdFor(appearThreshold);
            default -> requireInt(object.get("tsukumogamiFadeThreshold"), "tsukumogamiFadeThreshold");
        };
        return new Settings(levels, requireDuration(object.get("banLength"), "banLength"),
                requireInt(object.get("percentageFactor"), "percentageFactor"),
                requireBoolean(object.get("dropCountsAsInteraction"), "dropCountsAsInteraction"),
                appearThreshold, fadeThreshold,
                version == VERSION_WITHOUT_TSUKUMOGAMI
                        ? Settings.DEFAULTS.tsukumogamiReviewFreeze()
                        : requireDuration(object.get("tsukumogamiReviewFreeze"), "tsukumogamiReviewFreeze"));
    }

    /**
     * The fade threshold for a version 2 file's single threshold: the default, unless it isn't below the threshold.
     * Then it is one below, where it used to fade, or 0 for a threshold of 0, which turns the tsukumogami off.
     */
    private static int fadeThresholdFor(int appearThreshold) {
        int fadeThreshold = Settings.DEFAULTS.tsukumogamiFadeThreshold();
        return fadeThreshold < appearThreshold ? fadeThreshold : Math.max(0, appearThreshold - 1);
    }

    /** The file's text, with every field and the current version. */
    public static String format(Settings settings) {
        JsonObject json = new JsonObject();
        json.addProperty("version", VERSION);
        JsonArray levels = new JsonArray();
        for (LevelSettings level : settings.levels()) {
            JsonObject levelJson = new JsonObject();
            levelJson.addProperty("interval", DurationText.format(level.interval()));
            levelJson.addProperty("demotion", level.demotion());
            levels.add(levelJson);
        }
        json.add("levels", levels);
        json.addProperty("banLength", DurationText.format(settings.banLength()));
        json.addProperty("percentageFactor", settings.percentageFactor());
        json.addProperty("dropCountsAsInteraction", settings.dropCountsAsInteraction());
        json.addProperty("tsukumogamiAppearThreshold", settings.tsukumogamiAppearThreshold());
        json.addProperty("tsukumogamiFadeThreshold", settings.tsukumogamiFadeThreshold());
        json.addProperty("tsukumogamiReviewFreeze", DurationText.format(settings.tsukumogamiReviewFreeze()));
        return GSON.toJson(json) + "\n";
    }

    private static JsonObject requireObject(JsonElement json, String what) {
        if (json == null || !json.isJsonObject()) {
            throw new IllegalArgumentException(what + " must be an object");
        }
        return json.getAsJsonObject();
    }

    /** Every field must be there, and no others, so a misspelled field is never ignored without a word. */
    private static void requireFields(JsonObject object, Set<String> fields, String what) {
        for (String field : fields) {
            if (!object.has(field)) {
                throw new IllegalArgumentException(what + " has no " + field);
            }
        }
        for (String field : object.keySet()) {
            if (!fields.contains(field)) {
                throw new IllegalArgumentException(what + " has an unknown field " + field);
            }
        }
    }

    private static int requireInt(JsonElement json, String field) {
        if (json instanceof JsonPrimitive primitive && primitive.isNumber()) {
            double value = primitive.getAsDouble();
            if (value == Math.rint(value) && Math.abs(value) <= Integer.MAX_VALUE) {
                return (int) value;
            }
        }
        throw new IllegalArgumentException(field + " must be a whole number");
    }

    private static boolean requireBoolean(JsonElement json, String field) {
        if (json instanceof JsonPrimitive primitive && primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        throw new IllegalArgumentException(field + " must be true or false");
    }

    private static java.time.Duration requireDuration(JsonElement json, String field) {
        if (json instanceof JsonPrimitive primitive && primitive.isString()) {
            try {
                return DurationText.parse(primitive.getAsString());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(field + ": " + e.getMessage(), e);
            }
        }
        throw new IllegalArgumentException(field + " must be a duration like \"10m\" or \"1d 12h\"");
    }
}
