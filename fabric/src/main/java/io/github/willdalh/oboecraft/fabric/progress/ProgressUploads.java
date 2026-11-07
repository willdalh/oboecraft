package io.github.willdalh.oboecraft.fabric.progress;

import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.network.ProgressUnavailable;
import io.github.willdalh.oboecraft.fabric.network.RequestProgress;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

/**
 * Players' own progress, which their clients send while the players are configured, before they enter the world.
 * The server asks with {@link RequestProgress} and waits for the answer, so a player who uses their own progress
 * never plays without it. Each player is asked once per join, and only the first answer counts. Only used on the
 * server thread.
 */
public final class ProgressUploads {
    /** How long the server waits for the client's progress before it disconnects the player. */
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final ConfigurationTask.Type TASK_TYPE = new ConfigurationTask.Type("oboecraft:upload_progress");

    private final InstantSource clock;
    /** The players the server has asked, who haven't answered yet. */
    private final Set<ServerConfigurationPacketListenerImpl> awaiting = new HashSet<>();
    /** The progress players sent, until they enter the world. */
    private final Map<UUID, Uploaded> uploaded = new HashMap<>();

    private record Uploaded(ServerConfigurationPacketListenerImpl connection, Progress progress) {
    }

    public ProgressUploads(InstantSource clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Sets up a player who joins and is configured. When the server uses own progress, asks the player's client for
     * it, as one of the tasks of configuration, which run one after another, so the player stays in configuration
     * until the client answers. Progress an earlier connection of the player sent, which left before it entered the
     * world, is forgotten first, so it is never used for this one.
     */
    public void configure(ServerConfigurationPacketListenerImpl connection, boolean useOwnProgress) {
        uploaded.remove(connection.getOwner().id());
        if (useOwnProgress) {
            connection.addTask(new UploadTask(connection));
        }
    }

    /**
     * The client sent the player's own progress. Ignored unless the server has asked and is still waiting, so only the
     * first answer to the request counts.
     */
    public void received(ServerConfigurationPacketListenerImpl connection, Progress progress) {
        if (!awaiting.remove(connection)) {
            return;
        }
        uploaded.put(connection.getOwner().id(), new Uploaded(connection, progress));
        connection.completeTask(TASK_TYPE);
    }

    /**
     * The client can't send the player's own progress, so the player is disconnected. Ignored unless the server has
     * asked and is still waiting.
     */
    public void unavailable(ServerConfigurationPacketListenerImpl connection, ProgressUnavailable.Reason reason) {
        if (!awaiting.remove(connection)) {
            return;
        }
        OboecraftMod.LOGGER.warn("{} couldn't send their own progress: {}", connection.getOwner().name(), reason);
        connection.disconnect(switch (reason) {
            case TOO_NEW -> Component.translatableWithFallback("oboecraft.progress.too_new",
                    "Your Oboecraft progress was saved by a newer version of Oboecraft. "
                            + "Update Oboecraft to play here.");
            case UNREADABLE -> Component.translatableWithFallback("oboecraft.progress.own_unreadable",
                    "Oboecraft couldn't read your progress file. Check your game's log.");
            case IN_USE -> Component.translatableWithFallback("oboecraft.progress.in_use",
                    "Your Oboecraft progress is in use by another running game. Close that game and join again.");
        });
    }

    /** Forgets a player who left while configured, so their progress isn't kept for nothing. */
    public void disconnected(ServerConfigurationPacketListenerImpl connection) {
        awaiting.remove(connection);
        UUID player = connection.getOwner().id();
        Uploaded sent = uploaded.get(player);
        if (sent != null && sent.connection() == connection) {
            uploaded.remove(player);
        }
    }

    /** Takes the own progress the player sent before they entered the world, if they sent any. */
    public Optional<Progress> take(UUID player) {
        return Optional.ofNullable(uploaded.remove(player)).map(Uploaded::progress);
    }

    /** Sends the request, and disconnects the player if the answer doesn't arrive in time. */
    private final class UploadTask implements ConfigurationTask {
        private final ServerConfigurationPacketListenerImpl connection;
        private Instant deadline = Instant.MAX;

        UploadTask(ServerConfigurationPacketListenerImpl connection) {
            this.connection = connection;
        }

        @Override
        public void start(Consumer<Packet<?>> send) {
            awaiting.add(connection);
            deadline = clock.instant().plus(TIMEOUT);
            send.accept(ServerConfigurationNetworking.createClientboundPacket(RequestProgress.INSTANCE));
        }

        /** Never finishes on its own: {@link #received} finishes it. */
        @Override
        public boolean tick() {
            if (awaiting.contains(connection) && !clock.instant().isBefore(deadline)) {
                awaiting.remove(connection);
                OboecraftMod.LOGGER.warn("{} didn't send their own progress in time",
                        connection.getOwner().name());
                connection.disconnect(Component.translatableWithFallback("oboecraft.progress.upload_timeout",
                        "Your game didn't send your Oboecraft progress in time."));
            }
            return false;
        }

        @Override
        public Type type() {
            return TASK_TYPE;
        }
    }
}
