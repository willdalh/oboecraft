# Display

The client displays the player's [synced progress](time.md#the-client). It never changes progress while drawing. Names, the lock timer and the slot marker were added in milestone 7, and the notification in milestone 10.

## Names

A locked item's name is `???` in its hover tooltip and in the selected-item label above the hotbar. This includes custom names and the names of locked contents listed in another item's tooltip or shown as a bundle's selected item. The name keeps its usual color and style, and the tooltip keeps its other information, like enchantments, lore, potion effects and advanced details.

Unlocked items show their normal Minecraft name in the player's selected language. Names elsewhere, such as advancements, death messages and search indexes, are unchanged. This is a learning aid, not a way to keep the Japanese names secret from a modified client.

The displays use the same conditions as the [lock check](lock-check.md): they are off in creative and spectator mode, before progress arrives for the current connection, and on servers without the mod. Stacks outside the name list are unchanged.

## Lock timer

Hovering over an unlocked item with an interval shows a lock timer below the normal tooltip content: a time label and a bar. The label says how long until it locks, rounded up to whole seconds, in days, hours, minutes and seconds. Only the two largest nonzero units are shown, omitting smaller units. The label and separator are translated: English uses a space (`1m 30s`), Japanese joins the units (`1分30秒`).

The bar shows the remaining fraction of the item's level interval. It starts full, drains as the estimated server time advances, and disappears when the item locks. It is clamped at full if the server clock moves backwards or settings shorten an interval. Mastered items have no timer. An item's own tooltip image, such as a bundle preview, stays intact above the timer. A tooltip hidden by Minecraft's tooltip display component stays hidden.

## Slot marker

During the final 10% of the interval, including the boundary, the bar turns amber and a small amber square with a dark border appears at the top left of the item's slot. It leaves the stack count and durability bar clear. The marker appears wherever Minecraft draws item decorations, including inventory and container slots, the hotbar and offhand.

The marker disappears when the item locks; its name is then hidden instead. Locked, mastered and untracked items have no marker. With the default level 1 interval of ten minutes, the marker starts with one minute left.

## Notification

After a pass, the item's Japanese name is shown at the top of the screen, so the player sees how it is written even when they answered in kana or romaji. It is written as in the name list, with kanji where the name has kanji, like オークの原木. There is no reading, icon, box or background: only the name as white text with Minecraft's usual text shadow, centered near the top edge at twice the normal text size. A name too wide for the screen is drawn smaller, so it always fits with a small margin. When boss bars are shown, the name goes below the last one, so boss health is never covered.

The notification is shown for 3 seconds of real time and fades out during the last second, also while the game is paused or ticks at another rate. A new pass replaces it at once. It is drawn on top of the HUD and of any open screen, so after a review from the inventory or a container it isn't dimmed by the screen's dark background. It is hidden with the HUD (F1), and while an answer menu is open, so the next review's menu never shows the last item's name. It is cleared when the player leaves the world or server.

The server sends the name with the pass, and the client shows it even if the player closed the answer menu before the reply came, since the item was passed.

Only a pass shows it. After a fail, the answer menu shows the Japanese name and reading instead, see [Reviews](reviews.md#outcomes).

## Later milestones

Known and ignored items show their normal names, without timers or near-lock markers. A banned item is locked, so its name stays hidden during the ban. Milestone 12 supplies synced settings to the shared lock service, which also serves displays, so the timer uses the server's intervals.
