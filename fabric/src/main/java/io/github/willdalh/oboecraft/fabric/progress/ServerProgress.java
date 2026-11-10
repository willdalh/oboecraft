package io.github.willdalh.oboecraft.fabric.progress;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.LearningLanguage;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.lock.ProgressView;
import io.github.willdalh.oboecraft.fabric.network.ProgressSync;
import io.github.willdalh.oboecraft.fabric.network.StateUpdate;
import java.time.Instant;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;

/**
 * The progress of each player who is online. The player's client keeps their progress and sends it when they join,
 * see {@link ProgressUploads}, and the server holds it for the session. Every change goes through here, so it is sent
 * to the client, which saves it. The server never saves progress. Only used on the server thread.
 */
public final class ServerProgress implements ProgressView {
    private final InstantSource clock;
    private final Map<UUID, Session> online = new HashMap<>();
    private Settings settings = Settings.DEFAULTS;

    public ServerProgress(InstantSource clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * One online player's progress and the connection it belongs to. The connection, not the {@code ServerPlayer},
     * identifies the session, since respawning gives the same connection a new player.
     */
    private record Session(ServerGamePacketListenerImpl connection, LearningLanguage language, Progress progress) {
    }

    /**
     * Holds the progress the player's client sent for their session, and sends it back with the server settings. A
     * session of the same player that hasn't ended yet is replaced.
     */
    public void start(ServerPlayer player, LearningLanguage language, Progress progress) {
        online.put(player.getUUID(), new Session(player.connection, language, progress));
        ServerPlayNetworking.send(player, new ProgressSync(clock.instant(), language, progress, settings));
    }

    /** Forgets the progress of the session on this connection, when the player leaves. */
    public void end(ServerGamePacketListenerImpl connection) {
        UUID player = connection.player.getUUID();
        Session session = online.get(player);
        if (session != null && session.connection() == connection) {
            online.remove(player);
        }
    }

    /** Whether the player's progress is held. */
    public boolean hasSession(ServerPlayer player) {
        return online.containsKey(player.getUUID());
    }

    /** The server always has the final say, so its locks are always active. */
    @Override
    public boolean hasProgress(Player player) {
        return true;
    }

    /** The player's state for the item. A player whose progress isn't held counts as having none, so locked. */
    @Override
    public SrsState state(Player player, ItemId item) {
        Session session = online.get(player.getUUID());
        return session == null ? SrsState.Leveled.NEW : session.progress().state(item);
    }

    @Override
    public LearningLanguage language(Player player) {
        Session session = online.get(player.getUUID());
        return session == null ? LearningLanguage.JAPANESE : session.language();
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
        for (Session session : online.values()) {
            ServerPlayNetworking.send(session.connection().player,
                    new ProgressSync(clock.instant(), session.language(), session.progress(), settings));
        }
    }

    /**
     * Changes the player's state for the item, and sends it to them.
     *
     * @throws IllegalStateException if the player's progress isn't held
     */
    public void update(ServerPlayer player, ItemId item, SrsState state) {
        Session session = online.get(player.getUUID());
        if (session == null) {
            throw new IllegalStateException(player.getUUID() + " has no progress");
        }
        online.put(player.getUUID(), new Session(session.connection(), session.language(), session.progress().with(item, state)));
        sendState(player, item);
    }

    /** Sends the player their current state for the item. */
    public void sendState(ServerPlayer player, ItemId item) {
        ServerPlayNetworking.send(player, new StateUpdate(clock.instant(), item, state(player, item)));
    }
}
