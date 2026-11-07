package io.github.willdalh.oboecraft.fabric.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client, while the player is configured: asks for the player's progress, which their client keeps. The
 * client answers with {@link UploadProgress} or {@link ProgressUnavailable}, and the player only enters the world
 * after that.
 */
public record RequestProgress() implements CustomPacketPayload {
    public static final RequestProgress INSTANCE = new RequestProgress();
    public static final Type<RequestProgress> TYPE = NetworkCodecs.type("request_progress");
    public static final StreamCodec<ByteBuf, RequestProgress> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<RequestProgress> type() {
        return TYPE;
    }
}
