package io.github.willdalh.oboecraft.fabric.network;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.netty.buffer.ByteBuf;
import java.time.Instant;
import java.util.HashMap;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client, when the player joins: all of the player's saved SRS states. */
public record ProgressSync(Instant serverTime, Progress progress) implements CustomPacketPayload {
    public static final Type<ProgressSync> TYPE = NetworkCodecs.type("progress_sync");

    /** Far more than the name list has, which leaves room for saved items that left the name list. */
    private static final int MAX_STATES = 65_536;

    private static final StreamCodec<ByteBuf, Progress> PROGRESS = ByteBufCodecs.map(
                    HashMap<ItemId, SrsState>::new, NetworkCodecs.ITEM_ID, NetworkCodecs.SRS_STATE, MAX_STATES)
            .map(Progress::new, progress -> new HashMap<>(progress.states()));

    public static final StreamCodec<ByteBuf, ProgressSync> STREAM_CODEC = StreamCodec.composite(
            NetworkCodecs.INSTANT, ProgressSync::serverTime,
            PROGRESS, ProgressSync::progress,
            ProgressSync::new);

    @Override
    public Type<ProgressSync> type() {
        return TYPE;
    }
}
