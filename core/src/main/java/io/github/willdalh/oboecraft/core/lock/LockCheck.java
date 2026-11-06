package io.github.willdalh.oboecraft.core.lock;

import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.srs.SrsRules;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import java.time.Instant;

/**
 * The one check that decides what happens when a player interacts with an item, on both sides. A stack that isn't
 * an item has no SRS state and is always allowed, which the caller decides before it gets here.
 */
public final class LockCheck {
    private LockCheck() {
    }

    /**
     * Applies the lock check rules in order. The first that matches decides. Later milestones add what the player
     * tries to do as an input, once a rule depends on it.
     *
     * @param state       the item's SRS state for the player
     * @param now         the current time
     * @param locksActive false in creative and spectator mode, and on the client before the server has sent the
     *                    player's SRS states
     * @param settings    the server settings
     */
    public static LockDecision check(SrsState state, Instant now, boolean locksActive, Settings settings) {
        if (!locksActive) {
            return LockDecision.ALLOW;
        }
        return SrsRules.isLocked(state, now, settings) ? LockDecision.REVIEW : LockDecision.ALLOW;
    }
}
