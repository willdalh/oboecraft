package io.github.willdalh.oboecraft.core.item;

import java.util.Objects;
import java.util.Optional;

/**
 * Which items an anvil may rename: only the name tag. Renaming any other item, or taking its custom name away, would
 * let a player give a stack a name of their own, so the anvil leaves the names of every other item as they are. A
 * fixed rule, not a setting.
 */
public final class Renaming {
    /** The name tag, the one item an anvil renames. */
    public static final ItemId NAME_TAG = new ItemId("item.minecraft.name_tag");

    private Renaming() {
    }

    /**
     * Whether an anvil may rename a stack of {@code item}. A stack that isn't an item in the name list, like another
     * mod's item, is {@link Optional#empty()} and can't be renamed either.
     */
    public static boolean mayRename(Optional<ItemId> item) {
        return Objects.requireNonNull(item, "item").filter(NAME_TAG::equals).isPresent();
    }
}
