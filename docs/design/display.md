# Display

The client displays the player's [synced progress](time.md#the-client). It never changes progress while drawing. Names, the lock timer and the slot marker were added in milestone 7, the notification in milestone 10, and the reading clip in [willdalh/oboecraft#25](https://github.com/willdalh/oboecraft/issues/25).

## Names

A locked item's name is `???` in its hover tooltip and in the selected-item label above the hotbar. This includes custom names and the names of locked contents listed in another item's tooltip or shown as a bundle's selected item. The name keeps its usual color and style, and the tooltip keeps its other information, like enchantments, lore, potion effects and advanced details.

Changing game language in-world changes unlocked names immediately, while review answers stay in the learning language selected at join. Unlocked items show their normal Minecraft name in the player's selected language. Names elsewhere, such as advancements, death messages and search indexes, are unchanged. This is a learning aid, not a way to keep the answers secret from a modified client.

The displays use the same conditions as the [lock check](lock-check.md): they are off in creative and spectator mode, before progress arrives for the current connection, and on servers without the mod. Stacks outside the name list are unchanged.

## Lock timer

Hovering over an unlocked item with an interval shows a lock timer below the normal tooltip content: a time label and a bar. The label says how long until it locks, rounded up to whole seconds, in days, hours, minutes and seconds. Only the two largest nonzero units are shown, omitting smaller units. The label and separator are translated: English uses a space (`1m 30s`), Japanese joins the units (`1分30秒`).

The bar shows the remaining fraction of the item's level interval. It starts full, drains as the estimated server time advances, and disappears when the item locks. It is clamped at full if the server clock moves backwards or settings shorten an interval. Mastered items have no timer. An item's own tooltip image, such as a bundle preview, stays intact above the timer. A tooltip hidden by Minecraft's tooltip display component stays hidden.

## Slot marker

During the final 10% of the interval, including the boundary, the bar turns amber and a small amber square with a dark border appears at the top left of the item's slot. It leaves the stack count and durability bar clear. The marker appears wherever Minecraft draws item decorations, including inventory and container slots, the hotbar and offhand.

The marker disappears when the item locks; its name is then hidden instead. Locked, mastered and untracked items have no marker. With the default level 1 interval of ten minutes, the marker starts with one minute left.

## Notification

After a pass, the item's name in the learning language is shown at the top of the screen, so the player sees its spelling. For Japanese this also works when they answered in kana or romaji. It is written as in the name list, with kanji where the name has kanji, like オークの原木. There is no reading, icon, box or background: only the name as white text with Minecraft's usual text shadow, centered near the top edge at twice the normal text size. A name too wide for the screen is drawn smaller, so it always fits with a small margin. When boss bars are shown, the name goes below the last one, so boss health is never covered.

The notification is shown for 3 seconds of real time and fades out during the last second, also while the game is paused or ticks at another rate. A new pass replaces it at once. It is drawn on top of the HUD and of any open screen, so after a review from the inventory or a container it isn't dimmed by the screen's dark background. It is hidden with the HUD (F1), and while an answer menu is open, so the next review's menu never shows the last item's name. It is cleared when the player leaves the world or server.

The server sends the name with the pass, and the client shows it even if the player closed the answer menu before the reply came, since the item was passed.

Only a pass shows it. After a fail, the answer menu shows the name instead, and in Japanese its reading once the player asks for it, see [Reviews](reviews.md#outcomes).

## Reading clip

For Japanese only, with the notification after a pass and after a fail, the item's reading clip plays: the reading of the Japanese name, said by a voice, so the player hears how the name is said. The clips are made ahead of time with VOICEVOX, in the voice 玄野武宏, from the reviewed readings in the name list, and ship with the mod as one sound file per item. So they work offline and sound the same for everyone. The [word list](word-list.md) plays them too, from a button on each row. See [Reading clip tool](../architecture/reading-clip-tool.md).

The clip plays in Minecraft's Voice/Speech sound category, so the player sets its volume, or turns it off, under Options → Music & Sounds. It isn't placed in the world, so it sounds the same wherever the player looks, and it shows the subtitle "Japanese name is said" when subtitles are on. A new clip, from a pass, a fail or the word list, stops the clip that is playing. Leaving the world or server stops it.

It plays after every pass and every fail, also when the answer menu was closed before the result arrived, so a failed item's reading is heard as it is punished. Show answer after a fail doesn't play it again, and a lesson's shown answer plays no clip. An item without a clip, which only happens when the name list changed and the tool wasn't run again, passes and fails without a sound.

## Later milestones

Known and ignored items show their normal names, without timers or near-lock markers. A banned item is locked, so its name stays hidden during the ban. Since milestone 12, the displays use the server settings the server sent, the same ones as the client's lock check, so the timer uses the server's intervals.
