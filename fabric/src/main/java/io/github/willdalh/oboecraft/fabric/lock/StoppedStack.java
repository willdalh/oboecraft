package io.github.willdalh.oboecraft.fabric.lock;

import io.github.willdalh.oboecraft.core.item.NameListEntry;
import java.util.Objects;

/**
 * A stack whose action the lock check stopped. So far the lock check only stops an action to review the item.
 *
 * @param checked the stack and what the action does with it
 * @param entry   the stack's item
 */
public record StoppedStack(CheckedStack checked, NameListEntry entry) {
    public StoppedStack {
        Objects.requireNonNull(checked, "checked");
        Objects.requireNonNull(entry, "entry");
    }
}
