package io.github.willdalh.oboecraft.fabric.progress;

import io.github.willdalh.oboecraft.core.Oboecraft;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.lock.ProgressView;
import io.github.willdalh.oboecraft.fabric.network.ProgressSync;
import io.github.willdalh.oboecraft.fabric.network.StateUpdate;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The progress of each player who is online. Every change goes through here, so it is marked as unsaved and sent to
 * the client. Only used on the server thread.
 */
public final class ServerProgress implements ProgressView {
    private static final Component UNREADABLE = Component.translatableWithFallback("oboecraft.progress.unreadable",
            "Oboecraft couldn't read your progress file. Ask the server owner to check the server log.");

    private final InstantSource clock;
    private final Map<UUID, PlayerProgress> online = new HashMap<>();

    public ServerProgress(InstantSource clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** One online player's progress, where it is saved, and the player session it belongs to. */
    private static final class PlayerProgress {
        final ServerPlayer session;
        final Path file;
        Progress progress;
        boolean unsaved;

        PlayerProgress(ServerPlayer session, Path file, Progress progress, boolean unsaved) {
            this.session = session;
            this.file = file;
            this.progress = progress;
            this.unsaved = unsaved;
        }
    }

    /**
     * Loads the player's progress when they join, and sends it to them. A file from a newer version of the mod, or
     * one that can't be read, disconnects the player and is left untouched. A file that isn't valid progress is kept
     * as {@code .broken}, and the player starts with empty progress and is told in chat.
     */
    public void load(ServerPlayer player) {
        PlayerProgress earlierSession = online.get(player.getUUID());
        if (earlierSession != null) {
            // The player joined again before their earlier session was gone. Its progress is newer than the file.
            online.put(player.getUUID(), new PlayerProgress(
                    player, earlierSession.file, earlierSession.progress, earlierSession.unsaved));
            ServerPlayNetworking.send(player, new ProgressSync(clock.instant(), earlierSession.progress));
            return;
        }
        Path file = progressFile(player);
        ProgressFiles.ReadResult result;
        try {
            result = ProgressFiles.read(file);
        } catch (IOException e) {
            OboecraftMod.LOGGER.error("Couldn't read the progress file {}", file, e);
            player.connection.disconnect(UNREADABLE);
            return;
        }
        Optional<Progress> progress = switch (result) {
            case ProgressFiles.ReadResult.Loaded loaded -> Optional.of(loaded.progress());
            case ProgressFiles.ReadResult.Missing missing -> Optional.of(Progress.EMPTY);
            case ProgressFiles.ReadResult.TooNew tooNew -> {
                OboecraftMod.LOGGER.warn("The progress file {} has format version {}, newer than {}",
                        file, tooNew.version(), ProgressFiles.VERSION);
                player.connection.disconnect(Component.translatableWithFallback("oboecraft.progress.too_new",
                        "Your Oboecraft progress was saved by a newer version of Oboecraft. "
                                + "Update Oboecraft to play here."));
                yield Optional.empty();
            }
            case ProgressFiles.ReadResult.Broken broken -> keepBroken(player, file, broken.reason());
        };
        progress.ifPresent(loaded -> {
            online.put(player.getUUID(), new PlayerProgress(player, file, loaded, false));
            ServerPlayNetworking.send(player, new ProgressSync(clock.instant(), loaded));
        });
    }

    /** Moves a broken file out of the way and tells the player, or disconnects them if it can't be moved. */
    private static Optional<Progress> keepBroken(ServerPlayer player, Path file, String reason) {
        OboecraftMod.LOGGER.error("The progress file {} isn't valid progress: {}", file, reason);
        Path kept;
        try {
            kept = ProgressFiles.keepBroken(file);
        } catch (IOException e) {
            OboecraftMod.LOGGER.error("Couldn't move the broken progress file {}", file, e);
            player.connection.disconnect(UNREADABLE);
            return Optional.empty();
        }
        String keptName = kept.getFileName().toString();
        player.sendSystemMessage(Component.translatableWithFallback("oboecraft.progress.broken",
                "Your Oboecraft progress file couldn't be read, so you start with empty progress. "
                        + "The broken file was kept as %s.", keptName));
        return Optional.of(Progress.EMPTY);
    }

    /**
     * Where the player's progress is saved: in the game folder for the singleplayer player and the host of a world
     * opened to LAN, so it is shared across worlds, and in the world folder for everyone else.
     */
    private static Path progressFile(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Path folder = server.isSingleplayerOwner(player.nameAndId())
                ? FabricLoader.getInstance().getGameDir()
                : server.getWorldPath(LevelResource.ROOT);
        return folder.resolve(Oboecraft.MOD_ID).resolve("progress").resolve(player.getUUID() + ".json");
    }

    /** Whether the player's progress is loaded. */
    public boolean isLoaded(ServerPlayer player) {
        return online.containsKey(player.getUUID());
    }

    /** The server always has the final say, so its locks are always active. */
    @Override
    public boolean hasProgress(Player player) {
        return true;
    }

    /** The player's state for the item. A player whose progress isn't loaded counts as having none, so locked. */
    @Override
    public SrsState state(Player player, ItemId item) {
        PlayerProgress playerProgress = online.get(player.getUUID());
        return playerProgress == null ? SrsState.Leveled.NEW : playerProgress.progress.state(item);
    }

    @Override
    public Instant now() {
        return clock.instant();
    }

    /**
     * Changes the player's state for the item, and sends it to them.
     *
     * @throws IllegalStateException if the player's progress isn't loaded
     */
    public void update(ServerPlayer player, ItemId item, SrsState state) {
        PlayerProgress playerProgress = online.get(player.getUUID());
        if (playerProgress == null) {
            throw new IllegalStateException(player.getUUID() + " has no progress loaded");
        }
        playerProgress.progress = playerProgress.progress.with(item, state);
        playerProgress.unsaved = true;
        sendState(player, item);
    }

    /** Sends the player their current state for the item. */
    public void sendState(ServerPlayer player, ItemId item) {
        ServerPlayNetworking.send(player, new StateUpdate(clock.instant(), item, state(player, item)));
    }

    /** Saves the progress of every online player that has unsaved changes. */
    public void saveAll() {
        online.values().forEach(ServerProgress::save);
    }

    /**
     * Saves the player's progress, if it has unsaved changes, and forgets it. Called when the player leaves. Does
     * nothing if the progress has moved on to a newer session of the same player.
     */
    public void unload(ServerPlayer player) {
        PlayerProgress playerProgress = online.get(player.getUUID());
        if (playerProgress != null && playerProgress.session == player) {
            online.remove(player.getUUID());
            save(playerProgress);
        }
    }

    /** Saves the progress if it has unsaved changes. A failed save is logged and tried again at the next save. */
    private static void save(PlayerProgress playerProgress) {
        if (!playerProgress.unsaved) {
            return;
        }
        try {
            ProgressFiles.write(playerProgress.file, playerProgress.progress);
            playerProgress.unsaved = false;
        } catch (IOException e) {
            OboecraftMod.LOGGER.error("Couldn't save the progress file {}", playerProgress.file, e);
        }
    }
}
