package io.github.willdalh.oboecraft.core.punishment;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.settings.Settings;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * How much a fail removes from the current view. The removal rate depends on the level the item had before the fail
 * demoted it, and every count is rounded up. Protected items are never removed.
 */
public final class Punishment {
    /** The removal rate at level 1, in percent. A fixed rule, not a setting. */
    public static final int BASE_REMOVAL_PERCENT = 50;

    private Punishment() {
    }

    /**
     * The removal rate of a fail at {@code level}, in percent: 50% at level 1, and {@code percentageFactor} points
     * less for each level above it, but never below 0%.
     */
    public static int removalPercent(int level, Settings settings) {
        if (level < 1) {
            throw new IllegalArgumentException("Only a fail from level 1 up is punished, not at level " + level);
        }
        long percent = BASE_REMOVAL_PERCENT - (long) (level - 1) * settings.percentageFactor();
        return (int) Math.max(0, percent);
    }

    /**
     * How many of the reviewed item a fail removes, from the {@code inView} it has in the current view. None of a
     * protected item.
     */
    public static int removedCount(ItemId item, int inView, int level, Settings settings) {
        if (inView < 0) {
            throw new IllegalArgumentException("A count can't be negative, not " + inView);
        }
        if (ProtectedItems.isProtected(item)) {
            return 0;
        }
        return share(inView, 1, 1, removalPercent(level, settings));
    }

    /**
     * How many of each ingredient a fail on a crafted item removes, from what the current view has of it. Each
     * ingredient loses its part of the recipe times the removal rate, so a recipe of 3 diamonds and 2 sticks at 50%
     * removes 3/5 of 50% of the diamonds and 2/5 of 50% of the sticks. Protected ingredients and ingredients the
     * current view doesn't have are left out.
     *
     * @param recipe the recipe, as it was when the review started
     * @param inView how many of each item the current view has
     * @return how many of each ingredient to remove, only with counts above 0
     */
    public static Map<ItemId, Integer> removedIngredients(
            Recipe recipe, Map<ItemId, Integer> inView, int level, Settings settings) {
        Objects.requireNonNull(inView, "inView");
        int percent = removalPercent(level, settings);
        Map<ItemId, Integer> removed = new HashMap<>();
        recipe.uses().forEach((item, uses) -> {
            int count = inView.getOrDefault(item, 0);
            if (count < 0) {
                throw new IllegalArgumentException("A count can't be negative, not " + count);
            }
            int share = ProtectedItems.isProtected(item) ? 0 : share(count, uses, recipe.totalUses(), percent);
            if (share > 0) {
                removed.put(item, share);
            }
        });
        return Map.copyOf(removed);
    }

    /**
     * {@code count × part / whole × percent / 100}, rounded up, in exact integer arithmetic so that a share like 15
     * of 50 is never rounded up to 16. It is never more than {@code count}, since the part is at most the whole and
     * the rate at most 50%.
     */
    private static int share(int count, int part, int whole, int percent) {
        long numerator = (long) count * part * percent;
        long denominator = (long) whole * 100;
        return (int) ((numerator + denominator - 1) / denominator);
    }
}
