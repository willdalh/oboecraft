package io.github.willdalh.oboecraft.core.lock;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.srs.SrsRules;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** The one check that decides what happens when a player interacts with a stack, on both sides. */
public final class LockCheck {
    private LockCheck() {
    }

    /**
     * Applies the lock check rules in order. The first that matches decides.
     *
     * @param item        the stack's item, or empty if the stack isn't in the name list
     * @param interaction what the player tries to do
     * @param state       the item's SRS state for the player. Ignored when {@code item} is empty.
     * @param now         the current time
     * @param locksActive false in creative and spectator mode, and on the client before the server has sent the
     *                    player's SRS states
     * @param settings    the server settings
     */
    public static LockDecision check(
            Optional<ItemId> item,
            Interaction interaction,
            SrsState state,
            Instant now,
            boolean locksActive,
            Settings settings) {
        Objects.requireNonNull(interaction, "interaction");
        if (item.isEmpty() || !locksActive) {
            return LockDecision.ALLOW;
        }
        return SrsRules.isLocked(state, now, settings) ? LockDecision.REVIEW : LockDecision.ALLOW;
    }
}
