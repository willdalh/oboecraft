# Lock check

Every time a player interacts with a stack, or picks one up, one lock check decides what happens. Every interaction, on both the server and the client, goes through it. Rules that change what is allowed, like creative mode, ignored items, bans and the drop setting, are each decided in this one place.

## Inputs

- The item, or none if the stack isn't in the name list. See [Items](items.md).
- The interaction, from the list [below](#what-the-player-tries-to-do), including picking up.
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
| Use | Eating, drinking, throwing, shooting, blocking with a shield, other use in the air, and using the item on an entity, like feeding an animal or putting a name tag on a mob |
| Equip | Putting on armor or other wearable items, by using them or by putting them into an armor slot. On a mount, like a horse, putting a saddle or armor into its slots. Taking armor off is free. |
| Attack | Hitting a mob or player with a weapon, including a stab with a spear |
| Mine | Breaking a block with a tool |
| Ammo | An arrow or firework that a bow or a crossbow that isn't loaded would shoot |
| Ingredient | Putting the stack into an input slot of a menu that makes its result from input slots: the crafting grid, the crafter, the anvil, the smithing table, the stonecutter, the loom, the cartography table and the grindstone. Also the ingredients the recipe book moves into the crafting grid. |
| Craft | Taking the result of such a menu. A furnace's output is taken from a container instead. |
| Fuel | Putting the stack into the fuel slot of a furnace, smoker, blast furnace or brewing stand |
| Trade | Putting the stack into a payment slot of a trade, the payment that choosing a trade moves there, and taking the trade's result |
| Put in container | Moving the stack from the inventory into any other slot of an open menu, like a chest, a furnace's input or an enchanting table |
| Take from container | Moving the stack out of a slot of an open menu into the inventory, other than a result. This includes taking a stack back out of a crafting grid or a furnace. |
| Pick up | Picking up an item entity, arrow or trident. Locked items are allowed; ignored items are denied. |
| Drop | Throwing the stack out of the inventory, with the drop key or from a menu. It only counts as an interaction when the drop setting is on. |

Picking up is represented by `Interaction.PICK_UP` in the shared lock check. Unlike use, it never starts a review, so a locked item can still be picked up. Moving a stack within the inventory is never checked, and neither is moving it between the inventory and the offhand.

A new kind only needs a new value here and, if it needs its own rule, a new rule above.

### Held stacks

Placing is checked when the stack is used on the block, after the block itself had its chance to react. So right-clicking a chest or crafting table with a locked item in hand still opens it, and only the placing is stopped.

Using a stack in the air is only checked for items whose use does something, like food, armor, bows and throwables. A block used in the air does nothing, so it isn't checked.

Using a stack on an entity is checked before the entity reacts, since the game doesn't tell apart an entity that uses the held item, like a cow being fed wheat, from one that doesn't, like a villager opening its trades. So right-clicking any entity with a locked item in hand opens the answer menu. To trade or ride with a locked item, the player holds something else.

Attacking only counts for a weapon and mining only for a tool, since only they change what a hit or breaking a block does. Hitting a mob while holding a locked block, or breaking dirt while holding a locked torch, isn't checked. A weapon or a tool is any item Minecraft gives the weapon or the tool component, like swords, axes, pickaxes, the mace and the trident.

A bow, or a crossbow that isn't loaded, checks the bow first and then the ammo it would shoot. A loaded crossbow only checks the crossbow: its ammo is already in use.

### Clicks in a menu

A click in a menu is checked before Minecraft carries it out, from the clicked slot, the kind of click and what the player carries, as the stacks the click would move. If the lock check stops any of them, the whole click is stopped. A click that both takes a stack and puts another, like swapping, checks both.

A shift-click on a stack in the inventory is checked where the menu's own code moves it: into a slot of the menu, it counts as putting it there, and within the inventory it is free.

A drag over several slots checks each slot as it is added, so a locked stack is spread only over slots that don't need a check. A double-click that gathers stacks also from a container's slots counts as taking from that container.

## What is built when

Milestone 3b builds the decisions Allow and Review, and rules 1, 2, 7 and 9, for placing. Milestone 6 adds the interaction as an input, rule 3 with the drop setting, and the check for every interaction. Milestone 8 adds rules 4 to 6 with Deny and picking up, and milestone 9 adds rule 8 with Banned.

## Opening the answer menu on purpose

Right-clicking a locked item in the inventory opens the answer menu without using the item. This is not an interaction, so it doesn't go through the lock check, but it uses the same rules to start the review: see [Starting a review](reviews.md#starting-a-review).

## Both sides

The check runs on both sides with the same rules.

- The client checks first, from the SRS states the server sent it, and stops the action at once. So a locked block is never placed and then taken back, and the answer menu opens without waiting for the server.
- The client's locks are only active once the server has sent the player's SRS states for this connection. Before that, and on a server without the mod, the client allows everything. Otherwise every item would count as level 0 and locked, and joining a vanilla server would stop every action and open answer menus nobody answers.
- The server checks too and has the final say. A modified client can't get past it.

Minecraft sends placing and using to the server even when the client stopped them, so the server checks every action again. When the server stops an action, it tells the client what to show, the answer menu or later the ban message, and where the stack is. The client ignores this while its answer menu is already open, which is the normal case. When the client allowed the action and the server didn't, for example because the item locked a moment before, the client opens the answer menu then. The server sends the stack's slot again, so a stack the client already used up is put back, and after a stopped click it sends the menu's real contents, which undoes the move the client predicted.

The recipe book and choosing a trade move stacks from the inventory into a menu, and only the server checks them. The recipe book leaves locked stacks out of what it counts as available, so it uses unlocked ones where it can and otherwise places nothing. A trade skips a locked payment. Either way the client is then told once to open the answer menu for a locked stack. The server always sends the stack's real place before it asks for the answer menu, so the client finds the stack even after predicting a move.
