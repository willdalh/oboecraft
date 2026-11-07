package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.client.OboecraftClient;
import io.github.willdalh.oboecraft.fabric.network.ProgressUnavailable;
import io.github.willdalh.oboecraft.fabric.progress.ProgressStore;
import io.github.willdalh.oboecraft.fabric.progress.ProgressFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The player's progress in a real client in singleplayer: the client sends its file while the player is configured,
 * the file is locked while the player is in the world, and the server's changes are saved in it, never in the world.
 */
public class ProgressStoreClientGameTest implements FabricClientGameTest {
    private static final ItemId OAK_LOG = new ItemId("block.minecraft.oak_log");
    private static final SrsState LEVEL_THREE =
            new SrsState.Leveled(3, Optional.of(Instant.parse("2030-01-01T00:00:00Z")));
    private static final SrsState LEVEL_FOUR =
            new SrsState.Leveled(4, Optional.of(Instant.parse("2030-01-02T00:00:00Z")));

    @Override
    public void runTest(ClientGameTestContext context) {
        ProgressFolder.delete();
        UUID player = context.computeOnClient(client -> client.getUser().getProfileId());
        Path folder = ProgressStore.folder(FabricLoader.getInstance().getGameDir());
        Path file = folder.resolve(player + ".json");
        write(file, Progress.EMPTY.with(OAK_LOG, LEVEL_THREE));

        Path worldProgress;
        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            singleplayer.getConnection().waitForChunksRender();
            context.waitFor(client -> OboecraftClient.progress().hasProgress(client.player));
            check(context.computeOnClient(client -> OboecraftClient.progress().state(client.player, OAK_LOG))
                    .equals(LEVEL_THREE), "the client sent the file's progress");
            worldProgress = singleplayer.getServer().computeOnServer(server ->
                    server.getWorldPath(LevelResource.ROOT).resolve("oboecraft"));

            ProgressStore otherGame = new ProgressStore(folder, ProgressStore.SAVE_DELAY, () -> {
            });
            try {
                List<CustomPacketPayload> sent = new ArrayList<>();
                otherGame.start(new Connection(PacketFlow.CLIENTBOUND), player, sent::add).join();
                check(sent.equals(List.of(new ProgressUnavailable(ProgressUnavailable.Reason.IN_USE))),
                        "another game can't use the file, but sent " + sent);
            } finally {
                otherGame.close();
            }

            singleplayer.getServer().runOnServer(server -> OboecraftMod.serverProgress().update(
                    server.getPlayerList().getPlayers().getFirst(), OAK_LOG, LEVEL_FOUR));
            context.waitFor(client -> OboecraftClient.progress().state(client.player, OAK_LOG).equals(LEVEL_FOUR));
        }

        context.waitFor(client -> read(file).equals(
                new ProgressFiles.ReadResult.Loaded(Progress.EMPTY.with(OAK_LOG, LEVEL_FOUR))));
        check(!Files.exists(worldProgress), "the world has no progress files");
        ProgressFolder.delete();
    }

    private static void write(Path file, Progress progress) {
        try {
            ProgressFiles.write(file, progress);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ProgressFiles.ReadResult read(Path file) {
        try {
            return ProgressFiles.read(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
