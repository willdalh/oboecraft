package io.github.willdalh.oboecraft.fabric.network;

import io.github.willdalh.oboecraft.core.srs.Progress;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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

    /**
     * Whether the progress can be sent: it has at most {@link NetworkCodecs#MAX_STATES} states and takes at most
     * {@link #MAX_SIZE} bytes. Only a file edited by hand, or progress a changed server sent, can be larger.
     */
    public static boolean fits(Progress progress) {
        if (progress.states().size() > NetworkCodecs.MAX_STATES) {
            return false;
        }
        ByteBuf buffer = Unpooled.buffer();
        try {
            STREAM_CODEC.encode(buffer, new UploadProgress(progress));
            return buffer.readableBytes() <= MAX_SIZE;
        } finally {
            buffer.release();
        }
    }

    @Override
    public Type<UploadProgress> type() {
        return TYPE;
    }
}
