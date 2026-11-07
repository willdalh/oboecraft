package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.network.ProgressUnavailable;
import io.github.willdalh.oboecraft.fabric.network.UploadProgress;
import io.github.willdalh.oboecraft.fabric.progress.ProgressFiles;
import io.github.willdalh.oboecraft.fabric.progress.ProgressStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The client's progress file: what it sends the server, the lock that keeps two games from using it at once, saving
 * the server's changes, and sessions that belong to one connection. Each test uses a folder of its own.
 */
public class ProgressStoreGameTest {
    private static final ItemId OAK_LOG = new ItemId("block.minecraft.oak_log");
    private static final Progress LEVEL_THREE = Progress.EMPTY.with(OAK_LOG,
            new SrsState.Leveled(3, Optional.of(Instant.parse("2030-01-01T00:00:00Z"))));
    /** Long enough that only the end of a session saves, unless a test waits for the delay. */
    private static final Duration NO_SAVE_BEFORE_THE_END = Duration.ofHours(1);

    private static Path folder() throws IOException {
        return Files.createTempDirectory("oboecraft-progress-store");
    }

    private static ProgressStore store(Path folder, Duration saveDelay) {
        return new ProgressStore(folder, saveDelay, () -> {
        });
    }

    private static Connection connection() {
        return new Connection(PacketFlow.CLIENTBOUND);
    }

    /** Starts a session and returns what the client sends the server. */
    private static CustomPacketPayload start(ProgressStore store, Connection connection, UUID player) {
        List<CustomPacketPayload> sent = new ArrayList<>();
        store.start(connection, player, sent::add).join();
        return sent.getFirst();
    }

