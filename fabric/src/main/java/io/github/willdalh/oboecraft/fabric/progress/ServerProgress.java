package io.github.willdalh.oboecraft.fabric.progress;

import io.github.willdalh.oboecraft.core.Oboecraft;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.settings.Settings;
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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * The progress of each player who is online. Every change goes through here, so it is marked as unsaved and sent to
 * the client. A player uses their own progress, which their client sent while they were configured and which their
 * client saves, or else server progress, which is saved in the world folder. Only used on the server thread.
 */
public final class ServerProgress implements ProgressView {
    private static final Component UNREADABLE = Component.translatableWithFallback("oboecraft.progress.unreadable",
            "Oboecraft couldn't read your progress file. Ask the server owner to check the server log.");

    private final InstantSource clock;
    private final Map<UUID, PlayerProgress> online = new HashMap<>();
    private Settings settings = Settings.DEFAULTS;

    public ServerProgress(InstantSource clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * One online player's progress, where it is saved, and the connection it belongs to. The connection, not the
     * {@code ServerPlayer}, identifies the session, since respawning gives the same connection a new player.
     */
    private static final class PlayerProgress {
        final ServerGamePacketListenerImpl connection;
        /** The server progress file, or null for own progress, which the player's client saves. */
        final @Nullable Path file;
        Progress progress;
        boolean unsaved;

        PlayerProgress(ServerGamePacketListenerImpl connection, @Nullable Path file, Progress progress) {
            this.connection = connection;
            this.file = file;
            this.progress = progress;
        }
    }

    /**
     * Loads the player's progress when they join, and sends it to them: the own progress their client sent while they
     * were configured, see {@link ProgressUploads}, or else their server progress file. A file from a newer version of the mod, or one that can't be
     * read, disconnects the player and is left untouched. A file that isn't valid progress is kept as
     * {@code .broken}, and the player starts with empty progress and is told in chat.
     */
    public void load(ServerPlayer player, Optional<Progress> ownProgress) {
        PlayerProgress earlierSession = online.remove(player.getUUID());
        if (earlierSession != null) {
            // An earlier session of this player hasn't left yet. Its changes are saved first, so the file is current.
            save(earlierSession);
        }
        if (ownProgress.isPresent()) {
            start(player, null, ownProgress.get());
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
        progress.ifPresent(loaded -> start(player, file, loaded));
    }

    /** Holds the player's progress for the session, and sends it to them. */
    private void start(ServerPlayer player, @Nullable Path file, Progress progress) {
        online.put(player.getUUID(), new PlayerProgress(player.connection, file, progress));
        ServerPlayNetworking.send(player, new ProgressSync(clock.instant(), progress, settings));
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

    /** Where the player's server progress is saved: in the world folder. */
    private static Path progressFile(ServerPlayer player) {
        return player.level().getServer().getWorldPath(LevelResource.ROOT)
                .resolve(Oboecraft.MOD_ID).resolve("progress").resolve(player.getUUID() + ".json");
    }

    /** Whether the player uses their own progress, which their client saves, in this session. */
    public boolean usesOwnProgress(ServerPlayer player) {
        PlayerProgress playerProgress = online.get(player.getUUID());
        return playerProgress != null && playerProgress.file == null;
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

    /** The server settings, which every rule on the server reads. */
    @Override
    public Settings settings() {
        return settings;
    }

    /**
     * Uses new server settings from now on, and sends them to every online player with their progress, so their
     * client's lock check agrees with the server's at once.
     */
    public void useSettings(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        for (PlayerProgress playerProgress : online.values()) {
            ServerPlayNetworking.send(playerProgress.connection.player,
                    new ProgressSync(clock.instant(), playerProgress.progress, settings));
        }
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
     * Saves the progress of the player on this connection, if it has unsaved changes, and forgets it. Called when the
     * player leaves. Does nothing if a newer session of the same player has taken over.
     */
    public void unload(ServerGamePacketListenerImpl connection) {
        UUID player = connection.player.getUUID();
        PlayerProgress playerProgress = online.get(player);
        if (playerProgress != null && playerProgress.connection == connection) {
            online.remove(player);
            save(playerProgress);
        }
    }

    /**
     * Saves server progress if it has unsaved changes. A failed save is logged and tried again at the next save. Own
     * progress is never saved here: the player's client saves it.
     */
    private static void save(PlayerProgress playerProgress) {
        if (playerProgress.file == null || !playerProgress.unsaved) {
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
