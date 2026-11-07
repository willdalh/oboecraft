package io.github.willdalh.oboecraft.fabric.network;

import io.github.willdalh.oboecraft.core.srs.Progress;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server, in answer to {@link RequestProgress}: the player's progress, which the server uses for the
 * session. It can be larger than a normal payload, so Fabric splits it into parts, up to {@link #MAX_SIZE} in all.
 */
public record UploadProgress(Progress progress) implements CustomPacketPayload {
    /**
     * The most bytes the progress can take, the same as the largest payload the server can send back. Real progress
     * takes a few kilobytes per hundred items.
     */
    public static final int MAX_SIZE = 1 << 20;

    public static final Type<UploadProgress> TYPE = NetworkCodecs.type("upload_progress");
    public static final StreamCodec<ByteBuf, UploadProgress> STREAM_CODEC =
            NetworkCodecs.PROGRESS.map(UploadProgress::new, UploadProgress::progress);

    @Override
    public Type<UploadProgress> type() {
        return TYPE;
    }
}
