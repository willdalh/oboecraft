# Display

The client displays the player's [synced progress](time.md#the-client). It never changes progress while drawing. This was added in milestone 7.

## Names

A locked item's name is `???` in its hover tooltip and in the selected-item label above the hotbar. This includes custom names. The name keeps its usual color and style, and the tooltip keeps its other information, like enchantments, lore, potion effects and advanced details.

Unlocked items show their normal Minecraft name in the player's selected language. Names elsewhere, such as advancements and death messages, are unchanged. This is a learning aid, not a way to keep the Japanese names secret from a modified client.

The displays use the same conditions as the [lock check](lock-check.md): they are off in creative and spectator mode, before progress arrives for the current connection, and on servers without the mod. Stacks outside the name list are unchanged.

## Lock timer

Hovering over an unlocked item with an interval shows a lock timer below the name: a time label and a bar. The label says how long until it locks, rounded up to whole seconds, in days, hours, minutes and seconds. Zero units are omitted. The label is translated in English and Japanese.

The bar shows the remaining fraction of the item's level interval. It starts full, drains as the estimated server time advances, and disappears when the item locks. It is clamped at full if the server clock moves backwards or settings shorten an interval. Mastered items have no timer. An item's own tooltip image, such as a bundle preview, stays below the timer. A tooltip hidden by Minecraft's tooltip display component stays hidden.

## Slot marker

During the final 10% of the interval, including the boundary, the bar turns amber and a small amber square with a dark border appears at the top left of the item's slot. It leaves the stack count and durability bar clear. The marker appears wherever Minecraft draws item decorations, including inventory and container slots, the hotbar and offhand.

The marker disappears when the item locks; its name is then hidden instead. Locked, mastered and untracked items have no marker. With the default level 1 interval of ten minutes, the marker starts with one minute left.

## Later milestones

Known and ignored states arrive in milestone 8 and bans in milestone 9. Known items need no timer, and banned items keep their names hidden while locked. Milestone 12 must supply the synced settings to the display as well as to the lock check, so the timer uses the server's intervals.
