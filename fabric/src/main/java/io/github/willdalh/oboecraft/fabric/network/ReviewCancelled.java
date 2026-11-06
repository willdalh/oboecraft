package io.github.willdalh.oboecraft.fabric.network;

import io.netty.buffer.ByteBuf;
import java.time.Instant;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client, when the server cancelled the review {@code reviewId}, or the client answered in a review that
 * isn't going. The client closes that answer menu.
 */
public record ReviewCancelled(Instant serverTime, int reviewId) implements CustomPacketPayload {
    public static final Type<ReviewCancelled> TYPE = NetworkCodecs.type("review_cancelled");

    public static final StreamCodec<ByteBuf, ReviewCancelled> STREAM_CODEC = StreamCodec.composite(
            NetworkCodecs.INSTANT, ReviewCancelled::serverTime,
            ByteBufCodecs.VAR_INT, ReviewCancelled::reviewId,
            ReviewCancelled::new);

    @Override
    public Type<ReviewCancelled> type() {
        return TYPE;
    }
}
