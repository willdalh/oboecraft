# Word list

Open the word list with the **あ** button in the survival or adventure inventory. The button has Minecraft's normal button look, is the size of the recipe book button and sits right of it, in the empty space between the crafting area and the main inventory. It moves with the inventory when the recipe book opens or closes, and hovering it shows "Word list" (単語リスト in Japanese). It is the only way to open the word list; until [willdalh/oboecraft#43](https://github.com/willdalh/oboecraft/issues/43) it was the J key.

The button is only there while the player has progress, which means the server runs the mod, so without it the inventory looks exactly like vanilla. If progress arrives while the inventory is open, the button appears without reopening it. The creative inventory has no button, so in creative and spectator mode the word list can't be opened. Locks only apply in survival and adventure, which is where the list matters. There is no setting to hide or move the button.

The inventory stays open behind the word list. Close (Esc by default) goes back to the inventory, and the inventory key (E by default) closes both, also when Close is bound to the same key. Items in the 2×2 crafting grid and on the cursor stay where they were. If progress goes away while the word list is open, it goes back to the inventory, which then has no button.

The word list does not pause the game. It has four tabs, Learning, Known, Ignored and Mastered (学習中, 既知, 無視 and 習得済み), each with pages for long lists, and it opens on Learning. Each item is in at most one tab. Each row shows the item's icon, then the name and a speaker button, left-justified from the left edge of the tabs. The reading isn't shown, so the player recalls it from the name; the speaker button plays the item's [reading clip](display.md#reading-clip), stopping any clip that is playing, and the clip finishes even if the word list is closed. A name too wide for the screen is cut short with an ellipsis, leaving room for the button. Items that only the game's data names, like the camp maps, have no icon. Items missing from the current name list retain their saved progress but are not listed in any tab. A future name-list migration can recover them.

## Learning tab

The Learning tab lists the items being learned: leveled items from level 1 up to, but not including, mastered. Since a fail never drops an item below level 1, an item joins the tab on its first pass and stays until it is mastered, marked known or ignored, or reverted. Items at level 0 are never listed.

- A **locked** row shows the item's icon at full color, `???` in grey and the level, with no speaker button, since the clip would say the answer. A banned item also shows how long its ban has left, like "Banned: 3m 20s" (禁止中: 3分20秒).
- An **unlocked** row shows the icon, the name, the speaker button, the level and the lock timer. The timer has the same label and draining bar as the item tooltip, see [Display](display.md#lock-timer), and turns amber in the last 10%.

The timers and bans count down while the list is open, and a row changes to locked as soon as its item locks.

The button below the list switches the order, starting with **Urgency**. The choice is kept until the game closes, and isn't saved.

- **Urgency** (緊急度): locked items first, longest locked first, with banned items among them. Then unlocked items, least time left first. This is the order of the times the items lock, so it doesn't change as time passes.
- **Level** (レベル): lowest level first, with the urgency order breaking ties.

Items that lock at the same time, or have the same level and lock time, are ordered by Japanese name.

Clicking a row on the Learning tab does nothing: items can't be reviewed or reverted from it yet. That is planned in [willdalh/oboecraft#36](https://github.com/willdalh/oboecraft/issues/36).

## Known, Ignored and Mastered tabs

These tabs are ordered by Japanese name. Right-click a row, then choose **Revert to level 0**. The server checks that the item is currently known, ignored or mastered for that player and that no review is going. Revert resets it to level 0, with no timer, and the row disappears when the server confirms the state. It never restores previously dropped stacks.

Already known and Ignore act on the item in the active review, at any reviewable level. Ignoring drops all matching loose stacks from the main inventory, hotbar, offhand, cursor and personal 2×2 crafting grid onto the ground. Worn armor and stacks already in containers stay in use; nothing is removed from containers. Ignored items cannot be picked up (including arrows and tridents), used, crafted or taken from containers while locks are active. Dropping remains allowed. Creative and spectator mode still bypass locks.
