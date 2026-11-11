# Items

An item is anything in Minecraft with its own name in a learning language. The Japanese examples below illustrate grouping; every language groups its own keys separately. It is not the same thing as a Minecraft item, like `minecraft:potion`:

- One Minecraft item can be several items. Each potion effect has its own Japanese name, like 治癒のポーション and 暗視のポーション, so each is its own item. The same goes for splash and lingering potions and tipped arrows.
- Several Minecraft items can be one item. Every music disc is called レコード, so all music discs are one item and share one SRS state.
- Variants with the same name are one item. Healing I and II are both 治癒のポーション, and every enchanted book is エンチャントの本.

Each player has one SRS state per item and learning language. See [ADR: Items are identified by translation key](../decisions/20261106-items-identified-by-translation-key.md).

## Learning language

The learning language follows the game language: `ja_jp` selects Japanese, `de_de` German, `de_at` Austrian German, `no_no` Norwegian Bokmål, and `nl_nl` Dutch. Every other code selects Japanese, including `de_ch`, `nn_no`, `nb_no` and `nl_be`. It is selected on joining and stays fixed until the next join. Changing the game language while connected announces the next join's learning language in chat. Minecraft's and the mod's text still follow the game language immediately.

Every supported language is allowed on every server. Players on one server can learn different languages at once. The Japanese style, tsukumogami and **あ** button do not depend on the learning language.

Each language has its own name list, ids and progress. Brick and bricks share one Japanese and one Austrian German entry but are separate German, Bokmål and Dutch items; Bokmål and Dutch also split nether brick/bricks and resin brick/bricks. The awkward, mundane and thick tipped arrows share an entry in German, Bokmål and Dutch. Austrian German groups awkward and thick arrows; mundane arrows have a separate entry, even though review corrected their spelling to the same name. The answer is the reviewed Minecraft translation, corrected only if broken. German, Austrian German, Bokmål and Dutch names are plain text with no parts or readings.

Austrian German currently preserves three incorrect groupings from Minecraft's source translation: copper chain and copper chest share `Kupfakistn`, mossy cobblestone wall and mossy stone brick wall share `Bemooste Bruchstoamaua`, and guardian and warden spawn eggs share `Wächta-Spawn-Ei`. These are reviewed upstream limitations, not additional accepted answers. A correction to one entry's name would still name one of its items incorrectly. Splitting these groups needs generator support for retaining reviewed splits when the source names remain identical; the current tool would reject them as a merge on regeneration. Until that is supported, these items share names and progress within Austrian German. Other learning languages remain independent.

## Translation keys

Minecraft finds an item's name in each language through a translation key. The oak log's key is `block.minecraft.oak_log`, and Japanese turns it into オークの原木. A potion's key depends on its effect: `item.minecraft.potion.effect.healing` for both Healing I and II, `item.minecraft.potion.effect.night_vision` for night vision. Minecraft's language files, which the name lists come from, is keyed by translation key.

So a translation key is what a name hangs on, and the mod maps stacks to items through it:

1. The server or client takes the stack's item name, which is the name the item has without any custom name. A name given with an anvil or a name tag is ignored, so renaming a stack never changes which item it is.
2. The item name is a translatable text, and its translation key is looked up in the name list.
3. If the name list has an entry with that key, the stack is that item. If not, the stack is not an item: it is never locked, shows its normal name and has no SRS state. This covers other mods' items and new vanilla items until the name list is updated.

A few Minecraft items get a different key from their components, so they are several items, like the potions. The game's code names a potion, splash potion, lingering potion or tipped arrow after its potion, like `item.minecraft.potion.effect.healing`, and one with no potion `...effect.empty`, so their default keys, like `item.minecraft.potion`, never name a stack and aren't in the name list. A shield with a base color is named through `item.minecraft.shield.<color>`, like `item.minecraft.shield.red` (赤色の盾), and a lodestone compass through `item.minecraft.lodestone_compass` (ロードストーンコンパス). The game's data can also give a stack its own item name: the maps found in abandoned camps are named after the camp, like `filled_map.swamp_camp_map` (沼地のキャンプの地図), and the ominous banner is named `block.minecraft.ominous_banner` (不吉な旗). The name list gives these keys entries, or adds them to another entry, like any other key. Which keys name stacks is worked out by the [name list tool](../architecture/name-list-tool.md#steps), and a game test checks that every stack the game names is in the name list.

