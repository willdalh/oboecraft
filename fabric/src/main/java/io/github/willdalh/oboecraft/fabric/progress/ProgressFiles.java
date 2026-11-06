package io.github.willdalh.oboecraft.fabric.progress;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Reads and writes progress files: JSON with a format version. See {@code docs/architecture/progress-files.md}. */
public final class ProgressFiles {
    /** The format version this version of the mod writes. */
    public static final int VERSION = 3;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** ISO 8601 in UTC, to the millisecond, so times can be read and fixed by hand. */
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private ProgressFiles() {
    }

    /** What reading a progress file found. */
    public sealed interface ReadResult {
        /** The file was read. */
        record Loaded(Progress progress) implements ReadResult {
        }

        /** There is no file, so the player has no progress yet. */
        record Missing() implements ReadResult {
        }

        /** The file was saved by a newer version of the mod, in a format this version doesn't know. */
        record TooNew(int version) implements ReadResult {
        }

        /** The file isn't valid progress. */
        record Broken(String reason) implements ReadResult {
        }
    }

    /**
     * Reads a progress file.
     *
     * @throws IOException if the file exists but can't be read. Its contents are unknown, so it must not be
     *                     overwritten.
     */
    public static ReadResult read(Path file) throws IOException {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return new ReadResult.Missing();
        } catch (CharacterCodingException e) {
            return new ReadResult.Broken("The file isn't UTF-8: " + e);
        }
        try {
            return parse(JsonParser.parseString(text));
        } catch (RuntimeException e) {
            // Gson and the core records report every kind of invalid content as an unchecked exception, including
            // missing fields as NullPointerException. The file is kept as broken, never lost.
            return new ReadResult.Broken(e.toString());
        }
    }

    /** Parses the JSON of a progress file. Throws for anything that isn't valid progress. */
    static ReadResult parse(JsonElement json) {
        JsonObject root = json.getAsJsonObject();
        int version = root.getAsJsonPrimitive("version").getAsInt();
        if (version > VERSION) {
            return new ReadResult.TooNew(version);
        }
        if (version < 1) {
            throw new IllegalArgumentException("Unknown format version " + version);
        }
        Map<ItemId, SrsState> states = new HashMap<>();
        for (Map.Entry<String, JsonElement> item : root.getAsJsonObject("items").entrySet()) {
            states.put(new ItemId(item.getKey()), parseState(item.getValue().getAsJsonObject()));
        }
        return new ReadResult.Loaded(new Progress(states));
    }

    private static SrsState parseState(JsonObject state) {
        String kind = state.getAsJsonPrimitive("kind").getAsString();
        if (kind.equals("known")) return new SrsState.Known();
        if (kind.equals("ignored")) return new SrsState.Ignored();
        if (!kind.equals("leveled")) {
            throw new IllegalArgumentException("Unknown kind of state " + kind);
        }
        int level = state.getAsJsonPrimitive("level").getAsInt();
        boolean demoted = state.has("demoted") && state.getAsJsonPrimitive("demoted").getAsBoolean();
        int wrongAttempts = state.has("wrongAttempts") ? state.getAsJsonPrimitive("wrongAttempts").getAsInt() : 0;
        return new SrsState.Leveled(
                level, parseTime(state, "locksAt"), demoted, wrongAttempts, parseTime(state, "bannedUntil"));
    }

    private static Optional<Instant> parseTime(JsonObject state, String field) {
        return state.has(field)
                ? Optional.of(Instant.parse(state.getAsJsonPrimitive(field).getAsString()))
                : Optional.empty();
    }

    /** The JSON of a progress file. States that are new are left out, and so are unset fields. */
    static JsonObject toJson(Progress progress) {
        JsonObject items = new JsonObject();
        progress.states().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ItemId::value)))
                .forEach(entry -> items.add(entry.getKey().value(), stateToJson(entry.getValue())));
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.add("items", items);
        return root;
    }

    private static JsonObject stateToJson(SrsState state) {
        JsonObject json = new JsonObject();
        switch (state) {
            case SrsState.Known known -> json.addProperty("kind", "known");
            case SrsState.Ignored ignored -> json.addProperty("kind", "ignored");
            case SrsState.Leveled leveled -> {
                json.addProperty("kind", "leveled");
                json.addProperty("level", leveled.level());
                leveled.locksAt().ifPresent(locksAt -> json.add("locksAt", time(locksAt)));
                if (leveled.demoted()) {
                    json.addProperty("demoted", true);
                }
                if (leveled.wrongAttempts() > 0) {
                    json.addProperty("wrongAttempts", leveled.wrongAttempts());
                }
                leveled.bannedUntil().ifPresent(bannedUntil -> json.add("bannedUntil", time(bannedUntil)));
            }
        }
        return json;
    }

    private static JsonPrimitive time(Instant instant) {
        return new JsonPrimitive(TIME_FORMAT.format(instant));
    }

    /**
     * Writes a progress file. It is written to a temporary file next to it, flushed to disk and then moved into
     * place, so a crash while saving never leaves half a file.
     */
    public static void write(Path file, Progress progress) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        byte[] bytes = GSON.toJson(toJson(progress)).getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(temporary,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Moves a file that can't be read out of the way, to {@code <name>.broken}, or {@code <name>.broken.1} and so on if
     * that is taken, so no earlier broken file is overwritten.
     *
     * @return where the file was moved
     */
    public static Path keepBroken(Path file) throws IOException {
        Path target = file.resolveSibling(file.getFileName() + ".broken");
        for (int n = 1; Files.exists(target); n++) {
            target = file.resolveSibling(file.getFileName() + ".broken." + n);
        }
        return Files.move(file, target);
    }
}
