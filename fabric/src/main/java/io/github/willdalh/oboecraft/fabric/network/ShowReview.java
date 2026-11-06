package io.github.willdalh.oboecraft.fabric.network;

import io.netty.buffer.ByteBuf;
import java.time.Instant;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

/**
 * Server to client, when the server stopped an action because the stack in {@code hand} is locked. The client opens
 * the answer menu for it, unless an answer menu is already open.
 */
public record ShowReview(Instant serverTime, InteractionHand hand) implements CustomPacketPayload {
    public static final Type<ShowReview> TYPE = NetworkCodecs.type("show_review");

    public static final StreamCodec<ByteBuf, ShowReview> STREAM_CODEC = StreamCodec.composite(
            NetworkCodecs.INSTANT, ShowReview::serverTime,
            InteractionHand.STREAM_CODEC, ShowReview::hand,
            ShowReview::new);

    @Override
    public Type<ShowReview> type() {
        return TYPE;
    }
}
