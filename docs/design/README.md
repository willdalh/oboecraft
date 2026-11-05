# Confirmed gameplay rules

These are the agreed rules to implement. The repository has no playable mod yet. This document records later decisions that replace conflicting inventory and crafting rules in the frozen [original idea](../idea.md). The [implementation plan](../implementation-plan.md) describes the order of work.

## Reviews and attempts

- A review starts with two attempts. The first wrong answer leaves one attempt.
- After five minutes, attempts reset to two if the review has not already failed. A reset does not undo a fail or its punishment.
- A second wrong answer before the reset, or choosing Don't know, fails the review.
- The attempt reset is separate from the ban after a fail. Lessons at level 0 retain their existing no-punishment rule.

## Inventory and chest slots

Trying to pick up a locked item from an inventory or chest slot immediately opens its review, before dragging starts. This includes moving a locked item between slots within the player's own inventory.

On a pass, the item unlocks. The interrupted action is not performed automatically: the player repeats the intended action. For example, after passing a review opened by clicking a locked diamond in a chest, the player clicks the diamond again to pick it up.

A lock does not prevent picking up an item from the ground. It enters the inventory without a review and retains its SRS state. The separate restriction on picking up ignored items still applies.

## Crafting

- Locked ingredients trigger their own reviews when the player tries to move them, including into the crafting grid.
- A locked crafting result requires its own review when the player tries to take it, even if the ingredients are already unlocked.
- After each pass, the player repeats the intended movement or takes the result again. Passing does not automatically move ingredients or complete crafting.

For example, moving a locked diamond into a recipe first requires a diamond review. Taking a locked diamond sword from the result slot then requires a separate diamond sword review.

## Punishment and the current view

On a fail, removal includes matching items in both the player's inventory and the open chest. Opening the answer menu does not limit removal to the inventory alone. For example, failing a diamond review opened from a chest counts the diamonds in that chest and in the inventory together.

The existing removal rate, rounding, protected-item and ban rules still apply. On a fail for a crafting result, removal applies to the recipe's ingredients across the inventory and crafting grid, as described in the original idea.

## Item variants

Normal, damaged and enchanted diamond swords are the same item for learning purposes and share one SRS state per player. Damage and enchantments do not create separate reviews or levels.

## Examples for early playtesting

| Scenario | Expected behavior |
|---|---|
| Click a locked inventory item to move it to another slot | Open its review before dragging starts. |
| Pass the review opened from a chest slot | Unlock the item; leave it in place until the player picks it up again. |
| Walk over a locked ground item | Pick it up without opening a review. |
| Move a locked ingredient, then take a locked crafting result | Review the ingredient and the result separately, repeating each action after passing. |
| Submit one wrong answer | Leave one attempt; restore two after five minutes if the review has not failed. |
| Submit a second wrong answer before the reset | Fail once and apply punishment; the attempt reset cannot undo it. |
| Fail a diamond review with diamonds in the inventory and open chest | Include both locations in removal. |
| Unlock a normal diamond sword | Apply the same SRS state to damaged and enchanted diamond swords. |
