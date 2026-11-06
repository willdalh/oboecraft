package io.github.willdalh.oboecraft.tools.namelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ItemNameKeysTest {
    private static final List<ItemNameKeys.CodeName> CODE_NAMES = List.of(
            new ItemNameKeys.CodeName("minecraft:potion", "item.minecraft.potion.effect.", true, true),
            new ItemNameKeys.CodeName("minecraft:compass", "item.minecraft.lodestone_compass", false, false));

    private static final List<String> LANGUAGE_KEYS = List.of(
            "item.minecraft.potion", "item.minecraft.potion.effect.water", "item.minecraft.potion.effect.healing",
            "item.minecraft.compass", "item.minecraft.lodestone_compass", "filled_map.swamp_camp_map",
            "block.minecraft.ominous_banner");

    @Test
    void eachItemsNamesAreTogetherInTheOrderOfTheItems() {
        GameData gameData = new GameData(
                List.of(new ItemNameKey("item.minecraft.potion", "minecraft:potion"),
                        new ItemNameKey("item.minecraft.abandoned_camp_map", "minecraft:abandoned_camp_map"),
                        new ItemNameKey("item.minecraft.compass", "minecraft:compass")),
                List.of(new ItemNameKey("block.minecraft.ominous_banner", null),
                        new ItemNameKey("filled_map.swamp_camp_map", "minecraft:abandoned_camp_map")));

        ItemNameKeys.Collected collected = ItemNameKeys.collect(gameData, LANGUAGE_KEYS, CODE_NAMES);
        List<String> keys = collected.keys().stream().map(ItemNameKey::translationKey).toList();

        assertEquals(List.of(
                "item.minecraft.potion.effect.healing", "item.minecraft.potion.effect.water",
                "item.minecraft.abandoned_camp_map", "filled_map.swamp_camp_map",
                "item.minecraft.compass", "item.minecraft.lodestone_compass",
                "block.minecraft.ominous_banner"), keys);
    }

    @Test
    void aDefaultNameThatCodeAlwaysReplacesIsLeftOutAndNoted() {
        GameData gameData = new GameData(
                List.of(new ItemNameKey("item.minecraft.potion", "minecraft:potion"),
                        new ItemNameKey("item.minecraft.compass", "minecraft:compass")),
                List.of());

        ItemNameKeys.Collected collected = ItemNameKeys.collect(gameData, LANGUAGE_KEYS, CODE_NAMES);

        assertFalse(collected.keys().contains(new ItemNameKey("item.minecraft.potion", "minecraft:potion")));
        assertTrue(collected.keys().contains(new ItemNameKey("item.minecraft.compass", "minecraft:compass")));
        assertEquals(1, collected.notes().size());
        assertTrue(collected.notes().getFirst().startsWith("item.minecraft.potion"));
    }

    @Test
    void aNameFromCodeThatMatchesNoKeyIsRefused() {
        GameData gameData =
                new GameData(List.of(new ItemNameKey("item.minecraft.potion", "minecraft:potion")), List.of());
        List<ItemNameKeys.CodeName> outdated = List.of(
                new ItemNameKeys.CodeName("minecraft:shield", "item.minecraft.shield.", true, false));

        assertThrows(IllegalArgumentException.class, () -> ItemNameKeys.collect(gameData, LANGUAGE_KEYS, outdated));
    }
}
