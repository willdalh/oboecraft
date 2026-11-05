# Oboecraft idea

I want to create a Minecraft mod for learning Japanese. The core idea is a spaced repetition system (SRS) for the items in the game.

The mod is made for playing with the game language set to Japanese.

## The basic idea

Items start out locked. A locked item can be moved around in the inventory, but trying to interact with it (placing it, using it as an ingredient to craft, and so on) is blocked. Instead, a menu opens where you have to write the name of the item as it is in the Japanese version of Minecraft.

Answering correctly unlocks the item, and it can then be used and interacted with freely. This affects the item globally for the player, so every copy of that item is unlocked. After a set amount of time, the item is locked again. Answering again makes the time before it is locked again longer. This is the spaced repetition part.

### Example: starting a new world

How the mod should work is best described by starting a new world. You walk up to a tree and punch it, and you get an oak log. The oak log is marked as locked in the inventory. When you try to place it, the menu opens, and you write the name of the oak log in kana, romaji or kanji.

You answer correctly, which starts the SRS for the oak log, and now you can use it. If you punch another oak log, it is already unlocked and stacks together with the one you have.

Now you put the oak log in the 2x2 crafting grid, and wooden planks appear in the result slot. To complete the crafting, you have to answer correctly for the wooden planks as well.

## The answer menu

### Answer rules

The correct answer is the full name of the item in the Japanese translation of Minecraft. For example, oak log is オークの原木.

- It does not matter if the answer is written with kanji, kana or a mix, as long as the reading is the same.
- Romaji should be lenient, so ou, oo and ō are all accepted, and the same goes for tu/tsu and si/shi.
- Spaces and the particle の are optional.
- Case and punctuation are ignored.

### Answering correctly

When you answer correctly:

