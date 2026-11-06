package io.github.willdalh.oboecraft.core.item;

import java.util.Objects;

/**
 * The id of an item, like {@code block.minecraft.oak_log}. It is one of the item's translation keys, and never
 * changes once released, since progress is saved under it.
 */
public record ItemId(String value) {
    public ItemId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("An item id can't be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
