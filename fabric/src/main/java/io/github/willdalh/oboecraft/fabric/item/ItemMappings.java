package io.github.willdalh.oboecraft.fabric.item;

import io.github.willdalh.oboecraft.core.item.LearningLanguage;
import java.util.EnumMap;
import java.util.Map;

/** Immutable mappings loaded once for all supported learning languages, on both sides. */
public final class ItemMappings {
    private final Map<LearningLanguage, ItemMapping> mappings;

    public ItemMappings() {
        Map<LearningLanguage, ItemMapping> loaded = new EnumMap<>(LearningLanguage.class);
        for (LearningLanguage language : LearningLanguage.values()) {
            loaded.put(language, new ItemMapping(NameListFile.load(language)));
        }
        mappings = Map.copyOf(loaded);
    }

    public ItemMapping get(LearningLanguage language) { return mappings.get(language); }
}