Only the translation key is used, not the arguments filled into the name. Some keys exist only to fill in such arguments. A player head with a player's profile is named through `block.minecraft.player_head.named`, which is `%sの頭` in Japanese. A key whose text in the learning language has format arguments, like `%s`, is never its own entry, since `%sの頭` can't be an answer. Instead it is added to the entry of the item it names, so a named player head is still the player head, プレイヤーの頭. If that item has no entry, the key is left out. The name list tool lists these keys for review.

## Name list entries

The name list has one entry per item. Each entry has:

| Field | Example | Meaning |
|---|---|---|
| Item id | `block.minecraft.oak_log` | The item's id. It is saved in progress and sent between server and client, so it never changes once released. |
| Translation keys | `block.minecraft.oak_log` | Every translation key that maps to this item. Most items have one. |
| Former ids | | Item ids of entries that were merged into this one. Usually empty. Added with the first name list update that merges entries. |
| Name | Eichenstamm, or オーク, の, 原 (げん), 木 (ぼく) | The correct answer: plain text, or Japanese [name parts](#name-parts) with readings |

The item id is one of the item's translation keys: its only key, or for items with several, the one the name list picks, like `item.minecraft.music_disc_13` for all music discs. It stays the item's id even if Minecraft later drops that key, so saved progress never loses an item.

When Minecraft's translation in one learning language gives several keys the same name, they become one entry. Besides the music discs, every smithing template is 鍛冶型 and every banner pattern 旗の模様, and the brick item and the bricks block are both レンガ.

Translations change between Minecraft versions, so entries can split and merge:

- **A split:** when two keys of one entry get different names, the entry keeps its item id for one of them, and the other becomes a new item at level 0.
- **A merge:** when two entries get the same name, they become one entry. It keeps one of the item ids, and the other goes in its former ids. Players may already have SRS states for both ids. When progress is loaded, a state saved under a former id is moved to the entry's item id. If the player has states for both, the one that has them review the item sooner is kept: leveled before known or ignored, then the lower level, then the earlier locks-at time. The other is dropped, so a merge never unlocks an item the player hasn't earned.

An item id or former id belongs to at most one entry, and is never reused for another item.

A translation key belongs to at most one entry.

The name lists cover Minecraft 26.3: Japanese has 1,738 entries, German and Austrian German 1,737 each, and Bokmål and Dutch 1,739 each. Milestone 5's [name list tool](../architecture/name-list-tool.md) generated it from `ja_jp.json`, and every entry was reviewed. The first 10, from the first minutes of play, were written by hand in milestone 3b. The file format is in [Name list file](../architecture/name-list-file.md).

The name list has no merges yet, so former ids and the merge of states on load aren't built yet. They come with the first name list update that merges two entries, before any release that needs them. Until then, the name list tool stops when an update would merge two entries.

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

The Japanese name is the text of its parts joined, like オークの原木, and its reading is their readings joined, like おーくのげんぼく. After a pass, the [notification](display.md#notification) shows the Japanese name, and after a fail, the answer menu shows the name, and its reading once the player asks for it.

## Protected items

Some items are protected and never removed in a punishment: the dragon egg, shulker boxes and bundles. They are still demoted and banned. Which items are protected is a fixed list of item ids in the core, not a setting: `block.minecraft.dragon_egg`, `block.minecraft.shulker_box` and the 16 colored shulker boxes, and `item.minecraft.bundle` and the 16 colored bundles. A game test checks that each is in every language’s name list. See [Punishment](punishment.md#protected-items).

## Renaming

Only a name tag can be renamed. Renaming any other item with an anvil would let the player give a stack a name of their own, so the anvil leaves the name of every other item as it is: it neither gives it a new name nor takes an existing custom name away, and naming costs nothing for it. Repairing and combining enchantments on an anvil work as normal, and the result keeps the input's name. Only the Minecraft name tag item counts, whatever its item name says, so other mods' items can't be renamed either.

The anvil's name field can only be typed in while a name tag is in its first slot. It shows a locked item's name as `???`, like its tooltip, and then can't be typed in either. The server decides the result, so a modified client that sends a name anyway changes nothing. Name tags lock and unlock like any other item. This is a fixed rule, not a setting, and was added in milestone 11.
