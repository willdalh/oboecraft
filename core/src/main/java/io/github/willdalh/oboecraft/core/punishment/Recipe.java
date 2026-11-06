package io.github.willdalh.oboecraft.core.punishment;

import io.github.willdalh.oboecraft.core.item.ItemId;
import java.util.Map;
import java.util.Objects;

/**
 * What one craft of a recipe uses, noted when a review of its result starts: how many of each ingredient that is an
 * item, and how many ingredients it uses in total. The total also counts ingredients that aren't items, like another
 * mod's, so each ingredient's part of the recipe stays the same whether or not the others can be removed.
 *
 * @param uses       how many of each item one craft uses
 * @param totalUses  how many ingredients one craft uses, items or not
 */
public record Recipe(Map<ItemId, Integer> uses, int totalUses) {
    public Recipe {
        uses = Map.copyOf(uses);
        long itemUses = 0;
        for (Map.Entry<ItemId, Integer> use : uses.entrySet()) {
            Objects.requireNonNull(use.getKey(), "item");
            if (use.getValue() < 1) {
                throw new IllegalArgumentException("An ingredient is used at least once, not " + use.getValue());
            }
            itemUses += use.getValue();
        }
        if (totalUses < itemUses) {
            throw new IllegalArgumentException(
                    "The recipe uses " + itemUses + " items, more than its total of " + totalUses);
        }
    }
}
