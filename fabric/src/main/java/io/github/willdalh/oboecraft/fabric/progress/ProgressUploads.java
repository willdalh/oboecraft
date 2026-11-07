package io.github.willdalh.oboecraft.fabric.progress;

import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.mixin.ServerCommonPacketListenerImplAccessor;
import io.github.willdalh.oboecraft.fabric.network.ProgressAnswer;
import io.github.willdalh.oboecraft.fabric.network.ProgressUnavailable;
import io.github.willdalh.oboecraft.fabric.network.RequestProgress;
import io.github.willdalh.oboecraft.fabric.network.UploadProgress;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.jspecify.annotations.Nullable;

/**
 * The progress players' clients send while the players are configured, before they enter the world. The server asks
 * with {@link RequestProgress} and waits for the answer, so a player never enters the world without their progress.
 * Each player is asked every time they are configured, and only the first answer to the request counts.
 *
 * <p>Fabric runs the configuration of players and their receivers on the network threads, one for each group of
 * connections, and the server ticks the configuration on the server thread. So the network threads only record what
 * arrived, with atomic changes, and the upload's task acts on the answer when the server ticks it: it lets the player
 * continue, or disconnects them.
 */
public final class ProgressUploads {
    /** How long the server waits for the client's progress before it disconnects the player: 30 seconds. */
    private static final int TIMEOUT_TICKS = 30 * 20;

    private static final ConfigurationTask.Type TASK_TYPE = new ConfigurationTask.Type("oboecraft:upload_progress");

    /**
     * The upload of each player who is being configured, until they enter the world. There is at most one per
     * account, so the progress a player enters the world with is always the one their own connection sent.
     */
    private final Map<UUID, UploadTask> joining = new ConcurrentHashMap<>();

    /**
     * The task that asks the player's client for their progress, for the configuration of the player. Configuration
     * runs its tasks one after another, so the player stays in configuration until the client answers. An earlier
     * connection of the same account that hasn't entered the world yet is disconnected, as Minecraft does with a
     * player who logs in twice. Called on a network thread.
     */
    public ConfigurationTask request(ServerConfigurationPacketListenerImpl connection) {
        UploadTask task = new UploadTask(connection);
        UploadTask earlier = joining.put(task.player(), task);
        if (earlier != null && earlier.connection != connection) {
            earlier.connection.disconnect(Component.translatable("multiplayer.disconnect.duplicate_login"));
        }
        return task;
    }

    /**
     * The client answered. Ignored unless the server has asked this connection and is still waiting. The upload's task
     * acts on it when the server next ticks it. Called on a network thread.
     */
    public void answered(ServerConfigurationPacketListenerImpl connection, ProgressAnswer answer) {
        UploadTask task = joining.get(connection.getOwner().id());
        if (task != null && task.connection == connection && task.asked) {
            task.answer.compareAndSet(null, answer);
        }
    }

    /** Forgets a connection that left while configured. Called on any thread. */
    public void disconnected(ServerConfigurationPacketListenerImpl connection) {
        joining.computeIfPresent(connection.getOwner().id(),
                (player, task) -> task.connection == connection ? null : task);
    }

    /**
     * Takes the progress the player sent over this connection, when they enter the world. Every configured player
     * sent it, since configuration waits for it. Only players placed in the world without being configured, like the
     * game tests' players, have none, and so does a connection that a later connection of the same account replaced.
     * Called on the server thread.
     *
     * @param connection the connection the player entered the world over, the same as while they were configured
     */
    public Optional<Progress> take(UUID player, Connection connection) {
        UploadTask task = joining.get(player);
        if (task == null || connectionOf(task.connection) != connection || !joining.remove(player, task)) {
            return Optional.empty();
        }
        return task.answer.get() instanceof UploadProgress upload ? Optional.of(upload.progress()) : Optional.empty();
    }

    /** The network connection of a listener, which stays the same from configuration into the world. */
    public static Connection connectionOf(ServerCommonPacketListenerImpl listener) {
        return ((ServerCommonPacketListenerImplAccessor) listener).oboecraft$connection();
    }

    /**
     * Sends the request, and waits for the answer: finishes once the progress has arrived, and disconnects the player
     * if the client can't send it or doesn't answer in time.
     */
    private final class UploadTask implements ConfigurationTask {
        private final ServerConfigurationPacketListenerImpl connection;
        /** Whether the request was sent, so an answer counts. Set on a network thread or the server thread. */
        private volatile boolean asked;
        /** The client's first answer. Set on a network thread. */
        private final AtomicReference<@Nullable ProgressAnswer> answer = new AtomicReference<>();
        /** Only used on the server thread. */
        private int ticksWaited;
        /** Whether the player was disconnected. Only used on the server thread. */
        private boolean disconnected;

        UploadTask(ServerConfigurationPacketListenerImpl connection) {
            this.connection = connection;
        }

        UUID player() {
            return connection.getOwner().id();
        }

        @Override
        public void start(Consumer<Packet<?>> send) {
            asked = true;
            send.accept(ServerConfigurationNetworking.createClientboundPacket(RequestProgress.INSTANCE));
        }

        /** Called on the server thread. Finishes once the progress has arrived. */
        @Override
        public boolean tick() {
            if (disconnected) {
                return false;
            }
            return switch (answer.get()) {
                case UploadProgress upload -> true;
                case ProgressUnavailable unavailable -> {
                    OboecraftMod.LOGGER.warn("{} couldn't send their progress: {}",
                            connection.getOwner().name(), unavailable.reason());
                    disconnect(unavailableMessage(unavailable.reason()));
                    yield false;
                }
                case null -> {
                    if (++ticksWaited >= TIMEOUT_TICKS) {
                        OboecraftMod.LOGGER.warn("{} didn't send their progress in time", connection.getOwner().name());
                        disconnect(Component.translatableWithFallback("oboecraft.progress.upload_timeout",
                                "Your game didn't send your Oboecraft progress in time."));
                    }
                    yield false;
                }
            };
        }

        private void disconnect(Component message) {
            disconnected = true;
            joining.remove(player(), this);
            connection.disconnect(message);
        }

        @Override
        public Type type() {
            return TASK_TYPE;
        }
    }

    private static Component unavailableMessage(ProgressUnavailable.Reason reason) {
        return switch (reason) {
            case TOO_NEW -> Component.translatableWithFallback("oboecraft.progress.too_new",
                    "Your Oboecraft progress was saved by a newer version of Oboecraft. Update Oboecraft to play here.");
            case UNREADABLE -> Component.translatableWithFallback("oboecraft.progress.unreadable",
                    "Oboecraft couldn't read your progress file. Check your game's log.");
            case IN_USE -> Component.translatableWithFallback("oboecraft.progress.in_use",
                    "Your Oboecraft progress is in use by another running game. Close that game and join again.");
            case TOO_LARGE -> Component.translatableWithFallback("oboecraft.progress.too_large",
                    "Your Oboecraft progress file is too large to send. Check your game's log.");
        };
    }
}
