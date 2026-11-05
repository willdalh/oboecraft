# Oboecraft idea

Oboecraft is a Minecraft mod for learning Japanese. The core idea is a spaced repetition system (SRS) for the items in the game.

The mod is made for playing with the game language set to Japanese.

## The basic idea

Items start out locked. A locked item can be moved around in the inventory, but trying to interact with it (placing it, using it as an ingredient to craft, and so on) opens the answer menu instead. There you have to write the item's Japanese name, the name it has in the Japanese version of Minecraft.

Answering correctly unlocks the item, and it can then be used and interacted with freely. This affects the item globally for the player, so every stack of that item is unlocked. When its interval runs out, the item is locked again. Each pass makes the interval longer. This is the spaced repetition part.

### Example: starting a new world

How the mod should work is best described by starting a new world. You walk up to a tree and punch it, and you get an oak log. The oak log is marked as locked in the inventory. When you try to place it, the answer menu opens, and you write the Japanese name of the oak log in kana, romaji or kanji.

You answer correctly, which starts the SRS for the oak log, and now you can use it. If you punch another oak log, it is already unlocked and stacks together with the one you have.

Now you put the oak log in the 2x2 crafting grid, and wooden planks appear in the result slot. To complete the crafting, you have to answer correctly for the wooden planks as well.

## The answer menu

### Answer rules

The correct answer is the item's full Japanese name. For example, oak log is オークの原木.

- It does not matter if the answer is written with kanji, kana or a mix, as long as the reading is the same.
- Romaji should be lenient, so ou, oo and ō are all accepted, and the same goes for tu/tsu and si/shi.
- Spaces and the particle の are optional.
- Case and punctuation are ignored.

### Answering correctly

When you answer correctly:

