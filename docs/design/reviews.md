# Reviews

A review is one round in the answer menu for a locked item. It ends in a pass, a fail, or is cancelled when the player closes the menu. The server runs every review. The client only shows the answer menu and sends what the player does.

## Starting a review

A review starts in one of two ways:

- The [lock check](lock-check.md) decides **review** for an interaction.
- The player right-clicks a locked item in the inventory.

Either way, the server starts the review only if:

- the stack is an item, and it is locked and leveled,
- the item isn't banned,
- the stack is in the player's current view, or in the result slot of the open menu, and
- the player has no other review going.

The client says where the stack is: a slot of the inventory, a slot of the open menu, or the stack the player carries in it. It never says which item it is. The server reads the stack from its own state.

A player has at most one review at a time. The client only asks to start a review when it has no answer menu open, so if a review is still going on the server, the player has left it, and the new review replaces it. Reviewing an item that isn't locked, to top up its timer early, isn't possible.

If the server refuses, it tells the client why (not locked, banned, or the stack is gone), and the client closes the answer menu or never opens it. A refusal because the item isn't locked also sends the item's state and the server's time, so the client's next check allows the action. See [Time](time.md#the-client).

## The trigger

The server remembers the review's trigger: what the player was doing when the review started. Punishment needs it to know the current view and what to remove.

The server works the trigger out itself, from the menu the player has open on the server and the slot or hand the stack is in. The client never says what the trigger is, since a modified client could then pick the trigger that costs the least. The client only says where the stack is, and the server starts a review there only if an action with that stack was stopped: either the server stopped it itself and asked for the answer menu there, or the lock check, asked again, stops the stack there. A stack the lock check allows, for example in creative mode, is never reviewed.

| Trigger | Started by | Current view | Removed on a fail |
|---|---|---|---|
| Inventory | A stack in a hand or the inventory, with no other menu open | The inventory | The reviewed item |
| Container | A stack in an open container, or in the inventory while a container is open, including a furnace's output | The inventory and the open container | The reviewed item |
| Crafting | The result slot of a menu that makes its result from input slots, like the crafting grid, a stonecutter or a smithing table | The inventory and the menu's input slots | The recipe's ingredients |

The answer menu opens on top of the open container's screen and goes back to it when the review ends, see [Fabric adapter](../architecture/fabric.md#the-answer-menu). The container stays open on the server for the whole review, so the current view is still there if the review fails. If the container closes on the server, for example because the player walked away from the chest, a review with a container or crafting trigger is cancelled. A review with an inventory trigger needs no menu, so closing one doesn't cancel it.

For a crafting trigger, the server notes the recipe's ingredients and how many of each it uses when the review starts. On a fail, it removes from those ingredients, whatever is in the input slots by then. Milestone 9, which removes them, notes them.

While a review is going, the server refuses every click in the player's open menu, the recipe book, choosing a trade, a menu's buttons, dropping and swapping hands, so the current view can't be rearranged between the start and a fail. A normal client can't click anyway, since the answer menu covers the screen.

The worn armor slots are never part of the current view.

## Attempts

A review has up to two attempts. An attempt is one submitted answer:

- A correct answer is a **pass**.
- A wrong first answer leaves one attempt.
- A wrong second answer is a **fail**.

A wrong attempt is saved in the item's [SRS state](srs-state.md), so it isn't lost when the player closes the menu. Closing the menu after a wrong attempt and opening it again gives one attempt, not two. Otherwise closing and reopening the menu would give unlimited attempts without punishment. Wrong attempts are cleared by a pass or a fail.

Pressing Enter with an empty typing bar does nothing and isn't an attempt, and neither does text with no letter or digit, like a stray `。`, since [answer checking](answers.md#ignored-differences) ignores everything else. Neither does the Enter that confirms the keyboard's kanji conversion: while the conversion has text that isn't confirmed, every key belongs to the conversion, so Esc doesn't close the menu either.

The server checks the answer, with the rules in [Answers](answers.md). The client sends the typed text and gets back pass, wrong with one attempt left, or fail.

## Actions

| Action | Default key | Result |
|---|---|---|
| Answer | Enter | An attempt |
| Don't know | Delete | A fail at once |
| Already known | Ctrl+K | The item becomes known and is unlocked forever, and the review ends |
| Ignore | Ctrl+I | The item becomes ignored, and the review ends |
| Show answer | Ctrl+S | Lessons only: shows the Japanese name |
| Close | Esc | The review is cancelled. Nothing is punished and the item stays locked. |

All keys can be rebound in Minecraft's Controls screen.

## Lessons

A review of an item at level 0 is a lesson:

- Show answer shows the Japanese name. The player then types it to pass.
- A wrong answer, or Don't know, shows the Japanese name. It isn't a fail: nothing is removed, the item isn't banned and wrong attempts aren't counted.
- A pass puts the item at level 1.

## What the client is told

The answer menu shows the item's level. When a review starts, the server sends the level, whether it is a lesson, and how many attempts are left.

The Japanese name and reading are only sent to the client when it shows them: after a pass, after a fail, and in a lesson when the answer is shown.

## Outcomes

| Outcome | What happens |
|---|---|
| Pass | The item is unlocked, see [Pass](srs-state.md#pass). The reading is played and the Japanese name is shown at the top of the screen. |
| Wrong, one attempt left | The menu stays open with one attempt left. |
| Fail | Punishment: part of the current view is removed, the item is demoted and banned, see [Fail](srs-state.md#fail). Then the Japanese name is shown. |
| Lesson answer shown | The Japanese name is shown, and the lesson goes on. |
| Known or ignored | The item's SRS state changes, see [Kinds of state](srs-state.md#kinds-of-state). Ignored stacks are dropped from the inventory. |
| Cancelled | Nothing changes. Wrong attempts so far are kept. |

## What is built when

- Milestone 3b: starting a review from placing, Answer and Close, pass, and "wrong" with no attempt counted, so the review goes on. The trigger is always inventory, so it isn't stored yet. Enter and Esc are fixed keys, and the answer menu shows the item's icon without its name, its level and a hint line. After a pass, the Japanese name and reading are shown above the hotbar. Dying cancels the review.
- Milestone 4: full answer checking, see [Answers](answers.md).
- Milestone 6: starting a review from every interaction, the three triggers, the answer menu over an open menu's screen, refusing clicks in the open menu during a review, and cancelling a review whose menu closes.
- Milestone 8: lessons, Already known, Ignore, right-clicking in the inventory, and keys that can be rebound in the Controls screen.
- Milestone 9: two attempts, Don't know, fails and punishment, and noting a crafting trigger's ingredients.
- Milestone 10: the reading and the notification on a pass, in place of the text above the hotbar.
