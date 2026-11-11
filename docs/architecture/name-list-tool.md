# Name list tool

The name list tool writes the [name list file](name-list-file.md) from Minecraft's Japanese, German, Austrian German, Norwegian Bokmål and Dutch names, and a review list of the entries it generated. An agent reviews every generated entry and corrects the file by hand before it is merged. The tool was built in milestone 5.

```
./gradlew :tools:generateNameList
```

It reads the Minecraft version from `gradle/libs.versions.toml`, updates every file in `fabric/src/main/resources/oboecraft/name_lists/` and writes the Japanese review to `tools/build/name-list/review.md` and the others to `<language>-review.md` beside it. Downloads and the data generator's output are cached in `tools/build/name-list/cache/`, so later runs are quick. JMdict and KANJIDIC2 are only downloaded again once the cache is deleted, for example with `./gradlew clean`.

## Module

The tool is the `tools` Gradle module, package `io.github.willdalh.oboecraft.tools.namelist`. It depends on `core` and uses `NamePart`, `Name`, `JapaneseName`, `PlainName`, `NameListEntry` and `NameList`, so every entry it writes passes the same checks as when the mod loads the file. Nothing depends on `tools`, and it isn't part of the mod jar. It reads and writes the file with `NameListJson` from the `json` module, the same code the mod reads it with, so a hand-edited mistake gets the same error that names the entry.

## Sources

| Source | From | Used for |
|---|---|---|
| `ja_jp.json`, `de_de.json`, `de_at.json`, `no_no.json`, `nl_nl.json` | Mojang's asset server, found through the version's asset index | The name of each translation key in that learning language |
| `en_us.json` in the client jar | Mojang, from the version’s own file | Flagging untranslated names for review |
| The server jar | Mojang, from the version's own file | Running Minecraft's data generator, and the game's data |
| SudachiDict core | PyPI, where it is published as a Python package, at a fixed version with its checksum | Splitting names into words and reading them, with Sudachi |
| JMdict (`JMdict_e.gz`) | EDRDG | Checking the reading of each word written with kanji |
| KANJIDIC2 | EDRDG | Splitting a word's reading between its kanji |

Mojang's files are checked against the SHA-1 checksums Mojang gives. EDRDG updates its files every day under the same address, so they have no checksum. The review list names the JMdict and KANJIDIC2 versions it used. The dictionaries are XML from outside, so they are read without loading anything the file points to: no external entities and no external DTD. A file whose DOCTYPE names one fails to read.

Only SudachiDict's dictionary is a library dependency. The others are data files the tool downloads. None of them ship with the mod. Their licenses are in [`CREDITS.md`](../../CREDITS.md).

## Steps

1. **Item name keys** (`GameData`, `ItemNameKeys`). The tool runs Minecraft's data generator (`net.minecraft.data.Main --reports`) from the server jar. It needs no EULA. Its reports give every Minecraft item's default name and the order the items are registered in. Air is left out, since it is the empty stack. The data generator writes into a folder of its own, which only becomes the cache once it has finished without an error, so a run that was cut short is run again. Some names don't come from the default name, see [Items](../design/items.md#translation-keys):
   - Names that the game's code gives a stack from its components. No report lists them, so `ItemNameKeys.CODE_NAMES` lists them by hand: potions, splash potions, lingering potions and tipped arrows by potion, shields by base color, the lodestone compass and the named player head. These are the Minecraft items whose class overrides `Item.getName(ItemStack)`. A rule can say that the code always replaces the item's default name, as for the potions and tipped arrows, whose stacks with no potion are named `...effect.empty`. Then the default key, like `item.minecraft.potion`, is left out and listed in the notes. Check the list for each new Minecraft version. A rule that no key in `ja_jp.json` matches stops the tool, and the game test `everyStackTheGameNamesIsInTheNameList` fails if the game names a stack with a key the name list doesn't have.
   - Names that the game's data gives a stack: a loot function that sets the item name (`minecraft:set_name` with the target `item_name`, named by `type` since 26.1 and by `function` before), like the maps found in abandoned camps, or a stack's `item_name` component, like the ominous banner in advancements. The tool finds these in the data in the server jar. The item a name belongs to is the loot entry's item, the item a trade gives, or the `id` of a stack, which is an object with a `count` or `components`. The game test `everyItemNameALootTableGivesIsInTheNameList` checks the loot tables of the running game the same way.

   Each item's keys are kept together, in the order the items are registered.
