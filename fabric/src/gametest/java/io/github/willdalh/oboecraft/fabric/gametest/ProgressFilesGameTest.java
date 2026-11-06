package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.progress.ProgressFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Reading and writing progress files. These need no world, but run with the game tests like the rest of fabric. */
public class ProgressFilesGameTest {
    private static final ItemId OAK_LOG = new ItemId("block.minecraft.oak_log");
    private static final ItemId GONE = new ItemId("block.minecraft.not_in_the_name_list");

    private static Path temporaryFile() {
        try {
            return Files.createTempDirectory("oboecraft-progress").resolve("player.json");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ProgressFiles.ReadResult readText(String text) {
        try {
            Path file = temporaryFile();
            Files.writeString(file, text, StandardCharsets.UTF_8);
            return ProgressFiles.read(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @GameTest
    public void progressIsReadBackAsItWasWritten(GameTestHelper helper) throws IOException {
        Progress progress = new Progress(Map.of(
                OAK_LOG, new SrsState.Leveled(2, Optional.of(Instant.parse("2026-11-06T13:10:00.123Z"))),
                GONE, new SrsState.Leveled(9, Optional.empty()),
                new ItemId("block.minecraft.dirt"), new SrsState.Known(),
                new ItemId("block.minecraft.stone"), new SrsState.Ignored()));
        Path file = temporaryFile();

        ProgressFiles.write(file, progress);

        helper.assertValueEqual(ProgressFiles.read(file), new ProgressFiles.ReadResult.Loaded(progress), "read");
        helper.assertFalse(Files.exists(file.resolveSibling("player.json.tmp")), "the temporary file is gone");
        helper.succeed();
    }

    @GameTest
    public void theFileMatchesTheDocumentedFormat(GameTestHelper helper) throws IOException {
        Path file = temporaryFile();

        ProgressFiles.write(file, Progress.EMPTY.with(
                OAK_LOG, new SrsState.Leveled(2, Optional.of(Instant.parse("2026-11-06T13:10:00Z")))));

        String expected = """
                {
                  "version": 2,
                  "items": {
                    "block.minecraft.oak_log": {
                      "kind": "leveled",
                      "level": 2,
                      "locksAt": "2026-11-06T13:10:00.000Z"
                    }
                  }
                }""";
        helper.assertValueEqual(Files.readString(file, StandardCharsets.UTF_8), expected, "file");
        helper.succeed();
    }

    @GameTest
    public void aMissingFileIsNoProgress(GameTestHelper helper) throws IOException {
        helper.assertValueEqual(ProgressFiles.read(temporaryFile()), new ProgressFiles.ReadResult.Missing(), "read");
        helper.succeed();
    }

    @GameTest
    public void aFileFromANewerVersionIsTooNew(GameTestHelper helper) {
        helper.assertValueEqual(readText("{\"version\": 3, \"items\": {\"x\": \"a format we don't know\"}}"),
                new ProgressFiles.ReadResult.TooNew(3), "read");
        helper.succeed();
    }

    @GameTest
    public void invalidFilesAreBroken(GameTestHelper helper) {
        String[] invalid = {
            "not json",
            "[]",
            "{\"items\": {}}",
            "{\"version\": 1}",
            "{\"version\": 0, \"items\": {}}",
            "{\"version\": 1, \"items\": {\"block.minecraft.oak_log\": {\"kind\": \"mystery\"}}}",
            "{\"version\": 1, \"items\": {\"block.minecraft.oak_log\": {\"kind\": \"leveled\"}}}",
            "{\"version\": 1, \"items\": {\"block.minecraft.oak_log\": {\"kind\": \"leveled\", \"level\": -1}}}",
            "{\"version\": 1, \"items\": {\"block.minecraft.oak_log\": "
                    + "{\"kind\": \"leveled\", \"level\": 1, \"locksAt\": \"tomorrow\"}}}",
        };
        for (String text : invalid) {
            helper.assertTrue(readText(text) instanceof ProgressFiles.ReadResult.Broken, "broken: " + text);
        }
        helper.succeed();
    }

    @GameTest
    public void aFileThatIsntUtf8IsBroken(GameTestHelper helper) throws IOException {
        Path file = temporaryFile();
        Files.write(file, new byte[] {'{', (byte) 0xC3, (byte) 0x28, '}'});

        helper.assertTrue(ProgressFiles.read(file) instanceof ProgressFiles.ReadResult.Broken, "broken");
        helper.succeed();
    }

    @GameTest
    public void aBrokenFileIsKeptWithoutOverwritingAnEarlierOne(GameTestHelper helper) throws IOException {
        Path file = temporaryFile();
        Files.writeString(file, "first");
        Path first = ProgressFiles.keepBroken(file);
        Files.writeString(file, "second");
        Path second = ProgressFiles.keepBroken(file);

        helper.assertValueEqual(first.getFileName().toString(), "player.json.broken", "first broken file");
        helper.assertValueEqual(second.getFileName().toString(), "player.json.broken.1", "second broken file");
        helper.assertValueEqual(Files.readString(first), "first", "first broken file");
        helper.assertValueEqual(Files.readString(second), "second", "second broken file");
        helper.assertFalse(Files.exists(file), "the broken file was moved");
        helper.succeed();
    }
    @GameTest
    public void versionOneProgressSurvivesUpgrade(GameTestHelper helper) throws IOException {
        var old = readText("""
                {"version":1,"items":{"block.minecraft.oak_log":{"kind":"leveled","level":9}}}
                """);
        Progress expected = Progress.EMPTY.with(OAK_LOG, new SrsState.Leveled(9, Optional.empty()));
        helper.assertValueEqual(old, new ProgressFiles.ReadResult.Loaded(expected), "old progress");
        Path file = temporaryFile();
        ProgressFiles.write(file, ((ProgressFiles.ReadResult.Loaded) old).progress());
        helper.assertTrue(Files.readString(file).contains("\"version\": 2"), "rewritten as version 2");
        helper.assertValueEqual(ProgressFiles.read(file), old, "progress survives upgrade");
        helper.succeed();
    }

}
