package io.github.willdalh.oboecraft.fabric.network;

import io.github.willdalh.oboecraft.core.Oboecraft;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.netty.buffer.ByteBuf;
import java.time.Instant;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Stream codecs for the core values that payloads carry, and payload ids. */
final class NetworkCodecs {
    /** Longer than any translation key in the game. */
    private static final int MAX_ITEM_ID_LENGTH = 256;

    static final StreamCodec<ByteBuf, Instant> INSTANT =
            ByteBufCodecs.LONG.map(Instant::ofEpochMilli, Instant::toEpochMilli);

    static final StreamCodec<ByteBuf, ItemId> ITEM_ID =
            ByteBufCodecs.stringUtf8(MAX_ITEM_ID_LENGTH).map(ItemId::new, ItemId::value);

    private static final StreamCodec<ByteBuf, SrsState.Leveled> LEVELED = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SrsState.Leveled::level,
            ByteBufCodecs.optional(INSTANT), SrsState.Leveled::locksAt,
            SrsState.Leveled::new);

    /** Only leveled states exist so far. A new kind of state changes this codec and the protocol version. */
    static final StreamCodec<ByteBuf, SrsState> SRS_STATE = LEVELED.map(leveled -> leveled, state -> switch (state) {
        case SrsState.Leveled leveled -> leveled;
    });

    private NetworkCodecs() {
    }

    /** A payload type with an id in the mod's namespace. */
    static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Oboecraft.MOD_ID, path));
    }
}
