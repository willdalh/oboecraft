package io.github.willdalh.oboecraft.fabric.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the typed answer in the review {@code reviewId}. */
public record SubmitAnswer(int reviewId, String answer) implements CustomPacketPayload {
    /** The longest answer the answer menu's typing bar takes. Longer answers are refused when decoded. */
    public static final int MAX_LENGTH = 64;

    public static final Type<SubmitAnswer> TYPE = NetworkCodecs.type("submit_answer");

    public static final StreamCodec<ByteBuf, SubmitAnswer> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SubmitAnswer::reviewId,
            ByteBufCodecs.stringUtf8(MAX_LENGTH), SubmitAnswer::answer,
            SubmitAnswer::new);

    @Override
    public Type<SubmitAnswer> type() {
        return TYPE;
    }
}
