package io.github.willdalh.oboecraft.fabric.network;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.netty.buffer.ByteBuf;
import java.time.Instant;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client, when one of the player's SRS states changes, or the client needs it up to date. */
public record StateUpdate(Instant serverTime, ItemId item, SrsState state) implements CustomPacketPayload {
    public static final Type<StateUpdate> TYPE = NetworkCodecs.type("state_update");

    public static final StreamCodec<ByteBuf, StateUpdate> STREAM_CODEC = StreamCodec.composite(
            NetworkCodecs.INSTANT, StateUpdate::serverTime,
            NetworkCodecs.ITEM_ID, StateUpdate::item,
            NetworkCodecs.SRS_STATE, StateUpdate::state,
            StateUpdate::new);

    @Override
    public Type<StateUpdate> type() {
        return TYPE;
    }
}
