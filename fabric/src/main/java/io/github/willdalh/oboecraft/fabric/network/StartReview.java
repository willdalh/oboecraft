package io.github.willdalh.oboecraft.fabric.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

/**
 * Client to server: start a review of the stack in {@code hand}. Only where the stack is, never which item it is,
 * which the server reads from its own state.
 */
public record StartReview(InteractionHand hand) implements CustomPacketPayload {
    public static final Type<StartReview> TYPE = NetworkCodecs.type("start_review");

    public static final StreamCodec<ByteBuf, StartReview> STREAM_CODEC =
            InteractionHand.STREAM_CODEC.map(StartReview::new, StartReview::hand);

    @Override
    public Type<StartReview> type() {
        return TYPE;
    }
}
