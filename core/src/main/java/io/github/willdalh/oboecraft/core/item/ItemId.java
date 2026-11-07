package io.github.willdalh.oboecraft.core.item;

import java.util.Objects;

/**
 * The id of an item, like {@code block.minecraft.oak_log}. It is one of the item's translation keys, and never
 * changes once released, since progress is saved under it.
 */
public record ItemId(String value) {
    /** Longer than any translation key in the game, so an id that is longer is never an item. */
    public static final int MAX_LENGTH = 256;

    public ItemId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("An item id can't be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("An item id can be at most " + MAX_LENGTH + " characters long");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