- A sound is played with the reading of the Japanese name, generated with text-to-speech.
- You always get a notification at the top of the screen with the Japanese name, written with kanji if the name has kanji. This way you also see the kanji when you answered in romaji or kana.
- The item is unlocked (see [SRS levels](#srs-levels) for how long it stays unlocked).

### Answering wrong

You get two attempts to answer correctly, so if the first attempt was wrong because of a typo, you can fix it on the second attempt and avoid frustration. Items are removed and the item is demoted only once, after both attempts are wrong. At level 0 there is no punishment, so the two attempts do not matter there. There is also a "don't know" action, which fails the review right away.

Failing removes some of the item and puts a ban on it (see [The punishment system](#the-punishment-system)). The correct answer is displayed after the items have been removed.

### Lessons (level 0)

The first time an item is interacted with (level 0), the answer menu works as a lesson:

- There is an option to display the correct answer. The player then types it to unlock the item at level 1.
- Answering wrong or pressing "don't know" just shows the answer, without removing items or giving a ban.

### Using the menu

- The answer menu shows only a typing bar, with a hint line under it listing the key for each action (see [Keys](#keys)). A setting can also show clickable buttons for the actions.
- The game does not pause while the answer menu is open, so the mod also works in multiplayer. Instead, the player should walk somewhere safe to do their reviews.
- Closing the answer menu with Esc cancels the review without punishment, and the item stays locked.
- Right-clicking a locked item in the inventory opens the answer menu, so items can be unlocked without using them.
- The answer menu for an item shows its SRS level.
- There are two extra actions, "Already known" and "Ignore", available at any level (see [Known and ignored items](#known-and-ignored-items)).

### Keys

| Action | Default key |
|---|---|
| Answer | Enter |
| Don't know | Delete |
| Already known | Ctrl+K |
| Ignore | Ctrl+I |
| Show answer (lessons only) | Ctrl+S |

- Pressing Enter with an empty typing bar does nothing.
- Don't know uses the forward Delete key, separate from Backspace, so fixing a typo never fails the review. On Mac laptops it is fn+delete.
- All keys can be rebound in Minecraft's Controls screen.

## What counts as interacting

Any action where the item does something counts as interacting, like placing, eating, equipping, mining or attacking with it, or using it as ammo, fuel or in a trade. Only moving it around in the inventory is free.

- When using a chest, moving an item into or out of the chest counts as interacting, so the item must be unlocked.
- Dropping items out of the inventory can count as interacting or not, depending on a setting. If it is turned on, you have to answer correctly to drop the item. It is off by default.

A lock only blocks the next interaction, so items already in use are not affected:

- Worn armor keeps protecting until it is taken off.
- Items in a furnace stay there until taken out.
- A held tool or weapon does not count as in use, so if it gets locked, the next swing opens the answer menu.
- Placed blocks are not affected, but breaking them gives the item in its current SRS state.

## How items are displayed

- Locked items have their name hidden as "???", both in the hover text and above the hotbar when switching items. Only locked items are hidden.
- Item names showing up in other places, like advancements and death messages, is fine, since it reinforces the vocabulary.
- Hovering over an unlocked item shows a bar with the time until it is locked again.
- The slot gets a small marker when the item is close to locking.

## SRS levels

An item's interval runs in real-world time, also when the player is offline, just like in other SRS apps such as Anki.

- Level 0 means the item has never been unlocked.
- Answering correctly promotes the item, so it takes longer until it needs to be reviewed again.
- Failing demotes the item by one level for levels 1–4 and by two for levels 5–8, but never below level 1. The next correct answer only unlocks the item at the new level, without promoting it. Correct answers after that promote it as normal. This way, if you fail, you are reviewed again sooner.

The levels and their intervals are shown below. Both the number of levels and the intervals are subject to change through playtesting.

| Level | Interval |
|---|---|
| 1 | 10 minutes |
| 2 | 1 hour |
| 3 | 4 hours |
| 4 | 1 day |
| 5 | 3 days |
| 6 | 1 week |
| 7 | 2 weeks |
| 8 | 1 month |
| 9 | Never locked again, the item is mastered |

## Known and ignored items

The answer menu has two extra actions, available at any level:

- **Already known** is for items the player knows from before. It unlocks the item forever.
- **Ignore** is for items the player is not interested in, like andesite. Ignored items are dropped on the ground from the inventory, not removed, and can not be picked up, taken out of containers or crafted.

A word list screen, opened with a key bind, shows all known, ignored and mastered (level 9) items and lets the player revert them by right-clicking an item and choosing Revert. Reverted items go back to level 0. Known and mastered items are kept separate in the list, since mastered items are earned through reviews.

## The punishment system

### How much is removed

When a player fails, part of the reviewed item in the current view is removed. How much depends on the item's SRS level (see [below](#the-srs-level-as-an-advantage)), and at level 1 it is half.

- The current view is the inventory and any open container. So if you fail when taking an item from a chest, the items are removed from that item in the chest and inventory combined.
- With no menu open, only the inventory counts.
- Worn armor is in use, just like items in a furnace, so it is not counted.
- Items inside a closed shulker box or bundle are not counted, but an open shulker box counts as a container, like a chest.
- The number of removed items is always rounded up.
- Some items are protected and never removed on a fail: the dragon egg, shulker boxes and bundles. They lock like normal items, and failing still demotes them and gives a ban, but nothing is removed. When they are crafting ingredients, only the other ingredients are removed.

For example, if the player had 50 wooden planks at level 1 in the inventory and they were locked, failing removes 25 of the planks.

### The SRS level as an advantage

The item's level also works as an advantage in case you fail. Say the SRS level is `srsLevel`. Failing removes `50% - ((srsLevel - 1) * percentageFactor)%` of the items. This is the removal rate. `srsLevel` is the level the item had before the fail demoted it.

A suitable `percentageFactor` depends on how many levels there are and their intervals. With the levels in the [table above](#srs-levels), a `percentageFactor` of 5 means 50% of the items are removed at level 1 and 15% at level 8.

### Crafting

When you fail on an item you are trying to craft, the items removed are the ingredients. The number is based on each ingredient's part in the recipe and the SRS level of the crafted item. When crafting, the current view is the inventory and the crafting grid, so the ingredients are removed from both combined.

For example, say the player wants to craft a diamond pickaxe, which they have unlocked before and is now at level 1 and locked. They have 50 diamonds and 100 sticks, counting the ones in the crafting grid. To move the diamond pickaxe into the inventory after arranging the recipe, they have to answer the Japanese name for the pickaxe. The recipe uses 5 items in total, 3 diamonds and 2 sticks. If they fail at level 1, 3/5 of 50% of the diamonds and 2/5 of 50% of the sticks are removed, which is 15 diamonds and 20 sticks.

### Bans

Every fail from level 1 and up also puts a ban on the item, meaning it cannot be reviewed for 5 minutes. You have to wait until the ban is over before you can open the answer menu to unlock the item again.

The ban is there to make a non-legit way of playing harder. Here is the scenario. The player wants to craft a diamond pickaxe, but doesn't remember the word for diamond. Lucky for them, they already put 49 of their diamonds in one chest and 1 in another chest. They walk up to the second chest, take the one diamond, fail, and it is removed. Now they know the answer. They then walk up to the first chest with 49 diamonds, answer correctly and craft the pickaxe. This way they only lose 1 diamond instead of 25.

The ban does not fully stop this trick, since the player has already seen the answer. It only makes it slower. That is accepted, because the real cost of failing is the demotion, which means the item is reviewed again sooner.

## Other rules

### Renaming

Item renaming already exists in vanilla Minecraft using an anvil. This will be removed, and only name tags can be renamed. Name tags lock and unlock like any other item.

### Game modes

The mod is only active in survival and adventure mode. In creative mode items are not locked, but the SRS state is kept for when the player switches back.

### Saving progress

SRS progress is shared across all singleplayer worlds, so starting a new world does not reset it. On multiplayer servers, each server keeps its own progress for each player.

### Reviewing early

Reviewing an item before it is locked, for example to top up the timer before a trip, is not possible for now, but might be added later.

## Settings

Some things should be controlled by settings:

- Whether dropping items out of the inventory counts as an interaction
- The length of the ban after a fail
- The SRS levels and their intervals
- The `percentageFactor`
- Whether the answer menu shows clickable buttons for the actions

The values described in this document are the defaults. On multiplayer servers, the settings are controlled by the server owner, except the clickable buttons, which each player chooses.
