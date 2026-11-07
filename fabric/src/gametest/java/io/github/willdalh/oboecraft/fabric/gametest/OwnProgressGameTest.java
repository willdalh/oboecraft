package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.network.ProgressUnavailable;
import io.github.willdalh.oboecraft.fabric.network.UploadProgress;
import io.github.willdalh.oboecraft.fabric.progress.OwnProgress;
import io.github.willdalh.oboecraft.fabric.progress.ProgressFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The client's own progress file: what it sends the server, the lock that keeps two games from using it at once, and
 * saving the server's changes. Each test uses a folder of its own.
 */
public class OwnProgressGameTest {
    private static final ItemId OAK_LOG = new ItemId("block.minecraft.oak_log");
    private static final Progress LEVEL_THREE = Progress.EMPTY.with(OAK_LOG,
            new SrsState.Leveled(3, Optional.of(Instant.parse("2030-01-01T00:00:00Z"))));
    /** Long enough that only the end of a session saves, unless a test waits for the delay. */
    private static final Duration NO_SAVE_BEFORE_THE_END = Duration.ofHours(1);

    private static Path folder() throws IOException {
        return Files.createTempDirectory("oboecraft-own-progress");
    }

    /** Starts a session and returns what the client sends the server. */
    private static CustomPacketPayload start(OwnProgress ownProgress, UUID player) {
        List<CustomPacketPayload> sent = new ArrayList<>();
        ownProgress.start(player, sent::add).join();
        return sent.getFirst();
    }

    @GameTest
    public void aPlayerWithoutAFileSendsEmptyProgressAndTheChangesAreSavedWhenTheSessionEnds(GameTestHelper helper)
            throws IOException {
        Path folder = folder();
        UUID player = UUID.randomUUID();
        OwnProgress ownProgress = new OwnProgress(folder, NO_SAVE_BEFORE_THE_END);
        try {
            helper.assertValueEqual(start(ownProgress, player), new UploadProgress(Progress.EMPTY), "sent");
            ownProgress.changed(LEVEL_THREE);
            ownProgress.end().join();

            helper.assertValueEqual(ProgressFiles.read(folder.resolve(player + ".json")),
                    new ProgressFiles.ReadResult.Loaded(LEVEL_THREE), "saved progress");
            helper.assertValueEqual(start(ownProgress, player), new UploadProgress(LEVEL_THREE), "sent next time");
        } finally {
            ownProgress.close();
        }
        helper.succeed();
    }

    @GameTest
    public void changesAreSavedSoonAfterTheyArrive(GameTestHelper helper) throws IOException {
        Path folder = folder();
        UUID player = UUID.randomUUID();
        Path file = folder.resolve(player + ".json");
        OwnProgress ownProgress = new OwnProgress(folder, Duration.ofMillis(10));
        start(ownProgress, player);
        ownProgress.changed(LEVEL_THREE);

        helper.succeedWhen(() -> {
            try {
                helper.assertValueEqual(ProgressFiles.read(file), new ProgressFiles.ReadResult.Loaded(LEVEL_THREE),
                        "saved progress");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            ownProgress.close();
        });
    }

    @GameTest
    public void changesOutsideASessionAreNotSaved(GameTestHelper helper) throws IOException {
        Path folder = folder();
        UUID player = UUID.randomUUID();
        OwnProgress ownProgress = new OwnProgress(folder, NO_SAVE_BEFORE_THE_END);
        try {
            ownProgress.changed(LEVEL_THREE);
            ownProgress.end().join();
            start(ownProgress, player);
            ownProgress.end().join();
            ownProgress.changed(LEVEL_THREE);
            ownProgress.end().join();
        } finally {
            ownProgress.close();
        }
        helper.assertFalse(Files.exists(folder.resolve(player + ".json")), "no progress file");
        helper.succeed();
    }

    @GameTest
    public void aFileInUseByAnotherGameIsntSent(GameTestHelper helper) throws IOException {
        Path folder = folder();
        UUID player = UUID.randomUUID();
        OwnProgress firstGame = new OwnProgress(folder, NO_SAVE_BEFORE_THE_END);
        OwnProgress secondGame = new OwnProgress(folder, NO_SAVE_BEFORE_THE_END);
        try {
            start(firstGame, player);
            helper.assertValueEqual(start(secondGame, player),
                    new ProgressUnavailable(ProgressUnavailable.Reason.IN_USE), "sent by the second game");
            helper.assertValueEqual(start(secondGame, UUID.randomUUID()), new UploadProgress(Progress.EMPTY),
                    "another player's progress");

            firstGame.end().join();
            helper.assertValueEqual(start(secondGame, player), new UploadProgress(Progress.EMPTY),
                    "sent once the first game has left");
        } finally {
            firstGame.close();
            secondGame.close();
        }
        helper.succeed();
    }

    @GameTest
    public void aFileFromANewerVersionIsntSentAndIsLeftAlone(GameTestHelper helper) throws IOException {
        Path folder = folder();
        UUID player = UUID.randomUUID();
        Path file = folder.resolve(player + ".json");
        String newer = "{\"version\": 99, \"items\": {}}";
        Files.writeString(file, newer);
        OwnProgress ownProgress = new OwnProgress(folder, NO_SAVE_BEFORE_THE_END);
        try {
            helper.assertValueEqual(start(ownProgress, player),
                    new ProgressUnavailable(ProgressUnavailable.Reason.TOO_NEW), "sent");
            ownProgress.changed(LEVEL_THREE);
            ownProgress.end().join();
        } finally {
            ownProgress.close();
        }
        helper.assertValueEqual(Files.readString(file), newer, "the newer file");
        helper.succeed();
    }

    @GameTest
    public void aBrokenFileIsKeptAndThePlayerStartsEmpty(GameTestHelper helper) throws IOException {
        Path folder = folder();
        UUID player = UUID.randomUUID();
        Path file = folder.resolve(player + ".json");
        Files.writeString(file, "{ broken");
        OwnProgress ownProgress = new OwnProgress(folder, NO_SAVE_BEFORE_THE_END);
        try {
            helper.assertValueEqual(start(ownProgress, player), new UploadProgress(Progress.EMPTY), "sent");
            helper.assertValueEqual(ownProgress.takeBrokenFileKeptAs(), Optional.of(player + ".json.broken"),
                    "the name the player is told");
            helper.assertValueEqual(ownProgress.takeBrokenFileKeptAs(), Optional.empty(), "told once");
            helper.assertValueEqual(Files.readString(file.resolveSibling(player + ".json.broken")), "{ broken",
                    "kept broken file");
        } finally {
            ownProgress.close();
        }
        helper.succeed();
    }
}
