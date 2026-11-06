package io.github.willdalh.oboecraft.core.srs;

import io.github.willdalh.oboecraft.core.item.ItemId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * All SRS states of one player. An item with no saved state is {@link SrsState.Leveled#NEW}, so only items the
 * player has interacted with are stored. States of item ids that aren't in the current name list are kept too, so
 * they survive a name list change.
 */
public record Progress(Map<ItemId, SrsState> states) {
    public static final Progress EMPTY = new Progress(Map.of());

    /** Leaves out states that are {@link SrsState.Leveled#NEW}, since they are the same as no state. */
    public Progress {
        Map<ItemId, SrsState> saved = new HashMap<>(states);
        saved.values().removeIf(SrsState.Leveled.NEW::equals);
        states = Map.copyOf(saved);
    }

    /** The item's state, or {@link SrsState.Leveled#NEW} if it has none. */
    public SrsState state(ItemId item) {
        return states.getOrDefault(Objects.requireNonNull(item, "item"), SrsState.Leveled.NEW);
    }

    /** This progress with the item's state replaced. */
    public Progress with(ItemId item, SrsState state) {
        Map<ItemId, SrsState> changed = new HashMap<>(states);
        changed.put(Objects.requireNonNull(item, "item"), Objects.requireNonNull(state, "state"));
        return new Progress(changed);
    }
}
