package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import java.time.Duration;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

/** Changes a test player's progress directly, without a review. */
final class TestProgress {
    private TestProgress() {
    }

    /** Unlocks the items for a day, as if the player had passed them at level 1. */
    static void unlock(ServerPlayer player, ItemId... items) {
        for (ItemId item : items) {
            OboecraftMod.serverProgress().update(player, item, new SrsState.Leveled(
                    1, Optional.of(OboecraftMod.clock().instant().plus(Duration.ofDays(1)))));
        }
    }
}
