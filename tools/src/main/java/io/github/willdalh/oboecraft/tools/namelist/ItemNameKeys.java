package io.github.willdalh.oboecraft.tools.namelist;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.function.Predicate;

/**
 * Every translation key that Minecraft names a stack with. That is each Minecraft item's default name, the names the
 * game's code gives stacks from their components, like a potion's effect, and the names the game's data gives stacks.
 */
final class ItemNameKeys {
    /**
     * A name that Minecraft's code gives an item's stacks from their components, which no report lists. Each key in the
     * Japanese language file that the rule matches is a name of the item.
     *
     * @param item      the Minecraft item
     * @param key       the key, or the start of the keys if {@code prefix} is true
     * @param prefix    whether every key that starts with {@code key} is a name
     */
    record CodeName(String item, String key, boolean prefix) {
        boolean matches(String translationKey) {
            return prefix ? translationKey.startsWith(key) : translationKey.equals(key);
        }
    }

    /**
     * The names Minecraft's code gives stacks, as of 26.3. These are the Minecraft items whose class overrides
     * {@code Item.getName(ItemStack)}: a potion, splash potion, lingering potion or tipped arrow is named after its
     * potion, a shield with a base color after the color, a compass that tracks a lodestone is a lodestone compass,
     * and a player head with a profile is named after the player. Check them for each new Minecraft version.
     */
    static final List<CodeName> CODE_NAMES = List.of(
            new CodeName("minecraft:potion", "item.minecraft.potion.effect.", true),
            new CodeName("minecraft:splash_potion", "item.minecraft.splash_potion.effect.", true),
            new CodeName("minecraft:lingering_potion", "item.minecraft.lingering_potion.effect.", true),
            new CodeName("minecraft:tipped_arrow", "item.minecraft.tipped_arrow.effect.", true),
            new CodeName("minecraft:shield", "item.minecraft.shield.", true),
            new CodeName("minecraft:compass", "item.minecraft.lodestone_compass", false),
            new CodeName("minecraft:player_head", "block.minecraft.player_head.named", false));

    private ItemNameKeys() {
    }

    /**
     * Collects the keys, each once. Each item's keys are together, in the order the items are registered: its default
     * name, then the names from code, then the names from data. Names from data for no known item come last.
     *
     * @param gameData     the game's default names and names from data
     * @param languageKeys every key in the Japanese language file, in order
     * @param codeNames    the names from code
     * @throws IllegalArgumentException if a name from code matches no key, which means the rule is out of date
     */
    static List<ItemNameKey> collect(GameData gameData, Collection<String> languageKeys, List<CodeName> codeNames) {
        for (CodeName codeName : codeNames) {
            if (languageKeys.stream().noneMatch(codeName::matches)) {
                throw new IllegalArgumentException("No key in the Japanese language file matches the name "
                        + codeName.key() + " of " + codeName.item() + ". Has Minecraft changed how it names it?");
            }
        }

        SequencedMap<String, ItemNameKey> keys = new LinkedHashMap<>();
        for (ItemNameKey defaultName : gameData.defaultNames()) {
            String item = defaultName.item();
            keys.putIfAbsent(defaultName.translationKey(), defaultName);
            for (CodeName codeName : codeNames) {
                if (codeName.item().equals(item)) {
                    addMatching(languageKeys, codeName::matches, item, keys);
                }
            }
            for (ItemNameKey dataName : gameData.dataNames()) {
                if (item.equals(dataName.item())) {
                    keys.putIfAbsent(dataName.translationKey(), dataName);
                }
            }
        }
        for (ItemNameKey dataName : gameData.dataNames()) {
            keys.putIfAbsent(dataName.translationKey(), dataName);
        }
        return List.copyOf(keys.values());
    }

    private static void addMatching(
            Collection<String> languageKeys, Predicate<String> matches, String item, Map<String, ItemNameKey> keys) {
        List<String> matching = new ArrayList<>(languageKeys.stream().filter(matches).toList());
        matching.sort(null);
        for (String key : matching) {
            keys.putIfAbsent(key, new ItemNameKey(key, item));
        }
    }
}
