package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.punishment.ProtectedItems;
import io.github.willdalh.oboecraft.core.item.LearningLanguage;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.JapaneseName;
import io.github.willdalh.oboecraft.core.item.NameList;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.item.NamePart;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.item.ItemMapping;
import io.github.willdalh.oboecraft.fabric.item.NamedStacks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.storage.loot.LootTable;

/** The name list that ships with the mod, as the server loaded it from its file. */
public class NameListGameTest {
    private static Optional<JapaneseName> japaneseNameOf(String id) {
        NameList nameList = OboecraftMod.itemMappings().get(LearningLanguage.JAPANESE).nameList();
        return nameList.byId(new ItemId(id)).map(entry -> (JapaneseName) entry.name());
    }

    @GameTest
    public void kanaPartsAndKanjiWithTheirReadingsAreLoaded(GameTestHelper helper) {
        JapaneseName expected = new JapaneseName(List.of(
                NamePart.kana("オーク"), NamePart.kana("の"), new NamePart("原", "げん"), new NamePart("木", "ぼく")));

        helper.assertValueEqual(japaneseNameOf("block.minecraft.oak_log"), Optional.of(expected), "oak log");
        helper.succeed();
    }

    @GameTest
    public void kanjiReadTogetherAreOnePart(GameTestHelper helper) {
        JapaneseName expected = new JapaneseName(List.of(new NamePart("松明", "たいまつ")));

        helper.assertValueEqual(japaneseNameOf("block.minecraft.torch"), Optional.of(expected), "torch");
        helper.succeed();
    }

    /**
     * Every stack the game can name is an item: each Minecraft item, and the stacks the game's code names from their
     * components, which the name list tool keeps a hand-written list of. Names that the game's data gives stacks are
     * read from the data by the tool itself.
     */
    @GameTest
    public void everyStackTheGameNamesIsInTheNameList(GameTestHelper helper) {
        List<ItemStack> stacks = NamedStacks.all(
                helper.getLevel().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN));
        ItemMapping mapping = OboecraftMod.itemMappings().get(LearningLanguage.JAPANESE);
        List<String> missing = stacks.stream()
                .filter(stack -> mapping.entryOf(stack).isEmpty())
                .map(stack -> ItemMapping.translationKeyOf(stack).orElse(stack.toString()))
                .distinct()
                .toList();
        helper.assertTrue(missing.isEmpty(), "every stack is in the name list, but these keys aren't: " + missing);
        helper.succeed();
    }

    /**
     * Every item name that the game's loot tables give stacks, like the maps found in abandoned camps, is in the name
     * list. The loot tables are encoded back to JSON and searched for the loot function that sets an item name, the
     * same way the name list tool searches the data in the server jar.
     */
    @GameTest
    public void everyItemNameALootTableGivesIsInTheNameList(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        List<String> keys = new ArrayList<>();
        server.reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).listElements().forEach(table ->
                LootTable.DIRECT_CODEC.encodeStart(ops, table.value())
                        .ifSuccess(json -> collectItemNameKeys(json, keys))
                        .ifError(error -> helper.fail("couldn't encode " + table.key() + ": " + error.message())));

        ItemMapping mapping = OboecraftMod.itemMappings().get(LearningLanguage.JAPANESE);
        List<String> missing = keys.stream()
                .distinct()
                .filter(key -> mapping.nameList().byTranslationKey(key).isEmpty())
                .toList();
        helper.assertTrue(keys.contains("filled_map.swamp_camp_map"), "the loot tables name the swamp camp map");
        helper.assertTrue(missing.isEmpty(), "every item name in the loot tables is in the name list, but these "
                + "aren't: " + missing);
        helper.succeed();
    }

    /** Collects the keys of the loot functions that set a stack's item name. */
    private static void collectItemNameKeys(JsonElement json, List<String> keys) {
        if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(element -> collectItemNameKeys(element, keys));
        } else if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            boolean setName = isString(object.get("type"), "minecraft:set_name")
                    || isString(object.get("function"), "minecraft:set_name");
            if (setName && isString(object.get("target"), "item_name")
                    && object.get("name") instanceof JsonObject name && name.has("translate")) {
                keys.add(name.get("translate").getAsString());
            }
            object.entrySet().forEach(entry -> collectItemNameKeys(entry.getValue(), keys));
        }
    }

    private static boolean isString(JsonElement json, String value) {
        return json != null && json.isJsonPrimitive() && json.getAsString().equals(value);
    }

    @GameTest
    public void keysWithTheSameJapaneseNameAreOneItem(GameTestHelper helper) {
        ItemMapping mapping = OboecraftMod.itemMappings().get(LearningLanguage.JAPANESE);
        Optional<ItemId> disc13 = mapping.entryOf(new ItemStack(Items.MUSIC_DISC_13)).map(NameListEntry::id);
        Optional<ItemId> discCat = mapping.entryOf(new ItemStack(Items.MUSIC_DISC_CAT)).map(NameListEntry::id);

        helper.assertValueEqual(discCat, disc13, "the item of the music discs");
        helper.succeed();
    }

    @GameTest
    public void aNamedPlayerHeadIsThePlayerHead(GameTestHelper helper) {
        ItemStack namedHead = new ItemStack(Items.PLAYER_HEAD);
        namedHead.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved("Steve"));

        helper.assertValueEqual(OboecraftMod.itemMappings().get(LearningLanguage.JAPANESE).entryOf(namedHead).map(NameListEntry::id),
                Optional.of(new ItemId("block.minecraft.player_head")), "the item of a named player head");
        helper.succeed();
    }
    @GameTest public void everyLanguageCoversAllJapaneseKeysAndProtectedItems(GameTestHelper helper) {
        var keys = OboecraftMod.itemMappings().get(LearningLanguage.JAPANESE)
                .nameList().entries().stream().flatMap(entry -> entry.translationKeys().stream()).toList();
        for (var language : LearningLanguage.values()) {
            var mapping = OboecraftMod.itemMappings().get(language);
            for (String key : keys) helper.assertTrue(mapping.nameList().byTranslationKey(key).isPresent(), language + ": " + key);
            for (var id : ProtectedItems.IDS) {
                helper.assertTrue(mapping.nameList().byId(id).isPresent(), language + " protects " + id);
            }
        }
        helper.succeed();
    }

}
