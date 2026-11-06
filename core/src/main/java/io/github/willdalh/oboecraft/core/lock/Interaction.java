package io.github.willdalh.oboecraft.core.lock;

/** What the player tries to do with a stack, which the lock check is told since its rules depend on it. */
public enum Interaction {
    /** Placing a block, or using an item on a block, like a bucket or flint and steel. */
    PLACE,
    /** Eating, drinking, throwing, shooting, using an item on an entity, and other use. */
    USE,
    /** Putting on armor or another wearable item. */
    EQUIP,
    /** Hitting a mob or player with a weapon. */
    ATTACK,
    /** Breaking a block with a tool. */
    MINE,
    /** An arrow or firework used by a bow or crossbow. */
    AMMO,
    /** Putting the stack into the input of a menu that makes its result from input slots, like a crafting grid. */
    INGREDIENT,
    /** Taking the result of a menu that makes it from input slots, like the crafting grid or a stonecutter. */
    CRAFT,
    /** Putting the stack into a fuel slot. */
    FUEL,
    /** Giving or taking the stack in a trade. */
    TRADE,
    /** Moving the stack from the inventory into an open container. */
    PUT_IN_CONTAINER,
    /** Moving the stack out of an open container or a menu's slots. */
    TAKE_FROM_CONTAINER,
    /** Picking a stack up, allowed even while locked but never while ignored. */
    PICK_UP,
    /** Throwing the stack out of the inventory. Only an interaction when the drop setting is on. */
    DROP
}
