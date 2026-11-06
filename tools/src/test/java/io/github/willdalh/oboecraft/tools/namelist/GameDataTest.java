package io.github.willdalh.oboecraft.tools.namelist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameDataTest {
    @Test
    void findsANameALootTableGivesAndTheItemOfTheEntry() {
        String lootTable = """
                {"pools": [{"entries": [{"type": "minecraft:item", "name": "minecraft:abandoned_camp_map",
                  "functions": [{"function": "minecraft:set_name", "target": "item_name",
                    "name": {"translate": "filled_map.swamp_camp_map"}},
                    {"function": "minecraft:set_name", "target": "custom_name", "name": {"translate": "other"}}]}]}]}
                """;
        SequencedSet<ItemNameKey> names = new LinkedHashSet<>();

        GameData.collectItemNames(JsonParser.parseString(lootTable), null, names);

        assertEquals(List.of(new ItemNameKey("filled_map.swamp_camp_map", "minecraft:abandoned_camp_map")),
                List.copyOf(names));
    }

    @Test
    void findsANameWhenTheLootFunctionIsNamedByItsType() {
        String lootTable = """
                {"pools": [{"entries": [{"type": "minecraft:item", "name": "minecraft:abandoned_camp_map",
                  "modifier": [{"type": "minecraft:set_name", "target": "item_name",
                    "name": {"translate": "filled_map.swamp_camp_map"}},
                    {"type": "minecraft:exploration_map", "decoration": "minecraft:abandoned_camp"}]}]}]}
                """;
        SequencedSet<ItemNameKey> names = new LinkedHashSet<>();

        GameData.collectItemNames(JsonParser.parseString(lootTable), null, names);

        assertEquals(List.of(new ItemNameKey("filled_map.swamp_camp_map", "minecraft:abandoned_camp_map")),
                List.copyOf(names));
    }

    @Test
    void anIdThatIsntAStacksIsNotAnItem() {
        String data = """
                {"id": "minecraft:some_effect", "values": [{"type": "minecraft:set_name", "target": "item_name",
                  "name": {"translate": "item.minecraft.named"}}]}
                """;
        SequencedSet<ItemNameKey> names = new LinkedHashSet<>();

        GameData.collectItemNames(JsonParser.parseString(data), null, names);

        assertEquals(List.of(new ItemNameKey("item.minecraft.named", null)), List.copyOf(names));
    }

    @Test
    void findsTheItemNameComponentOfAStack() {
        String advancement = """
                {"display": {"icon": {"id": "minecraft:white_banner",
                  "components": {"minecraft:item_name": {"translate": "block.minecraft.ominous_banner"}}}}}
                """;
        SequencedSet<ItemNameKey> names = new LinkedHashSet<>();

        GameData.collectItemNames(JsonParser.parseString(advancement), null, names);

        assertEquals(List.of(new ItemNameKey("block.minecraft.ominous_banner", "minecraft:white_banner")),
                List.copyOf(names));
    }

    @Test
    void readsDefaultNamesInTheOrderTheItemsAreRegisteredWithoutAir(@TempDir Path reports) throws IOException {
        Files.writeString(reports.resolve("registries.json"), """
                {"minecraft:item": {"entries": {"minecraft:air": {"protocol_id": 0},
                  "minecraft:dirt": {"protocol_id": 2}, "minecraft:stone": {"protocol_id": 1}}}}
                """);
        Path components = Files.createDirectories(reports.resolve("minecraft/components/item"));
        Files.writeString(components.resolve("stone.json"),
                "{\"components\": {\"minecraft:item_name\": {\"translate\": \"block.minecraft.stone\"}}}");
        Files.writeString(components.resolve("dirt.json"),
                "{\"components\": {\"minecraft:item_name\": {\"translate\": \"block.minecraft.dirt\"}}}");

        assertEquals(List.of(new ItemNameKey("block.minecraft.stone", "minecraft:stone"),
                        new ItemNameKey("block.minecraft.dirt", "minecraft:dirt")),
                GameData.readDefaultNames(reports));
    }
}
