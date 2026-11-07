package io.github.willdalh.oboecraft.fabric.gametest;

import com.mojang.authlib.GameProfile;
import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;

/** Players for game tests that join the server like a real player, over a connection that goes nowhere. */
final class TestPlayers {
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
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        OboecraftMod.serverProgress().start(player, Progress.EMPTY);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /** The player leaves the server, as when the server disconnects them, which ends their progress's session. */
    static void leave(ServerPlayer player) {
        player.connection.disconnect(Component.literal("The test is done"));
    }
}
