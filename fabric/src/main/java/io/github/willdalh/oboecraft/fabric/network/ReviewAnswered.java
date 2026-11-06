package io.github.willdalh.oboecraft.fabric.network;

import io.github.willdalh.oboecraft.core.review.ReviewOutcome;
import io.netty.buffer.ByteBuf;
import java.time.Instant;
import java.util.Optional;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.ByIdMap;

/**
 * Server to client, after an answer in the review {@code reviewId}. The Japanese name and reading are only sent when the client shows them, which
 * is after a pass or a lesson reveal.
 */
public record ReviewAnswered(Instant serverTime, int reviewId, ReviewOutcome outcome, Optional<Answer> answer)
        implements CustomPacketPayload {
    public static final Type<ReviewAnswered> TYPE = NetworkCodecs.type("review_answered");

    private static final IntFunction<ReviewOutcome> OUTCOME_BY_ID =
            ByIdMap.continuous(ReviewOutcome::ordinal, ReviewOutcome.values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    private static final StreamCodec<ByteBuf, ReviewOutcome> OUTCOME =
            ByteBufCodecs.idMapper(OUTCOME_BY_ID, ReviewOutcome::ordinal);

    public static final StreamCodec<ByteBuf, ReviewAnswered> STREAM_CODEC = StreamCodec.composite(
            NetworkCodecs.INSTANT, ReviewAnswered::serverTime,
            ByteBufCodecs.VAR_INT, ReviewAnswered::reviewId,
            OUTCOME, ReviewAnswered::outcome,
            ByteBufCodecs.optional(Answer.STREAM_CODEC), ReviewAnswered::answer,
            ReviewAnswered::new);

    @Override
    public Type<ReviewAnswered> type() {
        return TYPE;
    }

    /** The correct answer, shown to the player. */
    public record Answer(String japaneseName, String reading) {
        static final StreamCodec<ByteBuf, Answer> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Answer::japaneseName,
                ByteBufCodecs.STRING_UTF8, Answer::reading,
                Answer::new);
    }
}
