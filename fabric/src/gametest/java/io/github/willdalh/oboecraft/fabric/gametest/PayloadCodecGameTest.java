package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.review.ReviewOutcome;
import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.lock.StackLocation;
import io.github.willdalh.oboecraft.fabric.network.CloseReview;
import io.github.willdalh.oboecraft.fabric.network.ProgressSync;
import io.github.willdalh.oboecraft.fabric.network.ReviewAnswered;
import io.github.willdalh.oboecraft.fabric.network.ReviewCancelled;
import io.github.willdalh.oboecraft.fabric.network.ReviewRefused;
import io.github.willdalh.oboecraft.fabric.network.ReviewStarted;
import io.github.willdalh.oboecraft.fabric.network.ShowReview;
import io.github.willdalh.oboecraft.fabric.network.StartReview;
import io.github.willdalh.oboecraft.fabric.network.StateUpdate;
import io.github.willdalh.oboecraft.fabric.network.SubmitAnswer;
import io.github.willdalh.oboecraft.fabric.network.ReviewAction;
import io.github.willdalh.oboecraft.fabric.network.RevertItem;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Every payload survives being encoded and decoded. Singleplayer passes payloads without encoding them, so only a
 * server over the network would otherwise use the codecs.
 */
public class PayloadCodecGameTest {
    private static final Instant TIME = Instant.parse("2026-11-06T12:00:00.123Z");
    private static final ItemId OAK_LOG = new ItemId("block.minecraft.oak_log");

    private static <T> void assertRoundTrip(GameTestHelper helper, StreamCodec<ByteBuf, T> codec, T payload) {
        ByteBuf buffer = Unpooled.buffer();
        codec.encode(buffer, payload);
        T decoded = codec.decode(buffer);
        helper.assertValueEqual(decoded, payload, payload.getClass().getSimpleName());
        helper.assertValueEqual(buffer.readableBytes(), 0, "bytes left after " + payload.getClass().getSimpleName());
    }

    @GameTest
    public void serverToClientPayloadsRoundTrip(GameTestHelper helper) {
        Progress progress = new Progress(Map.of(
                OAK_LOG, new SrsState.Leveled(3, Optional.of(TIME)),
                new ItemId("block.minecraft.dirt"), new SrsState.Leveled(9, Optional.empty())));

        for (SrsState state : new SrsState[]{new SrsState.Known(), new SrsState.Ignored()}) {
            assertRoundTrip(helper, StateUpdate.STREAM_CODEC, new StateUpdate(TIME, OAK_LOG, state));
        }
        assertRoundTrip(helper, ReviewAnswered.STREAM_CODEC, new ReviewAnswered(TIME, 7, ReviewOutcome.LESSON,
                Optional.of(new ReviewAnswered.Answer("オークの原木", "おーくのげんぼく"))));
        assertRoundTrip(helper, ProgressSync.STREAM_CODEC, new ProgressSync(TIME, progress, Settings.DEFAULTS.withBanLength(java.time.Duration.ofSeconds(30))
                .withDropCountsAsInteraction(true)));
        assertRoundTrip(helper, StateUpdate.STREAM_CODEC,
                new StateUpdate(TIME, OAK_LOG, new SrsState.Leveled(1, Optional.of(TIME))));
        assertRoundTrip(helper, ShowReview.STREAM_CODEC, new ShowReview(TIME, new StackLocation.InventorySlot(40)));
        assertRoundTrip(helper, ShowReview.STREAM_CODEC, new ShowReview(TIME, new StackLocation.MenuSlot(3, 12)));
        assertRoundTrip(helper, ReviewStarted.STREAM_CODEC, new ReviewStarted(TIME, 7, OAK_LOG, 4, 1));
        for (ReviewRefused.Reason reason : ReviewRefused.Reason.values()) {
            assertRoundTrip(helper, ReviewRefused.STREAM_CODEC, new ReviewRefused(TIME, 7, reason));
        }
        assertRoundTrip(helper, StateUpdate.STREAM_CODEC, new StateUpdate(TIME, OAK_LOG,
                new SrsState.Leveled(2, Optional.of(TIME), true, 1, Optional.of(TIME.plusSeconds(300)))));
        assertRoundTrip(helper, ReviewAnswered.STREAM_CODEC, new ReviewAnswered(TIME, 7, ReviewOutcome.PASS,
                Optional.of(new ReviewAnswered.Answer("オークの原木", "おーくのげんぼく"))));
        assertRoundTrip(helper, ReviewAnswered.STREAM_CODEC,
                new ReviewAnswered(TIME, 7, ReviewOutcome.WRONG, Optional.empty()));
        assertRoundTrip(helper, ReviewAnswered.STREAM_CODEC, new ReviewAnswered(TIME, 7, ReviewOutcome.FAIL,
                Optional.of(new ReviewAnswered.Answer("オークの原木", "おーくのげんぼく"))));
        assertRoundTrip(helper, ReviewCancelled.STREAM_CODEC, new ReviewCancelled(TIME, 7));
        helper.succeed();
    }

    @GameTest
    public void clientToServerPayloadsRoundTrip(GameTestHelper helper) {
        assertRoundTrip(helper, StartReview.STREAM_CODEC, new StartReview(300, new StackLocation.InventorySlot(4)));
        assertRoundTrip(helper, StartReview.STREAM_CODEC, new StartReview(300, new StackLocation.MenuSlot(3, 0)));
        assertRoundTrip(helper, StartReview.STREAM_CODEC, new StartReview(300, new StackLocation.Carried(3)));
        assertRoundTrip(helper, SubmitAnswer.STREAM_CODEC, new SubmitAnswer(300, "オークの原木"));
        for (ReviewAction.Action action : ReviewAction.Action.values()) {
            assertRoundTrip(helper, ReviewAction.STREAM_CODEC, new ReviewAction(300, action));
        }
        assertRoundTrip(helper, RevertItem.STREAM_CODEC, new RevertItem(OAK_LOG));
        assertRoundTrip(helper, CloseReview.STREAM_CODEC, new CloseReview(300));
        helper.succeed();
    }

    @GameTest
    public void aKindOfStackLocationThatDoesntExistIsRefused(GameTestHelper helper) {
        ByteBuf buffer = Unpooled.buffer();
        ByteBufCodecs.VAR_INT.encode(buffer, 1);
        ByteBufCodecs.VAR_INT.encode(buffer, 3);
        ByteBufCodecs.VAR_INT.encode(buffer, 0);
        boolean refused;
        try {
            StartReview.STREAM_CODEC.decode(buffer);
            refused = false;
        } catch (RuntimeException e) {
            refused = true;
        }
        helper.assertTrue(refused, "an unknown kind of stack location is refused");
        helper.succeed();
    }

    @GameTest
    public void anAnswerLongerThanTheTypingBarIsRefused(GameTestHelper helper) {
        ByteBuf buffer = Unpooled.buffer();
        ByteBufCodecs.VAR_INT.encode(buffer, 1);
        ByteBufCodecs.STRING_UTF8.encode(buffer, "あ".repeat(SubmitAnswer.MAX_LENGTH + 1));
        boolean refused;
        try {
            SubmitAnswer.STREAM_CODEC.decode(buffer);
            refused = false;
        } catch (RuntimeException e) {
            refused = true;
        }
        helper.assertTrue(refused, "a too long answer is refused");
        helper.succeed();
    }
}
