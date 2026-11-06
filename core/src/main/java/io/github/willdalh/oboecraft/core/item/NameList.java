package io.github.willdalh.oboecraft.core.item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** All items, with lookups by translation key and by item id. */
public final class NameList {
    private final List<NameListEntry> entries;
    private final Map<String, NameListEntry> entriesByTranslationKey;
    private final Map<ItemId, NameListEntry> entriesById;

    /**
     * @throws IllegalArgumentException if two entries share an item id or a translation key
     */
    public NameList(List<NameListEntry> entries) {
        Map<String, NameListEntry> byKey = new HashMap<>();
        Map<ItemId, NameListEntry> byId = new HashMap<>();
        for (NameListEntry entry : entries) {
            if (byId.putIfAbsent(entry.id(), entry) != null) {
                throw new IllegalArgumentException("Two entries have the item id " + entry.id());
            }
            for (String key : entry.translationKeys()) {
                NameListEntry other = byKey.putIfAbsent(key, entry);
                if (other != null) {
                    throw new IllegalArgumentException(
                            "The translation key " + key + " belongs to both " + other.id() + " and " + entry.id());
                }
            }
        }
        this.entries = List.copyOf(entries);
        this.entriesByTranslationKey = Map.copyOf(byKey);
        this.entriesById = Map.copyOf(byId);
    }

    /** The entry that has this translation key, if any. */
    public Optional<NameListEntry> byTranslationKey(String translationKey) {
        return Optional.ofNullable(entriesByTranslationKey.get(translationKey));
    }

    /** The entry with this item id, if any. */
    public Optional<NameListEntry> byId(ItemId id) {
        return Optional.ofNullable(entriesById.get(id));
    }

    /** Every entry, in the order it was given. */
    public List<NameListEntry> entries() {
        return entries;
    }

    public int size() {
        return entriesById.size();
    }
}
