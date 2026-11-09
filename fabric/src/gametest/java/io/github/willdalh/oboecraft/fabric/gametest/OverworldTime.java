package io.github.willdalh.oboecraft.fabric.gametest;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.clock.WorldClocks;

/** Sets the Overworld's time of day for a game test, which puts it back as it was. */
final class OverworldTime {
    private OverworldTime() {
    }

    /** Sets the Overworld's clock to {@code dayTime} ticks, and returns what it was. */
    static long set(MinecraftServer server, long dayTime) {
        long before = server.overworld().getOverworldClockTime();
        server.clockManager().setTotalTicks(
                server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD),
                dayTime);
        return before;
    }
}
