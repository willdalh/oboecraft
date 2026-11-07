package io.github.willdalh.oboecraft.fabric.gametest;

import com.mojang.authlib.GameProfile;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.network.ProgressAnswer;
import io.github.willdalh.oboecraft.fabric.network.ProgressUnavailable;
import io.github.willdalh.oboecraft.fabric.network.UploadProgress;
import io.github.willdalh.oboecraft.fabric.progress.ProgressUploads;
import io.netty.channel.embedded.EmbeddedChannel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

/**
 * How the server waits for a player's progress while the player is configured, over connections that go nowhere. The
 * game tests run on the server thread, which ticks the upload's task. Fabric runs the receivers on the network
 * threads, so the tests answer from a thread of their own.
 */
public class ProgressUploadsGameTest {
    private static final Progress LEVEL_THREE = Progress.EMPTY.with(new ItemId("block.minecraft.oak_log"),
            new SrsState.Leveled(3, Optional.of(Instant.parse("2030-01-01T00:00:00Z"))));
    /** 30 seconds, how long the server waits for the answer. */
    private static final int TIMEOUT_TICKS = 30 * 20;

    private static ServerConfigurationPacketListenerImpl configuring(GameTestHelper helper, UUID player) {
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        return new ServerConfigurationPacketListenerImpl(helper.getLevel().getServer(), connection,
                CommonListenerCookie.createInitial(new GameProfile(player, "test-player"), false));
    }

    /** Starts the task as configuration does, and returns what it sent. */
    private static List<Packet<?>> start(ConfigurationTask task) {
        List<Packet<?>> sent = new ArrayList<>();
        task.start(sent::add);
        return sent;
    }

