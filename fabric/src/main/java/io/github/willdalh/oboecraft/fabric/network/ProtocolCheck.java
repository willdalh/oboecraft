package io.github.willdalh.oboecraft.fabric.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A configuration payload whose id holds the mod's network protocol version. It is never sent: a client with the
 * mod registers a receiver for it, so the server knows the client runs the same protocol when it can send it.
 */
public record ProtocolCheck() implements CustomPacketPayload {
    /** Goes up when a payload or the meaning of one changes, so old and new versions never talk. */
    public static final int PROTOCOL_VERSION = 6;

    public static final ProtocolCheck INSTANCE = new ProtocolCheck();
    public static final Type<ProtocolCheck> TYPE = NetworkCodecs.type("protocol/" + PROTOCOL_VERSION);
    public static final StreamCodec<ByteBuf, ProtocolCheck> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<ProtocolCheck> type() {
        return TYPE;
    }
}
