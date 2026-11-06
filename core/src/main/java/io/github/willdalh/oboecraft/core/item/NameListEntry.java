package io.github.willdalh.oboecraft.core.item;

import java.util.List;
import java.util.Objects;

/**
 * One item in the name list.
 *
 * @param id              the item's id
 * @param translationKeys every translation key that maps to this item, at least one
 * @param japaneseName    the item's name in Minecraft's Japanese translation, the correct answer
 * @param reading         how the Japanese name is said, in kana
 */
public record NameListEntry(ItemId id, List<String> translationKeys, String japaneseName, String reading) {
    public NameListEntry {
        Objects.requireNonNull(id, "id");
        translationKeys = List.copyOf(translationKeys);
        Objects.requireNonNull(japaneseName, "japaneseName");
        Objects.requireNonNull(reading, "reading");
        if (translationKeys.isEmpty()) {
            throw new IllegalArgumentException(id + " has no translation keys");
        }
        if (translationKeys.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException(id + " has a blank translation key");
        }
        if (japaneseName.isBlank() || reading.isBlank()) {
            throw new IllegalArgumentException(id + " has a blank Japanese name or reading");
        }
    }
}
