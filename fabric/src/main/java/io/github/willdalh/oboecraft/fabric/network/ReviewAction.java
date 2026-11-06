package io.github.willdalh.oboecraft.fabric.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** An action in exactly one active review. Unknown actions are rejected when decoded. */
public record ReviewAction(int reviewId, Action action) implements CustomPacketPayload {
    public enum Action { SHOW_ANSWER, DONT_KNOW, KNOWN, IGNORE }
    public static final Type<ReviewAction> TYPE = NetworkCodecs.type("review_action");
    private static final StreamCodec<ByteBuf, Action> ACTION = ByteBufCodecs.VAR_INT.map(id -> {
        if (id < 0 || id >= Action.values().length) throw new IllegalArgumentException("Unknown review action");
        return Action.values()[id];
    }, Action::ordinal);
    public static final StreamCodec<ByteBuf, ReviewAction> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ReviewAction::reviewId, ACTION, ReviewAction::action, ReviewAction::new);
    @Override public Type<ReviewAction> type() { return TYPE; }
}
