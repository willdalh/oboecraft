package io.github.willdalh.oboecraft.fabric.lock;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import java.time.Instant;
import net.minecraft.world.entity.player.Player;

/** One side's view of the players' SRS states, which the lock check reads. */
public interface ProgressView {
    /**
     * Whether this side has the player's SRS states, so locks can be active. On the client, false until the server
     * has sent them for this connection.
     */
    boolean hasProgress(Player player);

    /** The player's SRS state for the item. */
    SrsState state(Player player, ItemId item);

    /** The current time: the clock on the server, and the client's estimate of the server's time on the client. */
    Instant now();
}
