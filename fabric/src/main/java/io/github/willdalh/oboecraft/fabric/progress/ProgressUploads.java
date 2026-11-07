package io.github.willdalh.oboecraft.fabric.progress;

import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.network.ProgressUnavailable;
import io.github.willdalh.oboecraft.fabric.network.RequestProgress;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.jspecify.annotations.Nullable;

/**
 * The progress players' clients send while the players are configured, before they enter the world. The server asks
 * with {@link RequestProgress} and waits for the answer, so a player never enters the world without their progress.
 * Each player is asked every time they are configured, and only the first answer to the request counts. Only used on
 * the server thread.
 */
public final class ProgressUploads {
    /** How long the server waits for the client's progress before it disconnects the player: 30 seconds. */
    private static final int TIMEOUT_TICKS = 30 * 20;

    private static final ConfigurationTask.Type TASK_TYPE = new ConfigurationTask.Type("oboecraft:upload_progress");

    /**
     * The connection of each player who is being configured, until they enter the world. There is at most one per
     * account, so the progress a player enters the world with is always the one their own connection sent.
     */
    private final Map<UUID, Joining> joining = new HashMap<>();

    /** A connection on its way to the world: whether the server has asked, and the progress once the client sent it. */
    private static final class Joining {
        final ServerConfigurationPacketListenerImpl connection;
        boolean asked;
        @Nullable Progress progress;

        Joining(ServerConfigurationPacketListenerImpl connection) {
            this.connection = connection;
        }

        boolean awaitsAnswer() {
            return asked && progress == null;
        }
    }

    /**
     * Asks the player's client for their progress when the player is configured, as one of the tasks of
     * configuration, which run one after another, so the player stays in configuration until the client answers. An
     * earlier connection of the same account that hasn't entered the world yet is disconnected, as Minecraft does with
     * a player who logs in twice.
     */
    public void request(ServerConfigurationPacketListenerImpl connection) {
        Joining player = new Joining(connection);
        Joining earlier = joining.put(connection.getOwner().id(), player);
        if (earlier != null && earlier.connection != connection) {
            earlier.connection.disconnect(Component.translatable("multiplayer.disconnect.duplicate_login"));
        }
        connection.addTask(new UploadTask(player));
    }

    /** The client sent the player's progress. Ignored unless the server has asked this connection and still waits. */
    public void received(ServerConfigurationPacketListenerImpl connection, Progress progress) {
        Joining player = waitingFor(connection);
        if (player != null) {
            player.progress = progress;
            connection.completeTask(TASK_TYPE);
        }
    }

    /**
     * The client can't send the player's progress, so the player is disconnected. Ignored unless the server has asked
     * this connection and still waits.
     */
    public void unavailable(ServerConfigurationPacketListenerImpl connection, ProgressUnavailable.Reason reason) {
        if (waitingFor(connection) == null) {
            return;
        }
        joining.remove(connection.getOwner().id());
        OboecraftMod.LOGGER.warn("{} couldn't send their progress: {}", connection.getOwner().name(), reason);
        connection.disconnect(switch (reason) {
            case TOO_NEW -> Component.translatableWithFallback("oboecraft.progress.too_new",
                    "Your Oboecraft progress was saved by a newer version of Oboecraft. "
                            + "Update Oboecraft to play here.");
            case UNREADABLE -> Component.translatableWithFallback("oboecraft.progress.unreadable",
                    "Oboecraft couldn't read your progress file. Check your game's log.");
            case IN_USE -> Component.translatableWithFallback("oboecraft.progress.in_use",
                    "Your Oboecraft progress is in use by another running game. Close that game and join again.");
        });
    }

    private @Nullable Joining waitingFor(ServerConfigurationPacketListenerImpl connection) {
        Joining player = joining.get(connection.getOwner().id());
        return player != null && player.connection == connection && player.awaitsAnswer() ? player : null;
    }

    /** Forgets a connection that left while configured. */
    public void disconnected(ServerConfigurationPacketListenerImpl connection) {
        UUID player = connection.getOwner().id();
        Joining current = joining.get(player);
        if (current != null && current.connection == connection) {
            joining.remove(player);
        }
    }

    /**
     * Takes the progress the player sent, when they enter the world. Every configured player sent it, since
     * configuration waits for it. Only players placed in the world without being configured, like the game tests'
     * players, have none.
     */
    public Optional<Progress> take(UUID player) {
        Joining current = joining.remove(player);
        return current == null ? Optional.empty() : Optional.ofNullable(current.progress);
    }

    /** Sends the request, and disconnects the player if the answer doesn't arrive in time. */
    private final class UploadTask implements ConfigurationTask {
        private final Joining player;
        private int ticksWaited;

        UploadTask(Joining player) {
            this.player = player;
        }

        @Override
        public void start(Consumer<Packet<?>> send) {
            player.asked = true;
            send.accept(ServerConfigurationNetworking.createClientboundPacket(RequestProgress.INSTANCE));
        }

        /** Never finishes on its own: {@link #received} finishes it. */
        @Override
        public boolean tick() {
            if (player.awaitsAnswer() && ++ticksWaited == TIMEOUT_TICKS) {
                joining.remove(player.connection.getOwner().id(), player);
                OboecraftMod.LOGGER.warn("{} didn't send their progress in time", player.connection.getOwner().name());
                player.connection.disconnect(Component.translatableWithFallback("oboecraft.progress.upload_timeout",
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
