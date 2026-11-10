# Oboecraft

A Minecraft mod for learning Japanese, using spaced repetition on the items in the game.

## Language

### Items

**Item**:
Anything in Minecraft with its own Japanese name, like oak log or potion of healing. Each item has its own SRS state per player.
_Avoid_: item type, word, card

**Minecraft item**:
One of Minecraft's own kinds of item, like `minecraft:potion`. One Minecraft item can be several items, like the potions, and several Minecraft items can be one item, like the music discs.
_Avoid_: item type, vanilla item

**Item id**:
The name that identifies an item, like `block.minecraft.oak_log`. It is one of the translation keys of the item's Japanese name.
_Avoid_: item key, Minecraft id

**Translation key**:
Minecraft's name for a piece of text that is the same in every language, like `block.minecraft.oak_log`. Each language file, like `ja_jp.json`, gives the text for each key.
_Avoid_: lang key, name key

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
Any action where an item does something, like placing, eating, equipping, attacking with a weapon, mining with a tool, or moving it into or out of a container. Moving it within the inventory is not an interaction.
_Avoid_: use, action

**Lock check**:
The rule that decides what happens when a player interacts with a stack or picks one up: allow it, open the answer menu, show the ban, or deny it.
_Avoid_: lock test, permission check

**In use**:
An item that is already doing something when it becomes locked, like worn armor or an item in a furnace. The lock only applies at its next interaction.
_Avoid_: active, equipped

### Display

**Lock timer**:
The remaining time before an unlocked item locks, shown as a time label and draining bar in its hover tooltip. It is different from the interval, which is the whole time granted by a pass.
_Avoid_: cooldown

**Slot marker**:
The small amber mark on a stack during the final tenth of its interval. It disappears when the item locks.
_Avoid_: badge, warning icon

**Notification**:
The item's Japanese name, shown at the top of the screen for a few seconds after a pass.
_Avoid_: toast, popup, title

### Reviews

**Review**:
One round in the answer menu for a locked item, ending in a pass or a fail, or cancelled when the player closes the menu.
_Avoid_: quiz, test, check

**Trigger**:
What the player was doing when a review started: an interaction in the inventory, moving a stack to or from a container, or crafting. It decides the current view for a punishment.
_Avoid_: source, cause, reason

**Pass**:
A review that ends with a correct answer.
_Avoid_: success, correct review

**Fail**:
A review that ends without a correct answer, after both attempts are wrong or the player chooses don't know.
_Avoid_: miss, wrong answer, failed review

**Attempt**:
One submitted answer in a review. An item has up to two attempts until it is passed or failed, even if the answer menu is closed in between.
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
How a Japanese name or a name part is pronounced, written in hiragana, like おーくのげんぼく. Each Japanese name has one reading, made of its name parts' readings.
_Avoid_: pronunciation, kana, furigana

**Name part**:
One piece of a Japanese name with its own reading: one kanji, like 原 (げん), kanji that are only read together, like 今日 (きょう), a run of kana, or the particle の. An answer may write each part as it is written or as its reading.
_Avoid_: segment, token, word, unit

**Name list**:
All items with their Japanese names and readings.
_Avoid_: word list, dictionary, vocabulary

**Reading clip**:
The sound file that says an item's reading, played after a pass and from the word list. It is made ahead of time with VOICEVOX and ships with the mod.
_Avoid_: voice line, audio, TTS, pronunciation

### Spaced repetition

**Level**:
A number for how well the player knows an item, from 0 (never unlocked) up to the top level. A higher level gives a longer interval.
_Avoid_: SRS level, stage, rank

**Interval**:
How long an item stays unlocked after a pass, set by its level.
_Avoid_: cooldown, time until locked again

**Mastered**:
An item that has reached the top level. It stays unlocked forever.
_Avoid_: finished, burned, learned

**Learning**:
An item from level 1 up to, but not including, mastered, which the word list's Learning tab lists. Not the same as a lesson, which is at level 0.
_Avoid_: in progress, under review

**Urgency**:
The default order of the Learning tab: unlocked items first, least time left first, then locked items, longest locked first. The other order is by level.
_Avoid_: priority, due order

**Leveled**:
The state of an item that has a level, from level 0 up to mastered. Every item starts leveled, and known and ignored items are not.
_Avoid_: learning, active, in review

**SRS state**:
Everything tracked about one item for one player: whether it is leveled, known or ignored, and for a leveled item its level, when it locks next, whether it was demoted, its wrong attempts, and any ban.
_Avoid_: card, item progress

**Progress**:
All SRS states for one player. Their game keeps it on their computer and sends it to every world they join.
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
The screen that shows learning, known, ignored and mastered items in separate tabs, with Revert for known, ignored and mastered items. See [Word list](design/word-list.md).
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

**Ingredient**:
An item a recipe uses, in an input slot of a menu that makes its result from input slots, like the crafting grid. A fail on a crafted item removes ingredients instead of the item.
_Avoid_: input, material, component

**Recipe**:
What one craft of a result uses: how many of each ingredient, noted from the input slots when a review of the result starts.
_Avoid_: formula, pattern

### Tsukumogami

**Tsukumogami**:
The mob that follows a player day and night while their overdue count is at the threshold or above, and leaves when they review. Each player has at most one, and it follows only that player, its owner. See [Tsukumogami](design/tsukumogami.md).
_Avoid_: yokai (a category of creatures, not this mob), ghost, boss, follower

**Overdue**:
The state of an item that is locked at level 1 or higher, banned or not. Level 0 items are never overdue.
_Avoid_: due, late, expired

**Overdue count**:
The number of different overdue items a player carries, in the inventory, the hotbar, the offhand and on the cursor. Stack size doesn't matter.
_Avoid_: backlog, debt, due count

**Review freeze**:
How long the tsukumogami stands still and watches its owner during one review, a server setting.
_Avoid_: pause, grace period

### Settings

**Server settings**:
The settings that the server decides and every player on it shares: the levels and their intervals and demotions, the ban length, `percentageFactor`, whether dropping counts as an interaction, and the tsukumogami's threshold and review freeze. In singleplayer, the built-in server's.
_Avoid_: config, options, game rules

**Client settings**:
The settings each player chooses on their own computer, like clickable buttons.
_Avoid_: preferences, local settings