    private static ProgressFiles.ReadResult read(Path file) {
        try {
            return ProgressFiles.read(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @GameTest
    public void aPlayerWithoutAFileSendsEmptyProgressAndTheChangesAreSavedWhenTheSessionEnds(GameTestHelper helper)
            throws IOException {
        Path folder = folder();
        UUID player = UUID.randomUUID();
        Connection connection = connection();
        ProgressStore store = store(folder, NO_SAVE_BEFORE_THE_END);
        try {
            helper.assertValueEqual(start(store, connection, player), new UploadProgress(Progress.EMPTY), "sent");
            store.changed(connection, LEVEL_THREE);
            store.end(connection).join();

            helper.assertValueEqual(read(folder.resolve(player + ".json")),
                    new ProgressFiles.ReadResult.Loaded(LEVEL_THREE), "saved progress");
            helper.assertValueEqual(start(store, connection(), player), new UploadProgress(LEVEL_THREE),
                    "sent next time");
        } finally {
            store.close();
        }
        helper.succeed();
    }

    @GameTest
    public void changesAreSavedSoonAfterTheyArrive(GameTestHelper helper) throws IOException {
        Path folder = folder();
        UUID player = UUID.randomUUID();
        Connection connection = connection();
        ProgressStore store = store(folder, Duration.ofMillis(10));
        start(store, connection, player);
        store.changed(connection, LEVEL_THREE);

        helper.succeedWhen(() -> {
            helper.assertValueEqual(read(folder.resolve(player + ".json")),
                    new ProgressFiles.ReadResult.Loaded(LEVEL_THREE), "saved progress");
            store.close();
        });
    }

    @GameTest
    public void anotherConnectionCantChangeOrEndTheSession(GameTestHelper helper) throws IOException {
        Path folder = folder();
        UUID player = UUID.randomUUID();
        Path file = folder.resolve(player + ".json");
        Connection earlier = connection();
        Connection current = connection();
        ProgressStore store = store(folder, NO_SAVE_BEFORE_THE_END);
        try {
            start(store, current, player);
            store.changed(earlier, Progress.EMPTY);
            store.end(earlier).join();
            helper.assertFalse(Files.exists(file), "a late change of an earlier connection isn't saved");

            store.changed(current, LEVEL_THREE);
            store.end(current).join();
            helper.assertValueEqual(read(file), new ProgressFiles.ReadResult.Loaded(LEVEL_THREE),
                    "the session's change is saved when it ends");
        } finally {
            store.close();
        }
        helper.succeed();
    }

    @GameTest
    public void changesOutsideASessionAreNotSaved(GameTestHelper helper) throws IOException {
        Path folder = folder();
        UUID player = UUID.randomUUID();
        Connection connection = connection();
        ProgressStore store = store(folder, NO_SAVE_BEFORE_THE_END);
        try {
            store.changed(connection, LEVEL_THREE);
            store.end(connection).join();
            start(store, connection, player);
            store.end(connection).join();
            store.changed(connection, LEVEL_THREE);
            store.end(connection).join();
        } finally {
            store.close();
        }
        helper.assertFalse(Files.exists(folder.resolve(player + ".json")), "no progress file");
        helper.succeed();
    }

    @GameTest
    public void stoppingTheGameSavesTheSessionAndLaterCallsAreIgnored(GameTestHelper helper) throws IOException {
        Path folder = folder();
        UUID player = UUID.randomUUID();
        Connection connection = connection();
        ProgressStore store = store(folder, NO_SAVE_BEFORE_THE_END);
        start(store, connection, player);
        store.changed(connection, LEVEL_THREE);

        store.close();
        // The game stops before it leaves the world, so the connection's end and late changes come afterwards.
        store.changed(connection, Progress.EMPTY);
        store.end(connection).join();

        helper.assertValueEqual(read(folder.resolve(player + ".json")),
                new ProgressFiles.ReadResult.Loaded(LEVEL_THREE), "saved progress");
        helper.succeed();
    }

    @GameTest
    public void thePlayerIsToldOnceWhenSavingFails(GameTestHelper helper) throws IOException {
        Path folder = folder();
        UUID player = UUID.randomUUID();
        Connection connection = connection();
        AtomicInteger told = new AtomicInteger();
        ProgressStore store = new ProgressStore(folder, NO_SAVE_BEFORE_THE_END, told::incrementAndGet);
        try {
            start(store, connection, player);
            // A folder where the file should be makes every save fail.
            Files.createDirectory(folder.resolve(player + ".json"));
            store.changed(connection, LEVEL_THREE);
            store.end(connection).join();
        } finally {
            store.close();
        }
        helper.assertValueEqual(told.get(), 1, "times told");
        helper.succeed();
    }

    @GameTest
    public void aFileInUseByAnotherGameIsntSent(GameTestHelper helper) throws IOException {
        Path folder = folder();
        UUID player = UUID.randomUUID();
        Connection firstConnection = connection();
        ProgressStore firstGame = store(folder, NO_SAVE_BEFORE_THE_END);
        ProgressStore secondGame = store(folder, NO_SAVE_BEFORE_THE_END);
        try {
            start(firstGame, firstConnection, player);
            helper.assertValueEqual(start(secondGame, connection(), player),
                    new ProgressUnavailable(ProgressUnavailable.Reason.IN_USE), "sent by the second game");
            helper.assertValueEqual(start(secondGame, connection(), UUID.randomUUID()),
                    new UploadProgress(Progress.EMPTY), "another player's progress");

            firstGame.end(firstConnection).join();
            helper.assertValueEqual(start(secondGame, connection(), player), new UploadProgress(Progress.EMPTY),
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
        Connection connection = connection();
        ProgressStore store = store(folder, NO_SAVE_BEFORE_THE_END);
        try {
            helper.assertValueEqual(start(store, connection, player),
                    new ProgressUnavailable(ProgressUnavailable.Reason.TOO_NEW), "sent");
            store.changed(connection, LEVEL_THREE);
            store.end(connection).join();
        } finally {
            store.close();
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
        ProgressStore store = store(folder, NO_SAVE_BEFORE_THE_END);
        try {
            helper.assertValueEqual(start(store, connection(), player), new UploadProgress(Progress.EMPTY), "sent");
            helper.assertValueEqual(store.takeBrokenFileKeptAs(), Optional.of(player + ".json.broken"),
                    "the name the player is told");
            helper.assertValueEqual(store.takeBrokenFileKeptAs(), Optional.empty(), "told once");
            helper.assertValueEqual(Files.readString(file.resolveSibling(player + ".json.broken")), "{ broken",
                    "kept broken file");
        } finally {
            store.close();
        }
        helper.succeed();
    }

    @GameTest
    public void progressTooLargeToSendIsntSentAndIsLeftAlone(GameTestHelper helper) throws IOException {
        Path folder = folder();
        UUID player = UUID.randomUUID();
        Path file = folder.resolve(player + ".json");
        Map<ItemId, SrsState> states = new HashMap<>();
        for (int i = 0; i <= 65_536; i++) {
            states.put(new ItemId("item.test.item_" + i), new SrsState.Known());
        }
        ProgressFiles.write(file, new Progress(states));
        String saved = Files.readString(file);
        ProgressStore store = store(folder, NO_SAVE_BEFORE_THE_END);
        try {
            helper.assertValueEqual(start(store, connection(), player),
                    new ProgressUnavailable(ProgressUnavailable.Reason.TOO_LARGE), "sent");
            helper.assertValueEqual(start(store, connection(), player),
                    new ProgressUnavailable(ProgressUnavailable.Reason.TOO_LARGE), "sent again, so it was unlocked");
        } finally {
            store.close();
        }
        helper.assertValueEqual(Files.readString(file), saved, "the file");
        helper.succeed();
    }
}
