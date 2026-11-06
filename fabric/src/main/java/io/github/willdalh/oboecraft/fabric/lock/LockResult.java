package io.github.willdalh.oboecraft.fabric.lock;

import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.lock.LockDecision;
import java.util.Objects;
import java.util.Optional;

/**
 * What the lock check decided for a stack.
 *
 * @param entry    the stack's item, or empty if the stack isn't an item
 * @param decision what happens to the action
 */
public record LockResult(Optional<NameListEntry> entry, LockDecision decision) {
    public LockResult {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(decision, "decision");
    }
}
