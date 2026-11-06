package io.github.willdalh.oboecraft.fabric.network;

import io.github.willdalh.oboecraft.fabric.lock.StackLocation;
import io.netty.buffer.ByteBuf;
import java.time.Instant;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client, when the server stopped an action because the stack at {@code location} is locked. The client
 * opens the answer menu for it, unless an answer menu or a screen other than the open menu's is already open.
 */
public record ShowReview(Instant serverTime, StackLocation location) implements CustomPacketPayload {
    public static final Type<ShowReview> TYPE = NetworkCodecs.type("show_review");

    public static final StreamCodec<ByteBuf, ShowReview> STREAM_CODEC = StreamCodec.composite(
            NetworkCodecs.INSTANT, ShowReview::serverTime,
            StackLocation.STREAM_CODEC, ShowReview::location,
            ShowReview::new);

    @Override
    public Type<ShowReview> type() {
        return TYPE;
    }
}
