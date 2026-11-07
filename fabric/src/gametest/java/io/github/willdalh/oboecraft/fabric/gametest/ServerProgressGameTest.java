package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** The server holds each player's progress, which their client sent, for their session. */
public class ServerProgressGameTest {
    private static final ItemId OAK_LOG = new ItemId("block.minecraft.oak_log");
    private static final SrsState LEVEL_THREE =
            new SrsState.Leveled(3, Optional.of(Instant.parse("2030-01-01T00:00:00Z")));

    @GameTest
    public void theProgressTheClientSentIsHeldForTheSession(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        OboecraftMod.serverProgress().start(player, Progress.EMPTY.with(OAK_LOG, LEVEL_THREE));
        helper.assertValueEqual(OboecraftMod.serverProgress().state(player, OAK_LOG), LEVEL_THREE, "sent state");

        SrsState passed = new SrsState.Leveled(4, Optional.of(Instant.parse("2030-01-02T00:00:00Z")));
        OboecraftMod.serverProgress().update(player, OAK_LOG, passed);
        helper.assertValueEqual(OboecraftMod.serverProgress().state(player, OAK_LOG), passed, "changed state");
        TestPlayers.leave(player);

        helper.assertFalse(OboecraftMod.serverProgress().hasSession(player), "progress is forgotten on leave");
        helper.succeed();
    }

    @GameTest
    public void aPlayerWhoJoinsBeforeTheirEarlierSessionIsGoneKeepsTheNewSession(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer earlier = TestPlayers.join(helper, id);
        ServerPlayer later = TestPlayers.join(helper, id);
        OboecraftMod.serverProgress().update(later, OAK_LOG, LEVEL_THREE);
        TestPlayers.leave(earlier);

        helper.assertTrue(OboecraftMod.serverProgress().hasSession(later), "the later session has progress");
        helper.assertValueEqual(OboecraftMod.serverProgress().state(later, OAK_LOG), LEVEL_THREE, "state");
        TestPlayers.leave(later);
        helper.succeed();
    }

    @GameTest
    public void aPlayerWhoRespawnedKeepsTheirProgressUntilTheyLeave(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        OboecraftMod.serverProgress().update(player, OAK_LOG, LEVEL_THREE);

        ServerPlayer respawned = helper.getLevel().getServer().getPlayerList()
                .respawn(player, false, Entity.RemovalReason.KILLED);
        helper.assertValueEqual(OboecraftMod.serverProgress().state(respawned, OAK_LOG), LEVEL_THREE, "state");
        TestPlayers.leave(respawned);

        helper.assertFalse(OboecraftMod.serverProgress().hasSession(respawned), "progress is forgotten on leave");
        helper.succeed();
    }
}
