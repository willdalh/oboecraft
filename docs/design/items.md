# Items

An item is anything in Minecraft with its own Japanese name. It is not the same thing as a Minecraft item, like `minecraft:potion`:

- One Minecraft item can be several items. Each potion effect has its own Japanese name, like 治癒のポーション and 暗視のポーション, so each is its own item. The same goes for splash and lingering potions and tipped arrows.
- Several Minecraft items can be one item. Every music disc is called レコード, so all music discs are one item and share one SRS state.
- Variants with the same name are one item. Healing I and II are both 治癒のポーション, and every enchanted book is エンチャントの本.

Each player has one SRS state per item. See [ADR: Items are identified by translation key](../decisions/20261106-items-identified-by-translation-key.md).

## Translation keys

Minecraft finds an item's name in each language through a translation key. The oak log's key is `block.minecraft.oak_log`, and Japanese turns it into オークの原木. A potion's key depends on its effect: `item.minecraft.potion.effect.healing` for both Healing I and II, `item.minecraft.potion.effect.night_vision` for night vision. Minecraft's `ja_jp.json`, which the name list comes from, is keyed by translation key.

So a translation key is what one Japanese name hangs on, and the mod maps stacks to items through it:

1. The server or client takes the stack's item name, which is the name the item has without any custom name. A name given with an anvil or a name tag is ignored, so renaming a stack never changes which item it is.
2. The item name is a translatable text, and its translation key is looked up in the name list.
3. If the name list has an entry with that key, the stack is that item. If not, the stack is not an item: it is never locked, shows its normal name and has no SRS state. This covers other mods' items and new vanilla items until the name list is updated.

A few Minecraft items get a different key from their components, so they are several items, like the potions. A shield with a base color is named through `item.minecraft.shield.<color>`, like `item.minecraft.shield.red` (赤色の盾), and a lodestone compass through `item.minecraft.lodestone_compass` (ロードストーンコンパス). The name list gives these keys entries, or adds them to another entry, like any other key.

Only the translation key is used, not the arguments filled into the name. Some keys exist only to fill in such arguments. A player head with a player's profile is named through `block.minecraft.player_head.named`, which is `%sの頭` in Japanese. A key whose Japanese text has format arguments, like `%s`, is never its own entry, since `%sの頭` can't be an answer. Instead it is added to the entry of the item it names, so a named player head is still the player head, プレイヤーの頭. If that item has no entry, the key is left out. The name list script in milestone 5 lists these keys for review.

## Name list entries

The name list has one entry per item. Each entry has:

| Field | Example | Meaning |
|---|---|---|
| Item id | `block.minecraft.oak_log` | The item's id. It is saved in progress and sent between server and client, so it never changes once released. |
| Translation keys | `block.minecraft.oak_log` | Every translation key that maps to this item. Most items have one. |
| Former ids | | Item ids of entries that were merged into this one. Usually empty. Added with the first name list update that merges entries. |
| Japanese name | オーク, の, 原 (げん), 木 (ぼく) | The correct answer, as [name parts](#name-parts) with their readings |

The item id is one of the item's translation keys: its only key, or for items with several, the one the name list picks, like `item.minecraft.music_disc_13` for all music discs. It stays the item's id even if Minecraft later drops that key, so saved progress never loses an item.

When Minecraft's Japanese translation gives several keys the same name, they become one entry.

Translations change between Minecraft versions, so entries can split and merge:

- **A split:** when two keys of one entry get different names, the entry keeps its item id for one of them, and the other becomes a new item at level 0.
- **A merge:** when two entries get the same name, they become one entry. It keeps one of the item ids, and the other goes in its former ids. Players may already have SRS states for both ids. When progress is loaded, a state saved under a former id is moved to the entry's item id. If the player has states for both, the one that has them review the item sooner is kept: leveled before known or ignored, then the lower level, then the earlier locks-at time. The other is dropped, so a merge never unlocks an item the player hasn't earned.

An item id or former id belongs to at most one entry, and is never reused for another item.

A translation key belongs to at most one entry.

The name list so far is a hand-written list of 10 blocks from the first minutes of play: the oak, birch and spruce logs, oak planks, the crafting table, the torch, dirt, cobblestone, sand and gravel. Milestone 5 generates the full list from `ja_jp.json`. The file format is in [Name list file](../architecture/name-list-file.md).

The hand-written list has no merges, so 3b doesn't build former ids or the merge of states on load yet. They come with the first name list update that merges two entries, before any release that needs them.

## Name parts

The Japanese name is stored split into name parts, so that each part has its own reading. An answer may write each part as it is written or as its reading, see [Answers](answers.md). A part is one of:

| Part | Example | Reading |
|---|---|---|
| One kanji | 原 | げん, how it is said in this name |
| Kanji that are only read together | 今日, 松明 | きょう, たいまつ |
| A run of kana | オーク | おーく, the kana as hiragana |
| The particle の | の | の |
| Letters or digits | TNT | てぃーえぬてぃー |

- Readings are per kanji where the kanji can be read one by one: 原木 is 原 (げん) and 木 (ぼく), and 作業台 is 作 (さ), 業 (ぎょう) and 台 (だい). Each reading is the sound the kanji has in this name, with any sound change, like 一本 (いっぽん) as 一 (いっ) and 本 (ぽん).
- Kanji that can't be split, because the word's reading doesn't come from each kanji, like 今日 (きょう), 松明 (たいまつ) and 砂利 (じゃり), are one part.
- Kana between kanji, like the き in 焼き鳥, is its own part. Kana is always read as it is written.
- The particle の is its own part, since an answer may leave it out. A の inside a word, like キノコ, stays in that word's part.
- Readings are in hiragana, with ー for a long vowel where the name has one. A reading never starts with a small kana like ゃ or with ー, since those belong to the kana before them, or with ん, which ends the syllable before it.
- Where a part ends matters for long vowels: a long vowel is only ever inside a part, see [Answers](answers.md#long-vowels). So parts are split where the name's sounds are separate, like 小 (こ) and 売 (うり), and a long vowel is never split between two parts.
- The small ヵ and ヶ, which are counters in names, like 三ヶ月, need their reading given, since it is か, が or こ.
- Punctuation, like the ・ in ジャック・オ・ランタン, stays in the part's text and is left out of its reading.

The Japanese name is the text of its parts joined, like オークの原木, and its reading is their readings joined, like おーくのげんぼく. The reading is what is shown after a pass, and what milestone 10 speaks.

## Protected items

Some items are protected and never removed in a punishment: the dragon egg, shulker boxes and bundles. Which items are protected is a fixed list of item ids in the core, not a setting. Milestone 9 adds it.
