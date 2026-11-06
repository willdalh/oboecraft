# Punishment

A [fail](reviews.md#attempts) is punished: part of the item is removed from the current view, and the item is demoted and banned, see [Fail](srs-state.md#fail). Lessons, at level 0, are never punished. The server carries out every punishment, and the client only sees the result.

## The removal rate

The removal rate depends on the level `L` the item had before the fail demoted it:

```
removal rate = 50% - (L - 1) × percentageFactor%, but never below 0%
```

`percentageFactor` is a [setting](settings.md), in whole percentage points. With the default of 5, the rate is 50% at level 1 and 15% at level 8. The base rate of 50% is a fixed rule.

Every removed count is rounded up, so a fail at level 1 with one plank removes it. The counts are worked out in whole numbers, so 3/5 of 50% of 50 diamonds is exactly 15, never 16.

## The current view

A punishment only counts and removes stacks in the current view, which depends on the review's [trigger](reviews.md#the-trigger):

| Trigger | Current view |
|---|---|
| Inventory | The inventory, the offhand, the inventory's crafting grid and the carried stack |
| Container | The inventory, the offhand, the carried stack and the open container's slots, including a furnace's output |
| Crafting | The inventory, the offhand, the carried stack and the menu's input slots |

Worn armor is in use, so it is never part of the current view, and neither is a mount's saddle or armor. Results aren't part of it either, like the crafting grid's result or a trade's. Items inside a closed shulker box or bundle are part of that stack, not stacks of their own, so they aren't counted.

The menu's own slots are taken from first, in their order in the menu, then the inventory in slot order, and the carried stack last.

## What is removed

With an inventory or container trigger, the reviewed item is removed: its removal rate of everything of the item in the current view. 50 planks at level 1 lose 25.

With a crafting trigger, the recipe's ingredients are removed instead. When the review starts, the server notes the recipe from the menu's input slots: each stack in an input slot is one use of its item, as a crafting grid takes one item from each slot per craft. On a fail, each ingredient loses its part of the recipe times the removal rate, from everything of it in the current view, whatever is in the input slots by then. A diamond pickaxe is 3 diamonds and 2 sticks, so a fail at level 1 with 50 diamonds and 100 sticks removes 3/5 × 50% of the diamonds and 2/5 × 50% of the sticks: 15 diamonds and 20 sticks.

Input slots whose stacks aren't items, like another mod's, count towards the recipe's total, so each ingredient's part stays the same, but nothing is removed of them.

## Protected items

The dragon egg, every shulker box and every bundle are protected: a punishment never removes them. They lock like other items, and failing still demotes and bans them. As a crafting ingredient, a protected item is kept, and the other ingredients lose only their own part of the recipe, as if it had been removed too. Which items are protected is a fixed list in the core, see [Items](items.md#protected-items).

## Bans

A fail bans the item for the ban length, 5 minutes by default. While banned, the item can't be reviewed: the [lock check](lock-check.md) decides **banned** for its interactions, and the player is told above the hotbar how long the ban has left, without an answer menu. Right-clicking it in the inventory shows the same message. The ban ends on its own, and then the item can be reviewed again.

## What is built when

Milestone 9 builds all of this.
