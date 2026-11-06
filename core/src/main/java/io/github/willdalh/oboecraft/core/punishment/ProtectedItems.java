package io.github.willdalh.oboecraft.core.punishment;

import io.github.willdalh.oboecraft.core.item.ItemId;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * The items that a punishment never removes: the dragon egg, shulker boxes and bundles, which can hold many other
 * items. They lock like other items, and failing still demotes and bans them. A fixed rule, not a setting.
 */
public final class ProtectedItems {
    private static final String[] COLORS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};

    /** Every protected item id, built once. */
    public static final Set<ItemId> IDS = build();

    private ProtectedItems() {
    }

    /** Whether a punishment never removes the item. */
    public static boolean isProtected(ItemId item) {
        return IDS.contains(Objects.requireNonNull(item, "item"));
    }

    private static Set<ItemId> build() {
        Set<ItemId> ids = new HashSet<>();
        ids.add(new ItemId("block.minecraft.dragon_egg"));
        ids.add(new ItemId("block.minecraft.shulker_box"));
        ids.add(new ItemId("item.minecraft.bundle"));
        for (String color : COLORS) {
            ids.add(new ItemId("block.minecraft." + color + "_shulker_box"));
            ids.add(new ItemId("item.minecraft." + color + "_bundle"));
        }
        return Set.copyOf(ids);
    }
}