    /** Answers on a thread of its own, as Fabric does on a network thread, and waits until it has. */
    private static void answerFromTheNetwork(
            ProgressUploads uploads, ServerConfigurationPacketListenerImpl connection, ProgressAnswer answer) {
        Thread network = new Thread(() -> uploads.answered(connection, answer), "test network thread");
        network.start();
        try {
            network.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    /** What the player enters the world with over the connection. */
    private static Optional<Progress> taken(
            ProgressUploads uploads, UUID player, ServerConfigurationPacketListenerImpl connection) {
        return uploads.take(player, ProgressUploads.connectionOf(connection));
    }

    private static boolean isConnected(ServerConfigurationPacketListenerImpl connection) {
        return connection.isAcceptingMessages();
    }

    private static void leave(ServerConfigurationPacketListenerImpl connection) {
        if (isConnected(connection)) {
            connection.disconnect(Component.literal("The test is done"));
        }
    }

    @GameTest
    public void theProgressFromTheNetworkFinishesTheTaskWhenTheServerTicksIt(GameTestHelper helper) {
        ProgressUploads uploads = new ProgressUploads();
        UUID player = UUID.randomUUID();
        ServerConfigurationPacketListenerImpl connection = configuring(helper, player);
        ConfigurationTask task = uploads.request(connection);
        helper.assertValueEqual(start(task).size(), 1, "requests sent");
        helper.assertFalse(task.tick(), "the task waits for the answer");

        answerFromTheNetwork(uploads, connection, new UploadProgress(LEVEL_THREE));
        helper.assertTrue(task.tick(), "the task finishes on the server's tick");
        helper.assertValueEqual(taken(uploads, player, connection), Optional.of(LEVEL_THREE), "progress entered with");
        helper.assertValueEqual(taken(uploads, player, connection), Optional.empty(), "taken once");
        leave(connection);
        helper.succeed();
    }

    @GameTest
    public void onlyTheFirstAnswerToTheRequestCounts(GameTestHelper helper) {
        ProgressUploads uploads = new ProgressUploads();
        UUID player = UUID.randomUUID();
        ServerConfigurationPacketListenerImpl connection = configuring(helper, player);
        ConfigurationTask task = uploads.request(connection);
        answerFromTheNetwork(uploads, connection, new UploadProgress(Progress.EMPTY));
        helper.assertFalse(task.tick(), "an answer before the request doesn't count");

        start(task);
        answerFromTheNetwork(uploads, connection, new UploadProgress(LEVEL_THREE));
        answerFromTheNetwork(uploads, connection, new ProgressUnavailable(ProgressUnavailable.Reason.IN_USE));
        helper.assertTrue(task.tick(), "the task finishes");
        helper.assertTrue(isConnected(connection), "a later answer doesn't disconnect the player");
        helper.assertValueEqual(taken(uploads, player, connection), Optional.of(LEVEL_THREE), "progress");
        leave(connection);
        helper.succeed();
    }

    @GameTest
    public void aClientThatCantSendTheProgressIsDisconnected(GameTestHelper helper) {
        ProgressUploads uploads = new ProgressUploads();
        UUID player = UUID.randomUUID();
        ServerConfigurationPacketListenerImpl connection = configuring(helper, player);
        ConfigurationTask task = uploads.request(connection);
        start(task);

        answerFromTheNetwork(uploads, connection, new ProgressUnavailable(ProgressUnavailable.Reason.TOO_NEW));
        helper.assertTrue(isConnected(connection), "the network thread doesn't disconnect the player");
        helper.assertFalse(task.tick(), "the task never finishes");
        helper.assertFalse(isConnected(connection), "disconnected on the server's tick");
        helper.assertValueEqual(taken(uploads, player, connection), Optional.empty(), "progress");
        helper.succeed();
    }

    @GameTest
    public void aClientThatDoesntAnswerInTimeIsDisconnected(GameTestHelper helper) {
        ProgressUploads uploads = new ProgressUploads();
        ServerConfigurationPacketListenerImpl connection = configuring(helper, UUID.randomUUID());
        ConfigurationTask task = uploads.request(connection);
        start(task);

        for (int tick = 1; tick < TIMEOUT_TICKS; tick++) {
            task.tick();
        }
        helper.assertTrue(isConnected(connection), "still waiting just before the time is up");
        helper.assertFalse(task.tick(), "the task never finishes");
        helper.assertFalse(isConnected(connection), "disconnected when the time is up");
        helper.succeed();
    }

    @GameTest
    public void aSecondConnectionOfTheSameAccountDisconnectsTheFirst(GameTestHelper helper) {
        ProgressUploads uploads = new ProgressUploads();
        UUID player = UUID.randomUUID();
        ServerConfigurationPacketListenerImpl first = configuring(helper, player);
        ConfigurationTask firstTask = uploads.request(first);
        start(firstTask);
        ServerConfigurationPacketListenerImpl second = configuring(helper, player);
        ConfigurationTask secondTask = uploads.request(second);
        start(secondTask);
        helper.assertFalse(isConnected(first), "the first connection is disconnected");

        answerFromTheNetwork(uploads, first, new UploadProgress(Progress.EMPTY));
        answerFromTheNetwork(uploads, second, new UploadProgress(LEVEL_THREE));
        uploads.disconnected(first);
        helper.assertTrue(secondTask.tick(), "the second connection's task finishes");
        helper.assertValueEqual(taken(uploads, player, first), Optional.empty(),
                "the first connection enters the world without progress");
        helper.assertValueEqual(taken(uploads, player, second), Optional.of(LEVEL_THREE),
                "the second connection's progress");
        leave(second);
        helper.succeed();
    }

    @GameTest
    public void anEarlierConnectionOnItsWayIntoTheWorldDoesntTakeTheLaterConnectionsProgress(GameTestHelper helper) {
        ProgressUploads uploads = new ProgressUploads();
        UUID player = UUID.randomUUID();
        ServerConfigurationPacketListenerImpl first = configuring(helper, player);
        ConfigurationTask firstTask = uploads.request(first);
        start(firstTask);
        answerFromTheNetwork(uploads, first, new UploadProgress(Progress.EMPTY));
        helper.assertTrue(firstTask.tick(), "the first connection has finished its configuration");

        ServerConfigurationPacketListenerImpl second = configuring(helper, player);
        ConfigurationTask secondTask = uploads.request(second);
        start(secondTask);
        helper.assertValueEqual(taken(uploads, player, first), Optional.empty(),
                "the first connection, joining before its disconnect is handled, has no progress");

        answerFromTheNetwork(uploads, second, new UploadProgress(LEVEL_THREE));
        helper.assertTrue(secondTask.tick(), "the second connection's task finishes");
        helper.assertValueEqual(taken(uploads, player, second), Optional.of(LEVEL_THREE),
                "the second connection's progress");
        leave(second);
        helper.succeed();
    }
}
