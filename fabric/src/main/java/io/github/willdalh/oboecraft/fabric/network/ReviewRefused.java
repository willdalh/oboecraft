package io.github.willdalh.oboecraft.fabric.network;

import io.netty.buffer.ByteBuf;
import java.time.Instant;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.ByIdMap;

/**
 * Server to client, when a review can't start. The client closes the answer menu. A refusal because the item isn't
 * locked comes right after a {@link StateUpdate} with the item's state, so the client's next check allows the action.
 */
public record ReviewRefused(Instant serverTime, Reason reason) implements CustomPacketPayload {
    public static final Type<ReviewRefused> TYPE = NetworkCodecs.type("review_refused");

    public static final StreamCodec<ByteBuf, ReviewRefused> STREAM_CODEC = StreamCodec.composite(
            NetworkCodecs.INSTANT, ReviewRefused::serverTime,
            Reason.STREAM_CODEC, ReviewRefused::reason,
            ReviewRefused::new);

    @Override
    public Type<ReviewRefused> type() {
        return TYPE;
    }

    /** Why the review can't start. */
    public enum Reason {
        /** The item isn't locked. */
        NOT_LOCKED,
        /** The stack isn't an item, or isn't there any more. */
        STACK_GONE;

        private static final IntFunction<Reason> BY_ID =
                ByIdMap.continuous(Reason::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        static final StreamCodec<ByteBuf, Reason> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Reason::ordinal);
    }
}
