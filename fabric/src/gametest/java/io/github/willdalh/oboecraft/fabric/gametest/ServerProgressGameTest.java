package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.Oboecraft;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.progress.ProgressFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Loading progress when a player joins and saving it when they leave, on a server. Test players join without being
 * configured, so they send no own progress and use server progress, unless a test gives them own progress.
 */
public class ServerProgressGameTest {
    private static final ItemId OAK_LOG = new ItemId("block.minecraft.oak_log");
    private static final SrsState LEVEL_THREE =
            new SrsState.Leveled(3, Optional.of(Instant.parse("2030-01-01T00:00:00Z")));

    /** On a server, each player's progress is in the world folder. */
    private static Path progressFile(GameTestHelper helper, UUID player) {
        return helper.getLevel().getServer().getWorldPath(LevelResource.ROOT)
                .resolve(Oboecraft.MOD_ID).resolve("progress").resolve(player + ".json");
    }

    @GameTest
    public void progressIsLoadedOnJoinAndSavedOnLeave(GameTestHelper helper) throws IOException {
        UUID id = UUID.randomUUID();
        Path file = progressFile(helper, id);
        ProgressFiles.write(file, Progress.EMPTY.with(OAK_LOG, LEVEL_THREE));

        ServerPlayer player = TestPlayers.join(helper, id);
        helper.assertValueEqual(OboecraftMod.serverProgress().state(player, OAK_LOG), LEVEL_THREE, "loaded state");

        SrsState passed = new SrsState.Leveled(4, Optional.of(Instant.parse("2030-01-02T00:00:00Z")));
        OboecraftMod.serverProgress().update(player, OAK_LOG, passed);
        TestPlayers.leave(player);

        helper.assertFalse(OboecraftMod.serverProgress().isLoaded(player), "progress is forgotten on leave");
        helper.assertValueEqual(ProgressFiles.read(file),
                new ProgressFiles.ReadResult.Loaded(Progress.EMPTY.with(OAK_LOG, passed)), "saved progress");
        helper.succeed();
    }

    @GameTest
    public void ownProgressIsUsedAndNeverSavedOnTheServer(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = TestPlayers.join(helper, id);
        OboecraftMod.serverProgress().load(player, Optional.of(Progress.EMPTY.with(OAK_LOG, LEVEL_THREE)));
        helper.assertTrue(OboecraftMod.serverProgress().usesOwnProgress(player), "the player uses own progress");
        helper.assertValueEqual(OboecraftMod.serverProgress().state(player, OAK_LOG), LEVEL_THREE, "own state");

        OboecraftMod.serverProgress().update(player, OAK_LOG,
                new SrsState.Leveled(4, Optional.of(Instant.parse("2030-01-02T00:00:00Z"))));
        OboecraftMod.serverProgress().saveAll();
        TestPlayers.leave(player);

        helper.assertFalse(Files.exists(progressFile(helper, id)), "no server progress file");
        helper.succeed();
    }

    @GameTest
    public void aPlayerWhoSentNoOwnProgressUsesServerProgress(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        try {
            helper.assertFalse(OboecraftMod.serverProgress().usesOwnProgress(player), "server progress");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void aPlayerWhoJoinsBeforeTheirEarlierSessionIsGoneKeepsItsProgress(GameTestHelper helper)
            throws IOException {
        UUID id = UUID.randomUUID();
        ServerPlayer earlier = TestPlayers.join(helper, id);
        OboecraftMod.serverProgress().update(earlier, OAK_LOG, LEVEL_THREE);

        ServerPlayer later = TestPlayers.join(helper, id);
        TestPlayers.leave(earlier);

        helper.assertTrue(OboecraftMod.serverProgress().isLoaded(later), "the later session has progress");
        helper.assertValueEqual(OboecraftMod.serverProgress().state(later, OAK_LOG), LEVEL_THREE, "state");
        TestPlayers.leave(later);
        helper.assertValueEqual(ProgressFiles.read(progressFile(helper, id)),
                new ProgressFiles.ReadResult.Loaded(Progress.EMPTY.with(OAK_LOG, LEVEL_THREE)), "saved progress");
        helper.succeed();
    }

    @GameTest
    public void progressIsSavedWhenAPlayerWhoRespawnedLeaves(GameTestHelper helper) throws IOException {
        UUID id = UUID.randomUUID();
        ServerPlayer player = TestPlayers.join(helper, id);
        OboecraftMod.serverProgress().update(player, OAK_LOG, LEVEL_THREE);

        ServerPlayer respawned = helper.getLevel().getServer().getPlayerList()
                .respawn(player, false, Entity.RemovalReason.KILLED);
        helper.assertValueEqual(OboecraftMod.serverProgress().state(respawned, OAK_LOG), LEVEL_THREE, "state");
        TestPlayers.leave(respawned);

        helper.assertFalse(OboecraftMod.serverProgress().isLoaded(respawned), "progress is forgotten on leave");
        helper.assertValueEqual(ProgressFiles.read(progressFile(helper, id)),
                new ProgressFiles.ReadResult.Loaded(Progress.EMPTY.with(OAK_LOG, LEVEL_THREE)), "saved progress");
        helper.succeed();
    }

    @GameTest
    public void aBrokenFileIsKeptAndThePlayerStartsEmpty(GameTestHelper helper) throws IOException {
        UUID id = UUID.randomUUID();
        Path file = progressFile(helper, id);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{ broken");

        ServerPlayer player = TestPlayers.join(helper, id);
        try {
            helper.assertTrue(OboecraftMod.serverProgress().isLoaded(player), "the player has progress");
            helper.assertValueEqual(OboecraftMod.serverProgress().state(player, OAK_LOG), SrsState.Leveled.NEW,
                    "state");
            helper.assertValueEqual(Files.readString(file.resolveSibling(id + ".json.broken")), "{ broken",
                    "kept broken file");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void aFileFromANewerVersionDisconnectsThePlayerAndIsLeftAlone(GameTestHelper helper) throws IOException {
        UUID id = UUID.randomUUID();
        Path file = progressFile(helper, id);
        Files.createDirectories(file.getParent());
        String newer = "{\"version\": 99, \"items\": {}}";
        Files.writeString(file, newer);

        ServerPlayer player = TestPlayers.join(helper, id);
        helper.assertFalse(OboecraftMod.serverProgress().isLoaded(player), "the player has no progress");
        helper.assertTrue(player.hasDisconnected(), "the player is disconnected");

        helper.assertValueEqual(Files.readString(file), newer, "the newer file");
        helper.succeed();
    }
}
