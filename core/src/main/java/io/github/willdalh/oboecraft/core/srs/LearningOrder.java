package io.github.willdalh.oboecraft.core.srs;

import io.github.willdalh.oboecraft.core.settings.Settings;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;

/**
 * The orders of the word list's Learning tab, which lists the items that are being learned. The player switches
 * between them, starting from {@link #URGENCY}.
 */
public enum LearningOrder {
    /**
     * Locked items first, longest locked first, with banned items among them, then unlocked items, least time left
     * first. Both are the order of the locks-at time, so the order never changes as time passes. An item with no
     * locks-at time is locked and comes first.
     */
    URGENCY(Comparator.comparing(SrsState.Leveled::locksAt,
            Comparator.comparing((Optional<Instant> locksAt) -> locksAt.orElse(Instant.MIN)))),
    /** Lowest level first, with the urgency order breaking ties. */
    LEVEL(Comparator.comparingInt(SrsState.Leveled::level).thenComparing(URGENCY.comparator));

    private final Comparator<SrsState.Leveled> comparator;

    LearningOrder(Comparator<SrsState.Leveled> comparator) {
        this.comparator = comparator;
    }

    /** Compares the states of two items on the Learning tab. */
    public Comparator<SrsState.Leveled> comparator() {
        return comparator;
    }

    /** The order the sort button switches to next. */
    public LearningOrder next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /**
     * Whether the item is being learned, so it is on the Learning tab: leveled from level 1 up to, but not including,
     * mastered. Since a fail never drops below level 1, an item joins on its first pass and stays until it is mastered,
     * marked known or ignored, or reverted.
     */
    public static boolean isLearning(SrsState state, Settings settings) {
        return state instanceof SrsState.Leveled leveled && leveled.level() >= 1 && leveled.level() < settings.topLevel();
    }
}
