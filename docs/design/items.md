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

Only the stack's own translation key is used. Arguments to the name, like a player's name on a player head, are ignored.

## Name list entries

The name list has one entry per item. Each entry has:

| Field | Example | Meaning |
|---|---|---|
| Item id | `block.minecraft.oak_log` | The item's id. It is saved in progress and sent between server and client, so it never changes once released. |
| Translation keys | `block.minecraft.oak_log` | Every translation key that maps to this item. Most items have one. |
| Japanese name | オークの原木 | The correct answer |
| Reading | おーくのげんぼく | How the Japanese name is said |

The item id is one of the item's translation keys: its only key, or for items with several, the one the name list picks, like `item.minecraft.music_disc_13` for all music discs. It stays the item's id even if Minecraft later drops that key, so saved progress never loses an item.

When Minecraft's Japanese translation gives several keys the same name, they become one entry. When a new Minecraft version splits a name in two, the entry keeps its item id for one of them, and the other becomes a new item at level 0.

A translation key belongs to at most one entry.

Milestone 4 decides how readings are stored, per kanji and with units like 今日 = きょう. Milestone 3b uses a hand-written list of about 10 items with the reading as one string. Milestone 5 generates the full list from `ja_jp.json`.

## Protected items

Some items are protected and never removed in a punishment: the dragon egg, shulker boxes and bundles. Which items are protected is a fixed list of item ids in the core, not a setting. Milestone 9 adds it.
