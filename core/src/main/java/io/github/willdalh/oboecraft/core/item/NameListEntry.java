package io.github.willdalh.oboecraft.core.item;

import java.util.List;
import java.util.Objects;

/**
 * One item in the name list.
 *
 * @param id              the item's id
 * @param translationKeys every translation key that maps to this item, at least one
 * @param japaneseName    the item's name in Minecraft's Japanese translation, with its reading, the correct answer
 */
public record NameListEntry(ItemId id, List<String> translationKeys, JapaneseName japaneseName) {
    public NameListEntry {
        Objects.requireNonNull(id, "id");
        translationKeys = List.copyOf(translationKeys);
        Objects.requireNonNull(japaneseName, "japaneseName");
        if (translationKeys.isEmpty()) {
            throw new IllegalArgumentException(id + " has no translation keys");
        }
        if (translationKeys.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException(id + " has a blank translation key");
        }
    }
}
