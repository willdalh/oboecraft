# Lock check

Every time a player does something with a stack that the mod may stop, one lock check decides what happens. Every interaction, on both the server and the client, goes through it. Rules that change what is allowed, like creative mode, ignored items, bans and the drop setting, are each decided in this one place.

## Inputs

- The item, or none if the stack isn't in the name list. See [Items](items.md).
- What the player is trying to do, from the list [below](#what-the-player-tries-to-do).
- The item's [SRS state](srs-state.md) for that player.
- The current time. See [Time](time.md).
- Whether locks are active for the player. They are active in survival and adventure mode, and not in creative or spectator mode.
- The [settings](settings.md).

## Decisions

| Decision | What happens |
|---|---|
| Allow | The action goes ahead as normal. |
| Review | The action is stopped, and the answer menu opens for the item. |
| Banned | The action is stopped, and the player is told how long the ban has left. No answer menu opens. |
| Deny | The action is stopped, with no answer menu. Used for ignored items. |

## Rules

The rules are applied in this order, and the first that matches decides:

1. The stack isn't an item: **allow**.
2. Locks aren't active for the player: **allow**. The SRS state is kept for when they return to survival.
3. The player drops the stack and the drop setting is off: **allow**.
4. The item is ignored: **allow** dropping and **deny** everything else.
5. The item is known: **allow**.
6. The player picks the stack up: **allow**. Locked items can be picked up.
7. The item is unlocked: **allow**.
8. The item is banned: **banned**.
9. Otherwise the item is locked: **review**.

## What the player tries to do

The check is told what the player is trying to do, since the rules depend on it. These are the interactions, plus picking up, which isn't an interaction but is checked so that ignored items are handled here too:

| Kind | Examples |
|---|---|
| Place | Placing a block, or using an item on a block, like a bucket or flint and steel |
| Use | Eating, drinking, throwing, shooting, and other use in the air |
| Equip | Putting on armor or other wearable items |
| Attack | Hitting a mob or player with the stack held |
| Mine | Breaking a block with the stack held |
| Ammo | An arrow or firework used by a bow or crossbow |
| Ingredient | Putting the stack into a crafting grid or other recipe input |
| Craft | Taking the result of a recipe |
| Fuel | Putting the stack into a fuel slot |
| Trade | Giving or taking the stack in a trade |
| Put in container | Moving the stack from the inventory into an open container |
| Take from container | Moving the stack out of an open container into the inventory |
| Drop | Throwing the stack out of the inventory |
| Pick up | Picking the stack up from the ground |

Moving a stack within the inventory is never checked.

Milestone 3b checks only placing. Milestone 6 adds the rest, and may split or add kinds where the game needs it. A new kind only needs a new value here and, if it needs its own rule, a new rule above.

## Opening the answer menu on purpose

Right-clicking a locked item in the inventory opens the answer menu without using the item. This is not an interaction, so it doesn't go through the lock check, but it uses the same rules to start the review: see [Starting a review](reviews.md#starting-a-review).

## Both sides

The check runs on both sides with the same rules.

- The client checks first, from the SRS states the server sent it, and stops the action at once. So a locked block is never placed and then taken back, and the answer menu opens without waiting for the server.
- The server checks too and has the final say. A modified client can't get past it.

If the server stops an action that the client allowed, for example because the item locked a moment before, the server sends the up-to-date SRS state and tells the client what to show: the answer menu, the ban message or nothing.
