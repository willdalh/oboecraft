# Oboecraft

A Minecraft mod for learning Japanese, using spaced repetition on the items in the game.

## Language

### Items

**Item**:
Anything in Minecraft with its own Japanese name, like oak log or potion of healing. Each item has its own SRS state per player.
_Avoid_: item type, word, card

**Stack**:
One or more copies of the same item in a single slot.
_Avoid_: copy, item stack

**Drop**:
Throw a stack out of the inventory onto the ground.
_Avoid_: eject, throw out

### Locking

**Locked**:
The state of an item where interacting with it opens the answer menu instead. New items start locked.
_Avoid_: blocked, hidden

**Unlocked**:
The state of an item where the player can interact with it freely.
_Avoid_: open, free, available

**Interaction**:
Any action where an item does something, like placing, eating, equipping, or moving it into or out of a container. Moving it within the inventory is not an interaction.
_Avoid_: use, action

**In use**:
An item that is already doing something when it becomes locked, like worn armor or an item in a furnace. The lock only applies at its next interaction.
_Avoid_: active, equipped

### Reviews

**Review**:
One round in the answer menu for a locked item, ending in a pass or a fail.
_Avoid_: quiz, test, check

**Pass**:
A review that ends with a correct answer.
_Avoid_: success, correct review

**Fail**:
A review that ends without a correct answer, after both attempts are wrong or the player chooses don't know.
_Avoid_: miss, wrong answer, failed review

**Attempt**:
One submitted answer in a review. A review has up to two attempts.
_Avoid_: try, guess

**Don't know**:
The action that gives up a review and makes it a fail right away.
_Avoid_: skip, give up

**Lesson**:
The answer menu for an item at level 0, where the player can see the answer and nothing is punished. Passing a lesson puts the item at level 1.
_Avoid_: first review, introduction

### Answers

**Answer menu**:
The screen where the player answers for a locked item.
_Avoid_: menu, quiz screen, popup

**Japanese name**:
The item's name in the Japanese translation of Minecraft, like オークの原木. It is the correct answer in a review.
_Avoid_: translation, correct answer, word

**Reading**:
How a Japanese name is pronounced, written in kana, like おーくのげんぼく. Each Japanese name has one reading, and an answer counts as correct when its reading matches.
_Avoid_: pronunciation, kana

**Name list**:
All items with their Japanese names and readings.
_Avoid_: word list, dictionary, vocabulary

### Spaced repetition

**Level**:
A number for how well the player knows an item, from 0 (never unlocked) up to the top level. A higher level gives a longer interval.
_Avoid_: SRS level, stage, rank

**Interval**:
How long an item stays unlocked after a pass, set by its level.
_Avoid_: lock timer, cooldown, time until locked again

**Mastered**:
An item that has reached the top level. It stays unlocked forever.
_Avoid_: finished, burned, learned

**SRS state**:
Everything tracked about one item for one player: its level, whether it is locked, unlocked, known or ignored, when it locks next, and any ban.
_Avoid_: card, item progress

**Progress**:
All SRS states for one player. It is shared across singleplayer worlds, and each server keeps its own.
_Avoid_: save, profile

**Promote**:
Raise an item's level by one after a pass.
_Avoid_: bump, level up

**Demote**:
Lower an item's level after a fail.
_Avoid_: drop, level down

### Known and ignored items

**Known**:
The state of an item the player marked as already known. It stays unlocked forever and has no level.
_Avoid_: already known, skipped, learned

**Ignored**:
The state of an item the player chose to leave out. It can't be picked up, taken out of containers, or crafted.
_Avoid_: blacklisted, hidden, excluded

**Word list**:
The screen that shows all known, ignored and mastered items.
_Avoid_: library, vocabulary, dictionary

**Revert**:
Putting a known, ignored or mastered item back to level 0, locked.
_Avoid_: reset, undo

### Punishment

**Punishment**:
What happens on a fail: part of the item is removed, the item is demoted, and it gets a ban.
_Avoid_: penalty

**Remove**:
Destroy some of an item's stacks as part of a punishment.
_Avoid_: delete, vanish, lose, destroy

**Current view**:
The inventory plus any open container or crafting grid. A punishment removes stacks only from here.
_Avoid_: visible items, screen

**Protected item**:
An item that is never removed in a punishment, like the dragon egg, shulker boxes and bundles. It is still demoted and banned.
_Avoid_: indestructible, non-vanishable

**Removal rate**:
The share of an item removed on a fail. It is half at level 1 and smaller at higher levels.
_Avoid_: punishment percentage, loss rate

**Ban**:
A period after a fail when the item can't be reviewed.
_Avoid_: cooldown, timeout, lockout
