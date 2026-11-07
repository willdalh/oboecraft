package io.github.willdalh.oboecraft.fabric.network;

import io.github.willdalh.oboecraft.core.Oboecraft;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.settings.LevelSettings;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.netty.buffer.ByteBuf;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
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
            ByteBufCodecs.BOOL, SrsState.Leveled::demoted,
            ByteBufCodecs.VAR_INT, SrsState.Leveled::wrongAttempts,
            ByteBufCodecs.optional(INSTANT), SrsState.Leveled::bannedUntil,
            SrsState.Leveled::new);

    /** State tags are checked before any state is constructed. */
    static final StreamCodec<ByteBuf, SrsState> SRS_STATE = ByteBufCodecs.VAR_INT.<SrsState>dispatch(
            state -> switch (state) {
                case SrsState.Leveled leveled -> 0;
                case SrsState.Known known -> 1;
                case SrsState.Ignored ignored -> 2;
            },
            kind -> switch (kind) {
                case 0 -> LEVELED;
                case 1 -> StreamCodec.unit(new SrsState.Known());
                case 2 -> StreamCodec.unit(new SrsState.Ignored());
                default -> throw new IllegalArgumentException("Unknown state kind " + kind);
            });

    private static final StreamCodec<ByteBuf, Duration> DURATION =
            ByteBufCodecs.VAR_LONG.map(Duration::ofMillis, Duration::toMillis);

    private static final StreamCodec<ByteBuf, LevelSettings> LEVEL_SETTINGS = StreamCodec.composite(
            DURATION, LevelSettings::interval,
            ByteBufCodecs.VAR_INT, LevelSettings::demotion,
            LevelSettings::new);

    /**
     * The server settings. The list is read with at most {@link Settings#MAX_LEVELS} levels, and the records check
     * every value, so invalid settings fail to decode and disconnect the sender.
     */
    static final StreamCodec<ByteBuf, Settings> SETTINGS = StreamCodec.composite(
            LEVEL_SETTINGS.apply(ByteBufCodecs.list(Settings.MAX_LEVELS)), Settings::levels,
            DURATION, Settings::banLength,
            ByteBufCodecs.VAR_INT, Settings::percentageFactor,
            ByteBufCodecs.BOOL, Settings::dropCountsAsInteraction,
            ByteBufCodecs.BOOL, Settings::useOwnProgress,
            Settings::new);

    /** Far more than the name list has, which leaves room for saved items that left the name list. */
    static final int MAX_STATES = 65_536;

    /** A player's SRS states. At most {@link #MAX_STATES} are read, and each state is checked by its record. */
    static final StreamCodec<ByteBuf, Progress> PROGRESS = ByteBufCodecs.map(
                    HashMap<ItemId, SrsState>::new, ITEM_ID, SRS_STATE, MAX_STATES)
            .map(Progress::new, progress -> new HashMap<>(progress.states()));

    private NetworkCodecs() {
    }

    /** A payload type with an id in the mod's namespace. */
    static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Oboecraft.MOD_ID, path));
    }
}
