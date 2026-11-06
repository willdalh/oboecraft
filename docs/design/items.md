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

Only the translation key is used, not the arguments filled into the name. Some keys exist only to fill in such arguments. A player head with a player's profile is named through `block.minecraft.player_head.named`, which is `%sの頭` in Japanese. A key whose Japanese text has format arguments, like `%s`, is never its own entry, since `%sの頭` can't be an answer. Instead it is added to the entry of the item it names, so a named player head is still the player head, プレイヤーの頭. If that item has no entry, the key is left out. The name list script in milestone 5 lists these keys for review.

## Name list entries

The name list has one entry per item. Each entry has:

| Field | Example | Meaning |
|---|---|---|
| Item id | `block.minecraft.oak_log` | The item's id. It is saved in progress and sent between server and client, so it never changes once released. |
| Translation keys | `block.minecraft.oak_log` | Every translation key that maps to this item. Most items have one. |
| Former ids | | Item ids of entries that were merged into this one. Usually empty. |
| Japanese name | オークの原木 | The correct answer |
| Reading | おーくのげんぼく | How the Japanese name is said |

The item id is one of the item's translation keys: its only key, or for items with several, the one the name list picks, like `item.minecraft.music_disc_13` for all music discs. It stays the item's id even if Minecraft later drops that key, so saved progress never loses an item.

When Minecraft's Japanese translation gives several keys the same name, they become one entry.

Translations change between Minecraft versions, so entries can split and merge:

- **A split:** when two keys of one entry get different names, the entry keeps its item id for one of them, and the other becomes a new item at level 0.
- **A merge:** when two entries get the same name, they become one entry. It keeps one of the item ids, and the other goes in its former ids. Players may already have SRS states for both ids. When progress is loaded, a state saved under a former id is moved to the entry's item id. If the player has states for both, the one that has them review the item sooner is kept: leveled before known or ignored, then the lower level, then the earlier locks-at time. The other is dropped, so a merge never unlocks an item the player hasn't earned.

An item id or former id belongs to at most one entry, and is never reused for another item.

A translation key belongs to at most one entry.

Milestone 4 decides how readings are stored, per kanji and with units like 今日 = きょう. Milestone 3b uses a hand-written list of about 10 items with the reading as one string. Milestone 5 generates the full list from `ja_jp.json`.

## Protected items

Some items are protected and never removed in a punishment: the dragon egg, shulker boxes and bundles. Which items are protected is a fixed list of item ids in the core, not a setting. Milestone 9 adds it.
