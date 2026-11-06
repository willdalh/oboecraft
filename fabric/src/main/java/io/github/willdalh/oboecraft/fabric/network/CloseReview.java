package io.github.willdalh.oboecraft.fabric.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the player closed the answer menu of the review {@code reviewId}, which cancels it. */
public record CloseReview(int reviewId) implements CustomPacketPayload {
    public static final Type<CloseReview> TYPE = NetworkCodecs.type("close_review");

    public static final StreamCodec<ByteBuf, CloseReview> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(CloseReview::new, CloseReview::reviewId);

    @Override
    public Type<CloseReview> type() {
        return TYPE;
    }
}
