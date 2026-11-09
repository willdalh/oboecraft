package io.github.willdalh.oboecraft.core.tsukumogami;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.srs.SrsRules;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * The overdue items a player carries, which call the tsukumogami. Their number is the overdue count. An item is
 * overdue when it is locked at level 1 or higher, banned or not: a fail never makes the tsukumogami weaker. Level 0
 * items were never learned, so they aren't overdue.
 */
public final class OverdueItems {
    private OverdueItems() {
    }

    /** Whether an item in this state is overdue at {@code now}. */
    public static boolean isOverdue(SrsState state, Instant now, Settings settings) {
        return state instanceof SrsState.Leveled leveled && leveled.level() >= 1
                && SrsRules.isLocked(state, now, settings);
    }

    /**
     * The overdue items among the items the player carries, each once, in the order they are first carried. Stack size
     * doesn't matter, so the caller passes each stack's item and this counts every item once.
     *
     * @param carried the items of the stacks the player carries, in a stable order, with repeats
     * @param states  the player's SRS state of an item
     */
    public static List<ItemId> of(
            List<ItemId> carried, Function<ItemId, SrsState> states, Instant now, Settings settings) {
        Objects.requireNonNull(states, "states");
        Set<ItemId> overdue = new LinkedHashSet<>();
        for (ItemId item : carried) {
            if (!overdue.contains(item) && isOverdue(states.apply(item), now, settings)) {
                overdue.add(item);
            }
        }
        return List.copyOf(overdue);
    }
}
