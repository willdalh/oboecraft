package io.github.willdalh.oboecraft.fabric.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the player closed the answer menu, which cancels the review. */
public record CloseReview() implements CustomPacketPayload {
    public static final CloseReview INSTANCE = new CloseReview();
    public static final Type<CloseReview> TYPE = NetworkCodecs.type("close_review");
    public static final StreamCodec<ByteBuf, CloseReview> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<CloseReview> type() {
        return TYPE;
    }
}
