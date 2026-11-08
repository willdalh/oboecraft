# Word list

Open the word list with the **あ** button in the survival or adventure inventory. The button has Minecraft's normal button look, is the size of the recipe book button and sits right of it, in the empty space between the crafting area and the main inventory. It moves with the inventory when the recipe book opens or closes, and hovering it shows "Word list" (単語リスト in Japanese). It is the only way to open the word list; until [willdalh/oboecraft#43](https://github.com/willdalh/oboecraft/issues/43) it was the J key.

The button is only there while the player has progress, which means the server runs the mod, so without it the inventory looks exactly like vanilla. If progress arrives while the inventory is open, the button appears without reopening it. The creative inventory has no button, so in creative and spectator mode the word list can't be opened. Locks only apply in survival and adventure, which is where the list matters. There is no setting to hide or move the button.

The inventory stays open behind the word list. Close (Esc by default) goes back to the inventory, and the inventory key (E by default) closes both. Items in the 2×2 crafting grid and on the cursor stay where they were. If progress goes away while the word list is open, it goes back to the inventory, which then has no button.

The word list does not pause the game. Known, ignored and mastered items have separate tabs, ordered by Japanese name, with pages for long lists. Each row shows the item's icon, then the name and a speaker button, left-justified from the left edge of the tabs. The reading isn't shown, so the player recalls it from the name; the speaker button plays the item's [reading clip](display.md#reading-clip), stopping any clip that is playing, and the clip finishes even if the word list is closed. A name too wide for the screen is cut short with an ellipsis, leaving room for the button. Items that only the game's data names, like the camp maps, have no icon.

Right-click a row, then choose **Revert to level 0**. The server checks that the item is currently known, ignored or mastered for that player and that no review is going. Revert resets it to level 0, with no timer, and the row disappears when the server confirms the state. It never restores previously dropped stacks.

Already known and Ignore act on the item in the active review, at any reviewable level. Ignoring drops all matching loose stacks from the main inventory, hotbar, offhand, cursor and personal 2×2 crafting grid onto the ground. Worn armor and stacks already in containers stay in use; nothing is removed from containers. Ignored items cannot be picked up (including arrows and tridents), used, crafted or taken from containers while locks are active. Dropping remains allowed. Creative and spectator mode still bypass locks.

Items missing from the current name list retain their saved progress but are not listed. A future name-list migration can recover them.
