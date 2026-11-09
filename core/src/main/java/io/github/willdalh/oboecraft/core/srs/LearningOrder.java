package io.github.willdalh.oboecraft.core.srs;

import io.github.willdalh.oboecraft.core.settings.Settings;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;

/**
 * The orders of the word list's Learning tab, which lists the items that {@link SrsRules#isLearning} accepts. The player switches
 * between them, starting from {@link #URGENCY}.
 */
public enum LearningOrder {
    /**
     * Unlocked items first, least time left first, then locked items, longest locked first, with banned items among
     * them. An item with no locks-at time is locked and comes first among the locked items. Since an item moves from
     * the unlocked to the locked items when it locks, the order depends on the time.
     */
    URGENCY,
    /** Lowest level first, with the urgency order breaking ties. */
    LEVEL;

    private static final Comparator<SrsState.Leveled> BY_LOCKS_AT = Comparator.comparing(SrsState.Leveled::locksAt,
            Comparator.comparing((Optional<Instant> locksAt) -> locksAt.orElse(Instant.MIN)));

    /** Compares the states of two items on the Learning tab at {@code now}. */
    public Comparator<SrsState.Leveled> comparator(Instant now, Settings settings) {
        Comparator<SrsState.Leveled> urgency = Comparator
                .comparing((SrsState.Leveled state) -> SrsRules.isLocked(state, now, settings))
                .thenComparing(BY_LOCKS_AT);
        return switch (this) {
            case URGENCY -> urgency;
            case LEVEL -> Comparator.comparingInt(SrsState.Leveled::level).thenComparing(urgency);
        };
    }

    /** The order the sort button switches to next. */
    public LearningOrder next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
