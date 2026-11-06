package io.github.willdalh.oboecraft.fabric.network;

import io.netty.buffer.ByteBuf;
import java.time.Instant;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.ByIdMap;

/**
 * Server to client, when the review the client asked for with {@code reviewId} can't start. The client closes that
 * answer menu. A refusal because the item isn't locked or is banned comes right after a {@link StateUpdate} with the
 * item's state, so the client's next check agrees with the server.
 */
public record ReviewRefused(Instant serverTime, int reviewId, Reason reason) implements CustomPacketPayload {
    public static final Type<ReviewRefused> TYPE = NetworkCodecs.type("review_refused");

    public static final StreamCodec<ByteBuf, ReviewRefused> STREAM_CODEC = StreamCodec.composite(
            NetworkCodecs.INSTANT, ReviewRefused::serverTime,
            ByteBufCodecs.VAR_INT, ReviewRefused::reviewId,
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
        /** The item is banned after a fail. The client shows how long the ban has left. */
        BANNED,
        /** The stack isn't an item, isn't there any more, or isn't somewhere the player can reach. */
        STACK_GONE,
        /** The player's progress isn't loaded, for example while they are being disconnected. */
        NO_PROGRESS;

        private static final IntFunction<Reason> BY_ID =
                ByIdMap.continuous(Reason::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        static final StreamCodec<ByteBuf, Reason> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Reason::ordinal);
    }
}
