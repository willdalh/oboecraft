package io.github.willdalh.oboecraft.fabric.network;

import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.srs.Progress;
import io.netty.buffer.ByteBuf;
import java.time.Instant;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client, when the player joins and when the server's settings change: all of the player's saved SRS states,
 * and the server settings that the client's lock check and displays use.
 */
public record ProgressSync(Instant serverTime, Progress progress, Settings settings) implements CustomPacketPayload {
    public static final Type<ProgressSync> TYPE = NetworkCodecs.type("progress_sync");

    public static final StreamCodec<ByteBuf, ProgressSync> STREAM_CODEC = StreamCodec.composite(
            NetworkCodecs.INSTANT, ProgressSync::serverTime,
            NetworkCodecs.PROGRESS, ProgressSync::progress,
            NetworkCodecs.SETTINGS, ProgressSync::settings,
            ProgressSync::new);

    @Override
    public Type<ProgressSync> type() {
        return TYPE;
    }
}
