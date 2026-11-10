package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.LearningLanguage;
import com.mojang.authlib.GameProfile;
import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;

/** Players for game tests that join the server like a real player, over a connection that goes nowhere. */
final class TestPlayers {
    /** The channel of each test player on the server, which holds the packets the server sent them. */
    private static final Map<UUID, EmbeddedChannel> CHANNELS = new ConcurrentHashMap<>();

    private TestPlayers() {
    }

    /**
     * A player who joins in survival mode with empty progress. Test players aren't configured, so the server holds the
     * progress a client without a progress file would send.
     */
    static ServerPlayer join(GameTestHelper helper, UUID id) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(id, "test-player"), false);
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        CHANNELS.put(id, new EmbeddedChannel(connection));
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        OboecraftMod.serverProgress().start(player, LearningLanguage.JAPANESE, Progress.EMPTY);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /** The player leaves the server, as when the server disconnects them, which ends their progress's session. */
    static void leave(ServerPlayer player) {
        player.connection.disconnect(Component.literal("The test is done"));
        CHANNELS.remove(player.getUUID());
    }

    /** The packets the server sent the player since the last call, or since they joined. */
    static List<Packet<?>> takeSentPackets(ServerPlayer player) {
        EmbeddedChannel channel = CHANNELS.get(player.getUUID());
        channel.flushOutbound();
        List<Packet<?>> packets = new ArrayList<>();
        for (Object message = channel.readOutbound(); message != null; message = channel.readOutbound()) {
            if (message instanceof Packet<?> packet) {
                packets.add(packet);
            }
        }
        return packets;
    }
}
