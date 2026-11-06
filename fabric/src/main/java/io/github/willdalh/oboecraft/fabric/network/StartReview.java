package io.github.willdalh.oboecraft.fabric.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

/**
 * Client to server: start a review of the stack in {@code hand}. Only where the stack is, never which item it is,
 * which the server reads from its own state. The client picks {@code reviewId}, and every message about this review
 * carries it, so a late reply never acts on a newer answer menu.
 */
public record StartReview(int reviewId, InteractionHand hand) implements CustomPacketPayload {
    public static final Type<StartReview> TYPE = NetworkCodecs.type("start_review");

    public static final StreamCodec<ByteBuf, StartReview> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StartReview::reviewId,
            InteractionHand.STREAM_CODEC, StartReview::hand,
            StartReview::new);

    @Override
    public Type<StartReview> type() {
        return TYPE;
    }
}
