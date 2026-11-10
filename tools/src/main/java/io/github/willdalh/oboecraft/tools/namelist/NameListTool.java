package io.github.willdalh.oboecraft.tools.namelist;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.item.LearningLanguage;
import io.github.willdalh.oboecraft.json.NameListJson;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes each name list from Minecraft's supported language files, and a list of the generated entries for an agent to review.
 * Entries already in the name list keep their reviewed parts, so after a new Minecraft version only new and changed
 * names need review. Run it with {@code ./gradlew :tools:generateNameList}.
 *
 * <p>Arguments, all required:
 *
 * <ul>
 *   <li>{@code --minecraft-version}: the Minecraft version whose names to read, like {@code 26.3}
 *   <li>{@code --name-lists}: the folder of per-language name lists to update
 *   <li>{@code --review-list}: where to write the review list
 *   <li>{@code --cache}: the folder for downloads and the data generator's output
 * </ul>
 */
public final class NameListTool {
    private static final List<String> ARGUMENTS = List.of("minecraft-version", "name-lists", "review-list", "cache");

    private NameListTool() {
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> arguments = parseArguments(args);
        String version = arguments.get("minecraft-version");
        Path nameLists = Path.of(arguments.get("name-lists"));
        Path reviewListFile = Path.of(arguments.get("review-list"));
        Downloads downloads = new Downloads(Path.of(arguments.get("cache")));

        MinecraftFiles minecraft = MinecraftFiles.download(version, downloads);
        GameData gameData = GameData.read(
                minecraft.serverJar(), downloads.cacheFolder().resolve("minecraft/" + version + "/data-generator"));
        Map<String, String> japanese = readLanguageFile(minecraft.languageFiles().get(LearningLanguage.JAPANESE));
        ItemNameKeys.Collected keys = ItemNameKeys.collect(gameData, japanese.keySet(), ItemNameKeys.CODE_NAMES);
        Dictionaries dictionaries = Dictionaries.download(downloads);
        Path nameListFile = nameLists.resolve("ja_jp.json");
        List<NameListEntry> existing = readNameList(nameListFile);

        NameListGenerator.Result result;
        try (SudachiWordSplitter words = new SudachiWordSplitter(dictionaries.sudachiDictionary())) {
            NameGuesser guesser = new DictionaryNameGuesser(
                    words, dictionaries.jmdict(), new KanjiSplitter(dictionaries.kanjidic()));
            result = NameListGenerator.generate(keys.keys(), japanese, existing, guesser);
        }

        writeNameList(nameListFile, result.entries());
        ReviewList.write(reviewListFile, result, keys.notes(), "Minecraft " + version + ", " + dictionaries.describe());
        System.out.println("Wrote " + result.entries().size() + " entries to " + nameListFile + ". "
                + result.generated().size() + " were generated and need review, see " + reviewListFile);
        Map<String, String> english = readLanguageFile(minecraft.englishLanguageFile());
        for (LearningLanguage language : LearningLanguage.values()) {
            if (language == LearningLanguage.JAPANESE) continue;
            Path file = nameLists.resolve(language.code() + ".json");
            Map<String, String> translated = readLanguageFile(minecraft.languageFiles().get(language));
            NameListGenerator.Result plain = NameListGenerator.generatePlain(keys.keys(), translated, english, readNameList(file));
            writeNameList(file, plain.entries());
            Path review = reviewListFile.resolveSibling(language.code() + "-review.md");
            ReviewList.write(review, plain, keys.notes(), "Minecraft " + version + " " + language.code());
            System.out.println("Wrote " + plain.entries().size() + " entries to " + file + ", see " + review);
        }

    }

    private static Map<String, String> parseArguments(String[] args) {
        if (args.length % 2 != 0) throw new IllegalArgumentException("Each argument needs a value");
        Map<String, String> arguments = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            if (!args[i].startsWith("--") || !ARGUMENTS.contains(args[i].substring(2))) {
                throw new IllegalArgumentException("Unknown argument " + args[i] + ", expected one of " + ARGUMENTS);
            }
            arguments.put(args[i].substring(2), args[i + 1]);
        }
        for (String name : ARGUMENTS) {
            if (!arguments.containsKey(name)) {
                throw new IllegalArgumentException("The argument --" + name + " is missing");
            }
        }
        return arguments;
    }

    /**
     * The name list as it is now, or none if there is no file yet.
     *
     * @throws IllegalArgumentException if the file isn't a valid name list, saying which entry is wrong
     */
    private static List<NameListEntry> readNameList(Path file) throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return NameListJson.parse(JsonParser.parseReader(reader)).entries();
        }
    }

    /** Writes the name list, replacing the old file only once the new one is written in full. */
    private static void writeNameList(Path file, List<NameListEntry> entries) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path partial = file.resolveSibling(file.getFileName() + ".part");
        Files.writeString(partial, NameListJson.format(entries), StandardCharsets.UTF_8);
        Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Minecraft's language file: one JSON object from each translation key to its text, in the file's order. */
    private static Map<String, String> readLanguageFile(Path file) throws IOException {
        JsonObject json = MinecraftFiles.readJson(file);
        Map<String, String> texts = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            texts.put(entry.getKey(), entry.getValue().getAsString());
        }
        return texts;
    }
}
