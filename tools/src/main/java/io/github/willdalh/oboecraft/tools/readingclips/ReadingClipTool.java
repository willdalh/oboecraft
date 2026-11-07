package io.github.willdalh.oboecraft.tools.readingclips;

import com.google.gson.JsonParser;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.json.NameListJson;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Makes a reading clip for each entry of the name list with VOICEVOX: a sound file that says the entry's reading,
 * which the mod plays after a pass. Clips whose name and reading haven't changed are kept, so after a name list
 * update only new and changed entries are made. Run it with {@code ./gradlew :tools:generateReadingClips}, with a
 * VOICEVOX engine running and FFmpeg installed.
 *
 * <p>Arguments, all required:
 *
 * <ul>
 *   <li>{@code --name-list}: the name list file
 *   <li>{@code --assets}: the mod's client assets folder, {@code assets/oboecraft}, where the clips and
 *       {@code sounds.json} go
 *   <li>{@code --clip-list}: the clip list file to update
 *   <li>{@code --review-list}: where to write the review list
 *   <li>{@code --engine}: the address of the VOICEVOX engine, like {@code http://localhost:50021}
 * </ul>
 */
public final class ReadingClipTool {
    /**
     * The voice of every clip. Chosen in willdalh/oboecraft#25: a plain adult male voice whose terms allow commercial and
     * non-commercial use with only a credit, see {@code CREDITS.md}.
     */
    static final ReadingClipList.Voice VOICE =
            new ReadingClipList.Voice("玄野武宏", "c30dc15a-0992-4f8d-8bb8-ad3b314e6a6f", "ノーマル");

    private static final List<String> ARGUMENTS = List.of("name-list", "assets", "clip-list", "review-list", "engine");
    private static final String CLIP_EXTENSION = ".ogg";

    private ReadingClipTool() {
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> arguments = parseArguments(args);
        List<NameListEntry> entries = readNameList(Path.of(arguments.get("name-list")));
        Path assets = Path.of(arguments.get("assets"));
        Path clipFolder = assets.resolve("sounds").resolve(SoundsJson.FOLDER);
        Path clipListFile = Path.of(arguments.get("clip-list"));
        Path reviewListFile = Path.of(arguments.get("review-list"));
        // Every item id must be a file name Minecraft allows, before any clip is made.
        entries.forEach(entry -> SoundsJson.checkedFileName(entry.id()));

        OggEncoder.check();
        Voicevox voicevox = Voicevox.connect(URI.create(arguments.get("engine")), VOICE);
        String engineVersion = voicevox.version();

        Files.createDirectories(clipFolder);
        Optional<ReadingClipList> old = readClipList(clipListFile);
        ClipPlan plan = ClipPlan.of(entries, old, VOICE, OggEncoder.FORMAT, clipFiles(clipFolder));
        System.out.println(plan.toMake().size() + " clips to make with VOICEVOX " + engineVersion + ", "
                + plan.kept().size() + " kept, " + plan.toDelete().size() + " to delete");

        List<ReviewList.Made> made = new ArrayList<>();
        for (NameListEntry entry : plan.toMake()) {
            SpokenText spoken = SpokenText.of(entry.japaneseName(), voicevox);
            OggEncoder.encode(voicevox.speak(spoken.text()), clipFolder.resolve(entry.id().value() + CLIP_EXTENSION));
            made.add(new ReviewList.Made(entry, spoken));
            if (made.size() % 100 == 0) {
                System.out.println("Made " + made.size() + " of " + plan.toMake().size() + " clips");
            }
        }

        Map<ItemId, ReadingClipList.Clip> clips = Stream.concat(
                        plan.kept().stream(),
                        made.stream().map(clip -> new ReadingClipList.Clip(clip.entry().id(),
                                clip.entry().japaneseName().text(), clip.entry().japaneseName().reading(),
                                clip.spoken().text())))
                .collect(Collectors.toMap(ReadingClipList.Clip::item, Function.identity()));
        List<ReadingClipList.Clip> inOrder = entries.stream().map(entry -> clips.get(entry.id())).toList();
        String listedVersion = made.isEmpty() && old.isPresent() ? old.get().engineVersion() : engineVersion;

        writeReplacing(assets.resolve("sounds.json"), SoundsJson.format(entries.stream().map(NameListEntry::id).toList()));
        writeReplacing(clipListFile, new ReadingClipList(VOICE, OggEncoder.FORMAT, listedVersion, inOrder).fileText());
        for (ItemId item : plan.toDelete()) {
            Files.delete(clipFolder.resolve(item.value() + CLIP_EXTENSION));
        }
        Files.createDirectories(reviewListFile.toAbsolutePath().getParent());
        Files.writeString(reviewListFile, ReviewList.format(made, VOICE, engineVersion), StandardCharsets.UTF_8);
        System.out.println("Made " + made.size() + " clips, deleted " + plan.toDelete().size() + ". "
                + made.stream().filter(clip -> clip.spoken().fromReading()).count()
                + " were said from their reading and " + made.stream().filter(clip -> !clip.spoken().checked()).count()
                + " need a listen, see " + reviewListFile);
    }

    private static Map<String, String> parseArguments(String[] args) {
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
     * @throws IllegalArgumentException if the file isn't a valid name list, saying which entry is wrong
     */
    private static List<NameListEntry> readNameList(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return NameListJson.parse(JsonParser.parseReader(reader)).entries();
        }
    }

    /** The clip list from the last run, or none if there is no file yet. */
    private static Optional<ReadingClipList> readClipList(Path file) throws IOException {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return Optional.of(ReadingClipList.parse(JsonParser.parseReader(reader)));
        }
    }

    /** The items that have a clip file in the folder. */
    private static Set<ItemId> clipFiles(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(CLIP_EXTENSION))
                    .map(name -> new ItemId(name.substring(0, name.length() - CLIP_EXTENSION.length())))
                    .collect(Collectors.toSet());
        }
    }

    /** Writes a text file, replacing the old one only once the new one is written in full. */
    private static void writeReplacing(Path file, String text) throws IOException {
        Path partial = file.resolveSibling(file.getFileName() + ".part");
        Files.writeString(partial, text, StandardCharsets.UTF_8);
        Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
