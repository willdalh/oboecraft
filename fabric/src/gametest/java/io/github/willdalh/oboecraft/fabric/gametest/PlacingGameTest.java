package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.progress.ServerProgress;
import io.github.willdalh.oboecraft.fabric.review.ServerReviews;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** The playable loop on the server: a locked block can't be placed, a pass unlocks it, and it locks again. */
public class PlacingGameTest {
    private static final ItemId OAK_LOG = new ItemId("block.minecraft.oak_log");

    /** Right-clicks the top of the block at {@code below} with the stack in the main hand, as the server does it. */
    private static InteractionResult placeOnTopOf(GameTestHelper helper, ServerPlayer player, BlockPos below) {
        BlockPos pos = helper.absolutePos(below);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
        return player.gameMode.useItemOn(
                player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    private static ServerPlayer playerWithOakLogs(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        Vec3 standingSpot = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 4)));
        player.snapTo(standingSpot.x(), standingSpot.y(), standingSpot.z());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_LOG, 4));
        return player;
    }

    @GameTest
    public void aLockedBlockIsPlacedAfterAPassAndLocksAgainTenMinutesLater(GameTestHelper helper) {
        ServerProgress progress = OboecraftMod.serverProgress();
        ServerReviews reviews = OboecraftMod.serverReviews();
        for (int x = 0; x < 3; x++) {
            helper.setBlock(new BlockPos(x, 0, 0), Blocks.STONE);
        }
        ServerPlayer player = playerWithOakLogs(helper);
        Instant start = Instant.parse("2026-11-06T12:00:00Z");
        OboecraftMod.clock().setSource(InstantSource.fixed(start));
        try {
            // A new item is locked, so placing it is stopped.
            helper.assertTrue(placeOnTopOf(helper, player, new BlockPos(0, 0, 0)) instanceof InteractionResult.Fail,
                    "placing a locked oak log fails");
            helper.assertBlockNotPresent(Blocks.OAK_LOG, new BlockPos(0, 1, 0));
            helper.assertValueEqual(player.getMainHandItem().getCount(), 4, "oak logs in hand");

            // The client asks for the review. A wrong answer keeps it going, the right one, in romaji, passes.
            reviews.start(player, 1, InteractionHand.MAIN_HAND);
            helper.assertValueEqual(reviews.activeItem(player), Optional.of(OAK_LOG), "item under review");
            reviews.answer(player, 1, "オークの減木");
            helper.assertValueEqual(reviews.activeItem(player), Optional.of(OAK_LOG), "item under review");
            helper.assertValueEqual(progress.state(player, OAK_LOG), SrsState.Leveled.NEW, "state after wrong");
            reviews.answer(player, 1, "ooku no genboku");
            helper.assertValueEqual(reviews.activeItem(player), Optional.empty(), "item under review");
            helper.assertValueEqual(progress.state(player, OAK_LOG),
                    new SrsState.Leveled(1, Optional.of(start.plus(Duration.ofMinutes(10)))), "state after pass");

            // Unlocked, so placing works, and reviewing early isn't possible.
            helper.assertTrue(placeOnTopOf(helper, player, new BlockPos(0, 0, 0)).consumesAction(),
                    "placing an unlocked oak log succeeds");
            helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(0, 1, 0));
            reviews.start(player, 1, InteractionHand.MAIN_HAND);
            helper.assertValueEqual(reviews.activeItem(player), Optional.empty(), "item under review");

            // Just before ten minutes it is still unlocked, and at ten minutes it is locked again.
            OboecraftMod.clock().setSource(InstantSource.fixed(start.plus(Duration.ofMinutes(10)).minusMillis(1)));
            helper.assertTrue(placeOnTopOf(helper, player, new BlockPos(1, 0, 0)).consumesAction(),
                    "placing just before the oak log locks succeeds");
            OboecraftMod.clock().setSource(InstantSource.fixed(start.plus(Duration.ofMinutes(10))));
            helper.assertTrue(placeOnTopOf(helper, player, new BlockPos(2, 0, 0)) instanceof InteractionResult.Fail,
                    "placing the oak log after it locked again fails");
            helper.assertBlockNotPresent(Blocks.OAK_LOG, new BlockPos(2, 1, 0));
            reviews.start(player, 1, InteractionHand.MAIN_HAND);
            helper.assertValueEqual(reviews.activeItem(player), Optional.of(OAK_LOG), "item under review");
            reviews.close(player, 1);
            helper.assertValueEqual(reviews.activeItem(player), Optional.empty(), "item under review");
        } finally {
            OboecraftMod.clock().setSource(InstantSource.system());
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void locksArentActiveInCreativeMode(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 0, 0), Blocks.STONE);
        ServerPlayer player = playerWithOakLogs(helper);
        try {
            player.setGameMode(GameType.CREATIVE);

            helper.assertTrue(placeOnTopOf(helper, player, new BlockPos(0, 0, 0)).consumesAction(),
                    "placing a locked oak log in creative mode succeeds");
            helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(0, 1, 0));
            helper.assertValueEqual(OboecraftMod.serverProgress().state(player, OAK_LOG), SrsState.Leveled.NEW,
                    "state");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void aStackNotInTheNameListIsNeverLocked(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 0, 0), Blocks.STONE);
        ServerPlayer player = playerWithOakLogs(helper);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ANDESITE));

            helper.assertTrue(placeOnTopOf(helper, player, new BlockPos(0, 0, 0)).consumesAction(),
                    "placing andesite succeeds");
            helper.assertBlockPresent(Blocks.ANDESITE, new BlockPos(0, 1, 0));
            reviews().start(player, 1, InteractionHand.MAIN_HAND);
            helper.assertValueEqual(reviews().activeItem(player), Optional.empty(), "item under review");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void aNewReviewReplacesOneThatIsGoingAndRepliesOnlyCountForTheirOwnReview(GameTestHelper helper) {
        ServerPlayer player = playerWithOakLogs(helper);
        try {
            reviews().start(player, 1, InteractionHand.MAIN_HAND);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
            reviews().start(player, 2, InteractionHand.MAIN_HAND);
            helper.assertValueEqual(reviews().activeItem(player),
                    Optional.of(new ItemId("block.minecraft.dirt")), "item under review");

            // An answer or a close for the earlier review doesn't touch the one that is going.
            reviews().answer(player, 1, "つち");
            reviews().close(player, 1);
            helper.assertValueEqual(OboecraftMod.serverProgress().state(player, new ItemId("block.minecraft.dirt")),
                    SrsState.Leveled.NEW, "dirt after an answer for the earlier review");
            helper.assertValueEqual(reviews().activeItem(player),
                    Optional.of(new ItemId("block.minecraft.dirt")), "item under review");

            reviews().answer(player, 2, "つち");
            helper.assertValueEqual(reviews().activeItem(player), Optional.empty(), "item under review");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    private static ServerReviews reviews() {
        return OboecraftMod.serverReviews();
    }
}
