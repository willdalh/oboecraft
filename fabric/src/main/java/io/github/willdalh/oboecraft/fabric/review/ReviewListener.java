package io.github.willdalh.oboecraft.fabric.review;

import net.minecraft.server.level.ServerPlayer;

/** Hears when a player's review starts and ends, in any way: a pass, a fail, Known, Ignore or a cancel. */
public interface ReviewListener {
    /** The player's review started, replacing any review that was going. */
    void reviewStarted(ServerPlayer player);

    /** The player's review ended, and no other review is going. */
    void reviewEnded(ServerPlayer player);
}
