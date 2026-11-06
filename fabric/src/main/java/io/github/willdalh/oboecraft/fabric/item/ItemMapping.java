package io.github.willdalh.oboecraft.fabric.item;

import io.github.willdalh.oboecraft.core.item.NameList;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;

/** Maps a stack to its item in the name list, through the translation key of its item name. */
public final class ItemMapping {
    private final NameList nameList;

    public ItemMapping(NameList nameList) {
        this.nameList = Objects.requireNonNull(nameList, "nameList");
    }

    public NameList nameList() {
        return nameList;
    }

    /** The stack's name list entry, or empty if the stack isn't an item. */
    public Optional<NameListEntry> entryOf(ItemStack stack) {
        return translationKeyOf(stack).flatMap(nameList::byTranslationKey);
    }

    /**
     * The translation key of the stack's item name. The item name ignores custom names from anvils and name tags, so
     * renaming a stack never changes its key. Empty if the item name isn't a translatable text.
     */
    public static Optional<String> translationKeyOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return stack.getItemName().getContents() instanceof TranslatableContents translatable
                ? Optional.of(translatable.getKey())
                : Optional.empty();
    }
}
