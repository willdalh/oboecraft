package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.lock.StackLocation;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** Game modes without locks: creative and spectator mode cancel a review, and keep the SRS state. */
public class GameModeGameTest {
    private static final ItemId OAK_LOG = new ItemId("block.minecraft.oak_log");
    private static final StackLocation MAIN_HAND = new StackLocation.InventorySlot(0);

    private static ServerPlayer playerReviewingOakLog(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_LOG, 4));
        OboecraftMod.serverReviews().start(player, 1, MAIN_HAND);
        return player;
    }

    @GameTest
    public void creativeModeCancelsAReviewAndKeepsTheState(GameTestHelper helper) {
        ServerPlayer player = playerReviewingOakLog(helper);
        try {
            OboecraftMod.serverReviews().answer(player, 1, "まちがい");
            helper.assertValueEqual(ActiveReviews.item(player), Optional.of(OAK_LOG), "item under review");

            player.setGameMode(GameType.CREATIVE);
            helper.assertValueEqual(ActiveReviews.item(player), Optional.empty(), "item under review in creative");
            helper.assertValueEqual(OboecraftMod.serverProgress().state(player, OAK_LOG), SrsState.Leveled.NEW,
                    "state after creative mode");

            // An answer for the cancelled review changes nothing.
            OboecraftMod.serverReviews().answer(player, 1, "オークの原木");
            helper.assertValueEqual(OboecraftMod.serverProgress().state(player, OAK_LOG), SrsState.Leveled.NEW,
                    "state after an answer for the cancelled review");

            // Back in survival mode, the oak log is still locked and can be reviewed again.
            player.setGameMode(GameType.SURVIVAL);
            OboecraftMod.serverReviews().start(player, 2, MAIN_HAND);
            helper.assertValueEqual(ActiveReviews.item(player), Optional.of(OAK_LOG), "item under review");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void spectatorModeCancelsAReview(GameTestHelper helper) {
        ServerPlayer player = playerReviewingOakLog(helper);
        try {
            player.setGameMode(GameType.SPECTATOR);
            helper.assertValueEqual(ActiveReviews.item(player), Optional.empty(), "item under review in spectator");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void adventureModeKeepsAReview(GameTestHelper helper) {
        ServerPlayer player = playerReviewingOakLog(helper);
        try {
            player.setGameMode(GameType.ADVENTURE);
            helper.assertValueEqual(ActiveReviews.item(player), Optional.of(OAK_LOG), "item under review in adventure");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }
}
