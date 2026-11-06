# ADR-20261106: Items are identified by translation key

## Status
Accepted

## Context
One item is one Japanese name, so an item is not the same as a Minecraft item. Every potion is `minecraft:potion`, but each effect has its own Japanese name and its own SRS state. All music discs are different Minecraft items, but they share the name レコード and so share one SRS state. Healing I and II are different potions with the same name.

The mod needs an id for each item. It is saved in every player's progress and sent between server and client, so changing it later means upgrading every save. It also decides how a stack in the game is mapped to its item.

Minecraft finds an item's name through a translation key, like `block.minecraft.oak_log` or `item.minecraft.potion.effect.healing`. A potion's key depends on its effect, and Healing I and II share theirs. `ja_jp.json`, which the name list is made from, is keyed by translation key.

## Decision
A stack maps to its item through the translation key of its item name, ignoring any custom name. The name list lists the translation keys for each item, and a stack whose key isn't in the list is not an item.

The item id is one of the item's translation keys. When several keys share a Japanese name, like the music discs, they are one name list entry, and the name list picks one of them as the id. An id never changes once released, even if Minecraft drops that key.

Alternatives considered:
- Minecraft item ids, like `minecraft:oak_log`. They can't tell potion effects apart, and every exception would need its own code that reads data components.
- Minecraft item ids plus rules for potions, tipped arrows and the other exceptions. Every new item with component-based names would need new code, and the rules would repeat what Minecraft's names already know.
- The Japanese name itself as the id. Grouping comes for free, but Mojang sometimes fixes Japanese translations, and every fix would reset progress for that item.
- Ids made up by the mod, like `oak_log`. Readable, but the name list script would have to invent and keep a mapping for about 1,500 items, with no gain over the translation key.

## Consequences
- Potions, tipped arrows, music discs and enchanted books need no special code. Grouping is data in the name list.
- The name list script works on `ja_jp.json` keys directly.
- Renaming a stack with an anvil or name tag never changes which item it is.
- Progress files are keyed by strings like `block.minecraft.oak_log`, which are readable by hand.
- Grouping by Japanese name means translation fixes can still split or merge items. A split keeps the old id for one key, and the other starts at level 0. A merge keeps one id and lists the other as a former id, and loading progress keeps the state that has the player review sooner. This is rarer and gentler than ids made from the names, where every fix to a name would reset its item.
- Keys whose Japanese text has format arguments, like `block.minecraft.player_head.named` (`%sの頭`), can't be answers. The name list adds them to the entry of the item they name instead.
- Items from other mods are left out unless they are added to the name list, which works the same way.
- The mapping depends on Minecraft putting a translation key in the item name. If some item doesn't, only the mapping code in `fabric` changes, not the ids.