2. **Entries** (`NameListGenerator`). Within each language, keys with the same name become one entry, whose item id is its first key. A key whose name has a format argument, like `%sの頭`, is added to the entry of the item it names instead. The other keys with no name in that language are left out and listed.
3. **Reviewed entries are kept.** An entry already in the name list keeps its item id. A name that is unchanged keeps its parts, since they were reviewed, even when it splits off as a new item. Otherwise the name is guessed again and listed for review. When an entry's keys get different names, the name of its item id's key keeps the entry, and the others become new items, as in [Items](../design/items.md#name-list-entries). If Minecraft dropped the id's key, the entry lives on in its other keys, and the dropped key belongs to a new item if it comes back. A new item's id is its first key that isn't already an item id, so an id is never given to another item, and the tool stops if a new item has no such key. When two entries get the same name, the tool stops, since a merge needs former ids, which the name list doesn't have yet. An entry whose keys are all gone from the game is kept, so saved progress for it isn't lost.
4. **Guessing Japanese parts** (`DictionaryNameGuesser`), for new and changed names only:
   - Sudachi splits the name into words, in its longest units (split mode C), and reads them.
   - The particle の is its own part. Kana next to each other is one part if it is from the same word, or in the same script. So the hiragana された stays together, and the だ of 歪んだ stays apart from the katakana ナイリウム after it. Punctuation like ・ joins the part before it.
   - Latin letters, like TNT, are spelled out (てぃーえぬてぃー).
   - A word with kanji is checked with JMdict, in its dictionary form if JMdict doesn't have it as written. If the word is missing, or JMdict doesn't have Sudachi's reading, it is noted. If Sudachi has no reading at all, each kanji gets its first reading in KANJIDIC2, and that is noted too.
   - `KanjiSplitter` splits the word's reading between its kanji with their KANJIDIC2 readings, allowing rendaku (a dakuten or handakuten on the first kana after the first kanji) and a small っ at the end of a kanji's reading (いち read いっ). A kun reading with okurigana also counts as its stem form, like やき for 焼 in 焼鳥. It picks the split with the fewest sound changes. Kanji whose readings don't add up are one part, read together, like 松明 and 硫黄. A kanji with a reading KANJIDIC2 doesn't have is only its own part when it has no kanji next to it. Kana that can't start a part, like ん, joins the part before it. A split that could go more than one way is noted.
   - Two parts of one word that meet in what could be a long vowel, like 灰 (はい) and 色 (いろ), are noted, since a long vowel is only matched inside a part.
5. **Output.** The name list is written one entry per line, in the order of the keys. The file is replaced only once the new one is fully written. The review list has the notes about the whole list, then the entries with notes ("words to check closely"), then every generated entry. Plain-name reviews flag untranslated names (equal to English), format arguments and splits. A merge stops the tool with the conflicting ids for review; former ids must be implemented before it can ship. Plain names use no dictionaries or readings.

## Reviewing

Every generated entry is reviewed, not only the ones with notes. A wrong reading can pass every check, like 金 read かね in 金のリンゴ. The reviewer corrects the appropriate `<language>.json` by hand. Plain names keep the original translation in `sourceName`, so a reviewed correction of `name` survives later runs until Minecraft changes that original translation. Running the tool again then keeps the corrected entries, since their names haven't changed, and lists 0 generated entries.

Common corrections from the first review, in milestone 5:

- Sudachi picks a reading that fits the word on its own but not the name, like 焼き豚 read やきとん for やきぶた, 樽 read そん, and 試練の間 read あいだ for ま.
- Kanji read together that KANJIDIC2 splits, like 鍛冶 (かじ).
- Sound changes Sudachi misses in a longer word, like 鉄 in 深層鉄鉱石 (てっ) and 熱帯魚入り (ぎょ).
- Colors are read with いろ, like 白色 (しろいろ), as in the other color names.

## Tests

The tool's rules have JUnit tests in `tools/src/test`, with small hand-written dictionaries and a stand-in for Sudachi, so they need no downloads, and the file format has its tests in `json/src/test`. `./gradlew build` runs them. The tool itself is only run by hand.

The command-line tool takes `--minecraft-version`, `--name-lists` (the output folder), `--review-list` (the Japanese review file), and `--cache`. The Gradle task supplies them. Generated German, Austrian German, Bokmål and Dutch entries use the same key collection and id retention rules as Japanese.
