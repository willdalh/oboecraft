package io.github.willdalh.oboecraft.fabric.lock;

import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.lock.LockDecision;
import java.util.Objects;

/**
 * A stack whose action the lock check stopped.
 *
 * @param checked  the stack and what the action does with it
 * @param entry    the stack's item
 * @param decision what the lock check decided, never {@link LockDecision#ALLOW}
 */
public record StoppedStack(CheckedStack checked, NameListEntry entry, LockDecision decision) {
    public StoppedStack {
        Objects.requireNonNull(checked, "checked");
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(decision, "decision");
        if (decision == LockDecision.ALLOW) {
            throw new IllegalArgumentException("An allowed stack isn't stopped");
        }
    }
}
