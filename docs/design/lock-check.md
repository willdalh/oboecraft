# Lock check

Every time a player interacts with a stack, or picks one up, one lock check decides what happens. Every interaction, on both the server and the client, goes through it. Rules that change what is allowed, like creative mode, ignored items, bans and the drop setting, are each decided in this one place.

## Inputs

- The item, or none if the stack isn't in the name list. See [Items](items.md).
- The interaction, from the list [below](#what-the-player-tries-to-do), or picking up.
- The item's [SRS state](srs-state.md) for that player.
- The current time. See [Time](time.md).
- Whether locks are active for the player. They are active in survival and adventure mode, and not in creative or spectator mode. On the client, they are also only active once the server has sent the player's SRS states, see [Both sides](#both-sides).
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

The check is told which interaction the player is trying, since the rules depend on it:

| Kind | Examples |
|---|---|
| Place | Placing a block, or using an item on a block, like a bucket or flint and steel |
| Use | Eating, drinking, throwing, shooting, and other use in the air |
| Equip | Putting on armor or other wearable items |
| Attack | Hitting a mob or player with the stack held |
| Mine | Breaking a block with the stack held |
| Ammo | An arrow or firework used by a bow or crossbow |
| Ingredient | Putting the stack into a crafting grid or other recipe input |
| Craft | Taking the result of a menu that makes it from input slots, like the crafting grid, a stonecutter or a smithing table. A furnace's output is taken from a container instead. |
| Fuel | Putting the stack into a fuel slot |
| Trade | Giving or taking the stack in a trade |
| Put in container | Moving the stack from the inventory into an open container |
| Take from container | Moving the stack out of an open container into the inventory |
| Drop | Throwing the stack out of the inventory. It only counts as an interaction when the drop setting is on. |

Picking a stack up from the ground isn't an interaction, but the lock check decides it too, so ignored items are handled in the same place. Moving a stack within the inventory is never checked.

Milestone 3b checks only placing. Milestone 6 adds the rest, and may split or add kinds where the game needs it. A new kind only needs a new value here and, if it needs its own rule, a new rule above.

Placing is checked when the stack is used on the block, after the block itself had its chance to react. So right-clicking a chest or crafting table with a locked item in hand still opens it, and only the placing is stopped.

## What is built when

Milestone 3b builds the decisions Allow and Review, and rules 1, 2, 7 and 9. Milestone 6 adds rule 3 with the drop setting, milestone 8 adds rules 4 to 6 with Deny and picking up, and milestone 9 adds rule 8 with Banned.

## Opening the answer menu on purpose

Right-clicking a locked item in the inventory opens the answer menu without using the item. This is not an interaction, so it doesn't go through the lock check, but it uses the same rules to start the review: see [Starting a review](reviews.md#starting-a-review).

## Both sides

The check runs on both sides with the same rules.

- The client checks first, from the SRS states the server sent it, and stops the action at once. So a locked block is never placed and then taken back, and the answer menu opens without waiting for the server.
- The client's locks are only active once the server has sent the player's SRS states for this connection. Before that, and on a server without the mod, the client allows everything. Otherwise every item would count as level 0 and locked, and joining a vanilla server would stop every action and open answer menus nobody answers.
- The server checks too and has the final say. A modified client can't get past it.

Minecraft sends a placement to the server even when the client stopped it, so the server checks every placement again. When the server stops an action, it tells the client what to show: the answer menu, or later the ban message. The client ignores this while its answer menu is already open, which is the normal case. When the client allowed the action and the server didn't, for example because the item locked a moment before, the client opens the answer menu then, and the server sends the player's slots again so a stack the client already used up is put back.
