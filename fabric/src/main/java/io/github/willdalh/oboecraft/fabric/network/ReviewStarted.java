package io.github.willdalh.oboecraft.fabric.network;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.netty.buffer.ByteBuf;
import java.time.Instant;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client, when a review starts, or the client asks to start one while it is going. */
public record ReviewStarted(Instant serverTime, ItemId item, int level) implements CustomPacketPayload {
    public static final Type<ReviewStarted> TYPE = NetworkCodecs.type("review_started");

    public static final StreamCodec<ByteBuf, ReviewStarted> STREAM_CODEC = StreamCodec.composite(
            NetworkCodecs.INSTANT, ReviewStarted::serverTime,
            NetworkCodecs.ITEM_ID, ReviewStarted::item,
            ByteBufCodecs.VAR_INT, ReviewStarted::level,
            ReviewStarted::new);

    @Override
    public Type<ReviewStarted> type() {
        return TYPE;
    }
}
