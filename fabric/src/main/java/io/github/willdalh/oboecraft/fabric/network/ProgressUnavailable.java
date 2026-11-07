package io.github.willdalh.oboecraft.fabric.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Client to server, in answer to {@link RequestProgress}, when the client can't send the player's progress. The
 * server disconnects the player with a message that says why.
 */
public record ProgressUnavailable(Reason reason) implements ProgressAnswer {
    public static final Type<ProgressUnavailable> TYPE = NetworkCodecs.type("progress_unavailable");

    public static final StreamCodec<ByteBuf, ProgressUnavailable> STREAM_CODEC =
            Reason.STREAM_CODEC.map(ProgressUnavailable::new, ProgressUnavailable::reason);

    @Override
    public Type<ProgressUnavailable> type() {
        return TYPE;
    }

    /** Why the client can't send the player's progress. */
    public enum Reason {
        /** The progress file was saved by a newer version of the mod. */
        TOO_NEW,
        /** The progress file can't be read, or a broken file can't be moved out of the way. */
        UNREADABLE,
        /** Another game running on the same computer uses the progress file. */
        IN_USE,
        /** The progress has more states, or takes more bytes, than {@link UploadProgress} can carry. */
        TOO_LARGE;

        /** A reason that doesn't exist fails to decode, which disconnects the sender. */
        static final StreamCodec<ByteBuf, Reason> STREAM_CODEC =
                ByteBufCodecs.VAR_INT.map(Reason::byId, Reason::ordinal);

        private static Reason byId(int id) {
            Reason[] reasons = values();
            if (id < 0 || id >= reasons.length) {
                throw new IllegalArgumentException("Unknown reason " + id);
            }
            return reasons[id];
        }
    }
}
