package io.github.willdalh.oboecraft.fabric.network;

import io.netty.buffer.ByteBuf;
import java.time.Instant;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client, when the server cancelled the player's review, or the client acts in a review that isn't going. */
public record ReviewCancelled(Instant serverTime) implements CustomPacketPayload {
    public static final Type<ReviewCancelled> TYPE = NetworkCodecs.type("review_cancelled");

    public static final StreamCodec<ByteBuf, ReviewCancelled> STREAM_CODEC =
            NetworkCodecs.INSTANT.map(ReviewCancelled::new, ReviewCancelled::serverTime);

    @Override
    public Type<ReviewCancelled> type() {
        return TYPE;
    }
}
