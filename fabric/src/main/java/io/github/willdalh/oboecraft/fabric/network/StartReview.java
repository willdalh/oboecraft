package io.github.willdalh.oboecraft.fabric.network;

import io.github.willdalh.oboecraft.fabric.lock.StackLocation;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: start a review of the stack at {@code location}. Only where the stack is, never which item it is
 * or what the trigger is, which the server works out from its own state. The client picks {@code reviewId}, and every
 * message about this review carries it, so a late reply never acts on a newer answer menu.
 */
public record StartReview(int reviewId, StackLocation location) implements CustomPacketPayload {
    public static final Type<StartReview> TYPE = NetworkCodecs.type("start_review");

    public static final StreamCodec<ByteBuf, StartReview> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StartReview::reviewId,
            StackLocation.STREAM_CODEC, StartReview::location,
            StartReview::new);

    @Override
    public Type<StartReview> type() {
        return TYPE;
    }
}
