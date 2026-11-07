package io.github.willdalh.oboecraft.fabric.network;

import io.github.willdalh.oboecraft.core.srs.Progress;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Client to server, in answer to {@link RequestProgress}: the player's progress, which the server uses for the
 * session. It can be larger than a normal payload, so Fabric splits it into parts, up to {@link #MAX_SIZE} in all.
 */
public record UploadProgress(Progress progress) implements ProgressAnswer {
    /**
     * The most bytes the progress can take. The server sends the same progress back in {@link ProgressSync}, whose
     * largest payload is 1 MiB, so this leaves room for its time and settings, at most a few kilobytes. Real progress
     * takes a few kilobytes per hundred items.
     */
    public static final int MAX_SIZE = (1 << 20) - (16 << 10);

    public static final Type<UploadProgress> TYPE = NetworkCodecs.type("upload_progress");
    public static final StreamCodec<ByteBuf, UploadProgress> STREAM_CODEC =
            NetworkCodecs.PROGRESS.map(UploadProgress::new, UploadProgress::progress);

    @Override
    public Type<UploadProgress> type() {
        return TYPE;
    }
}
