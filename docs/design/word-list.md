# Word list

Open the word list with the **あ** button in the survival or adventure inventory. The button has Minecraft's normal button look, is the size of the recipe book button and sits right of it, in the empty space between the crafting area and the main inventory. It moves with the inventory when the recipe book opens or closes, and hovering it shows "Word list" (単語リスト in Japanese). It is the only way to open the word list; until [willdalh/oboecraft#43](https://github.com/willdalh/oboecraft/issues/43) it was the J key.

The button is only there while the player has progress, which means the server runs the mod, so without it the inventory looks exactly like vanilla. If progress arrives while the inventory is open, the button appears without reopening it. The creative inventory has no button, so in creative and spectator mode the word list can't be opened. Locks only apply in survival and adventure, which is where the list matters. There is no setting to hide or move the button, since hiding it would leave the word list unreachable.

The inventory stays open behind the word list. Close (Esc by default) goes back to the inventory, and the inventory key (E by default) closes both, also when Close is bound to the same key. Items in the 2×2 crafting grid and on the cursor stay where they were. If progress goes away while the word list is open, it goes back to the inventory, which then has no button.

The word list does not pause the game. It has four tabs, Learning, Known, Ignored and Mastered (学習中, 既知, 無視 and 習得済み), and it opens on Learning. Each tab is one list that scrolls with the mouse wheel or its scrollbar, as wide as the tabs above it, which on a wide screen is at most 400 GUI pixels, so the columns stay close together. Switching tabs or the order goes back to the top of the list; the list stays where it is when progress changes or a row locks. Each item is in at most one tab. Each row shows the item's icon, then (in Japanese only) a speaker button, then the name, each in a column, so the buttons and names line up. The reading isn't shown, so the player recalls it from the name; the speaker button plays the item's [reading clip](display.md#reading-clip), stopping any clip that is playing, and the clip finishes even if the word list is closed. A name too wide for its column is cut short with an ellipsis. Items that only the game's data names, like the camp maps, have no icon. Items missing from the current name list retain their saved progress but are not listed in any tab. A future name-list migration can recover them.

## Learning tab

The Learning tab lists the items being learned: leveled items from level 1 up to, but not including, mastered. Since a fail never drops an item below level 1, an item joins the tab on its first pass and stays until it is mastered, marked known or ignored, or reverted. Items at level 0 are never listed.

- An **unlocked** row shows the icon, the Japanese speaker button if applicable, the name, the lock timer and the level, with the level at the right end. The timer has the same label and draining bar as the item tooltip, see [Display](display.md#lock-timer), and turns amber in the last 10%.
- A **locked** row shows the item's icon at full color, in Japanese an empty space where the speaker button would be, `???` in grey and the level, since the clip would say the answer. A banned item also shows how long its ban has left in the timer's place, like "Banned: 3m 20s" (禁止中: 3分20秒).

The timers and bans count down while the list is open. When an item locks, its row changes to locked and moves to where it belongs in the order at once.

The button below the list switches the order, starting with **Urgency**. The choice is kept until the game closes, and isn't saved.

- **Urgency** (緊急度): unlocked items first, least time left first, so the item that locks soonest is at the top. Then locked items, longest locked first, with banned items among them.
- **Level** (レベル): lowest level first, with the urgency order breaking ties.

Items that lock at the same time, or have the same level and lock time, are ordered by name in the learning language’s alphabetical order.

Clicking a row on the Learning tab does nothing: items can't be reviewed or reverted from it yet. That is planned in [willdalh/oboecraft#36](https://github.com/willdalh/oboecraft/issues/36).

## Known, Ignored and Mastered tabs

These tabs are ordered by name in the learning language’s alphabetical order. Right-click a row, then choose **Revert to level 0**. The server checks that the item is currently known, ignored or mastered for that player and that no review is going. Revert resets it to level 0, with no timer, and the row disappears when the server confirms the state. It never restores previously dropped stacks.

Already known and Ignore act on the item in the active review, at any reviewable level. Ignoring drops all matching loose stacks from the main inventory, hotbar, offhand, cursor and personal 2×2 crafting grid onto the ground. Worn armor and stacks already in containers stay in use; nothing is removed from containers. Ignored items cannot be picked up (including arrows and tridents), used, crafted or taken from containers while locks are active. Dropping remains allowed. Creative and spectator mode still bypass locks.

The word list uses only the active learning language’s entries and progress. German and Austrian German sort ä with a; Bokmål sorts æ, ø and å after z. Japanese keeps its existing order. Plain-name languages have no speaker column or reading clips.
