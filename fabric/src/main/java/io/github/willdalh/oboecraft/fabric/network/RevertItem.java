package io.github.willdalh.oboecraft.fabric.network;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Requests a revert; the server checks the sender's current state. */
public record RevertItem(ItemId item) implements CustomPacketPayload {
    public static final Type<RevertItem> TYPE = NetworkCodecs.type("revert_item");
    public static final StreamCodec<ByteBuf, RevertItem> STREAM_CODEC =
            NetworkCodecs.ITEM_ID.map(RevertItem::new, RevertItem::item);
    @Override public Type<RevertItem> type() { return TYPE; }
}