- A sound is played with the reading of the item name.
- You always get a notification at the top of the screen with the correct name, written with kanji if the name has kanji. This way you also see the kanji when you answered in romaji or kana.
- The item is unlocked (see [SRS levels](#srs-levels) for how long it stays unlocked).

### Answering wrong

You get two tries to answer correctly, so if the first attempt was wrong because of a typo, you can fix it on the second try and avoid frustration. Items are removed and the level drops only once, after both tries have failed. At level 0 there is no punishment, so the two tries do not matter there. There is also a "don't know" button, which fails immediately.

Failing removes some of the item and puts a ban on it (see [The punishment system](#the-punishment-system)). The correct answer is displayed after the items have been deleted.

### Lessons (level 0)

The first time an item is interacted with (level 0), the menu works as a lesson:

- There is an option to display the correct answer. The player then types it to unlock the item at level 1.
- Answering wrong or pressing "don't know" just shows the answer, without removing items or giving a ban.
- There are two extra buttons, "Already known" and "Ignore" (see [Known and ignored items](#known-and-ignored-items)).

### Using the menu

- The game does not pause while the menu is open, so the mod also works in multiplayer. Instead, the player should walk somewhere safe to do their unlockings.
- Closing the menu with Esc gives no penalty, and the item stays locked.
- Right-clicking a locked item in the inventory opens the menu, so items can be unlocked without using them.
- The menu for an item shows its SRS level.

## What counts as interacting

Any action where the item does something counts as interacting, like placing, eating, equipping, mining or attacking with it, or using it as ammo, fuel or in a trade. Only moving it around in the inventory is free.

- When using a chest, moving an item into or out of the chest counts as interacting, so the item must be unlocked.
- Throwing items out of the inventory can count as interacting or not, depending on a setting. If it is turned on, you have to answer correctly to throw the item out.

A lock only blocks the next interaction, so items already in use are not affected:

- Worn armor keeps protecting until it is taken off.
- Items in a furnace stay there until taken out.
- A held tool or weapon does not count as in use, so if it gets locked, the next swing opens the menu.
- Placed blocks are not affected, but breaking them gives the item in its current SRS state.

## How items are displayed

- Locked items have their name hidden as "???", both in the hover text and above the hotbar when switching items. Only locked items are hidden.
- Item names showing up in other places, like advancements and death messages, is fine, since it reinforces the vocabulary.
- Hovering over an unlocked item shows a bar with the time until it is locked again.
- The slot gets a small marker when the item is close to locking.

## SRS levels

The time until an item is locked again runs in real-world time, also when the player is offline, just like in other SRS apps such as Anki.

- Level 0 means the item has never been unlocked.
- Answering correctly bumps the item's level, so it takes longer until it needs to be tested again.
- Answering wrong decreases the level by one for levels 1–4 and by two for levels 5–8, but never below level 1. The next correct answer unlocks the item at the new level. This way, if you answer wrong, you are tested again sooner.

The levels and the time until an item is locked again are shown below. Both the number of levels and the times are subject to change through playtesting.

| Level | Time until locked again |
|---|---|
| 1 | 10 minutes |
| 2 | 1 hour |
| 3 | 4 hours |
| 4 | 1 day |
| 5 | 3 days |
| 6 | 1 week |
| 7 | 2 weeks |
| 8 | 1 month |
| 9 | Never, the item is mastered |

## Known and ignored items

In the lesson menu at level 0, there are two extra buttons:

- **Already known** is for items the player knows from before. It unlocks the item forever.
- **Ignore** is for items the player is not interested in, like andesite. Ignored items are dropped on the ground from the inventory, not deleted, and can not be picked up, taken out of containers or crafted.

These buttons are only available at level 0, so they can not be used to avoid the punishment.

A word list screen, opened with a key bind, shows all known and ignored items and lets the player revert them. Reverted items go back to level 0.

## The punishment system

### How much is removed

When a player answers wrong, part of the quizzed item in the current view is removed. How much depends on the item's SRS level (see [below](#the-srs-level-as-an-advantage)), and at level 1 it is half.

- The current view is the inventory and any open container. So if you fail when taking an item from a chest, the items are removed from that item in the chest and inventory combined.
- With no menu open, only the inventory counts.
- Items inside a closed shulker box or bundle are not counted, but an open shulker box counts as a container, like a chest.
- The number of removed items is always rounded up.
- Some items are never deleted when answering wrong: the dragon egg, shulker boxes and bundles.

For example, if the player had 50 wooden planks at level 1 in the inventory and they were locked, answering wrong two times deletes 25 of the planks.

### The SRS level as an advantage

The item's level also works as an advantage in case you answer wrong. Say the SRS level is `srsLevel`. Answering wrong makes `50% - ((srsLevel - 1) * percentageFactor)%` of the items vanish.

A suitable `percentageFactor` depends on how many levels there are and the time intervals for the levels. With the levels in the [table above](#srs-levels), a `percentageFactor` of 5 means 50% of the items vanish at level 1 and 15% at level 8.

### Crafting

When you fail on a crafted item, the items removed are the ingredients. The number is based on each ingredient's part in the recipe and the SRS level of the crafted item.

For example, say the player wants to craft a diamond pickaxe, which they have unlocked before and is now at level 1 and locked. They have 50 diamonds and 100 sticks in the inventory. To move the diamond pickaxe into the inventory after arranging the recipe, they have to answer the name for the pickaxe. The recipe uses 5 items in total, 3 diamonds and 2 sticks. If they fail at level 1, 3/5 of 50% of the diamonds and 2/5 of 50% of the sticks are removed, which is 15 diamonds and 20 sticks.

### Bans

Every failed review from level 1 and up also puts a ban on the item, meaning it cannot be unlocked for use for 5 minutes. You have to wait until the ban is over before you can open the menu to unlock the item again.

The ban is there to stop a non-legit way of playing. Here is the scenario. The player wants to craft a diamond pickaxe, but doesn't remember the word for diamond. Lucky for them, they already put 49 of their diamonds in one chest and 1 in another chest. They walk up to the second chest, take the one diamond, answer wrong, and it is deleted. Now they know the answer. They then walk up to the first chest with 49 diamonds, answer correctly and craft the pickaxe. This way they only lose 1 diamond instead of 25.

## Other rules

### Renaming

Item renaming already exists in vanilla Minecraft using an anvil. This will be removed, and only name tags can be renamed. To use name tags, the player must give the Japanese word for "name tag".

### Game modes

The mod is only active in survival and adventure mode. In creative mode items are not locked, but the SRS state is kept for when the player switches back.

### Saving progress

SRS progress is shared across all singleplayer worlds, so starting a new world does not reset it. On multiplayer servers, each server keeps its own progress for each player.

### Reviewing early

Reviewing an item before it is locked, for example to top up the timer before a trip, is not possible for now, but might be added later.

## Settings

Some things should be controlled by settings:

- Whether throwing items out of the inventory counts as an interaction
- The time ban on items when answering wrong
- The SRS levels and the time until an item is locked again for each level
- The `percentageFactor`

The values described in this document are the defaults. On multiplayer servers, the settings are controlled by the server owner.
