package io.github.willdalh.oboecraft.fabric.gametest;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Stacks that stand in for another mod's items. Every vanilla item is in the name list, so a test that needs a stack
 * that isn't takes a vanilla one and gives it an item name from a key the name list doesn't have, as another mod's item
 * would have.
 */
final class OtherModStacks {
    private OtherModStacks() {
    }

    /** A stack of the item that is named like another mod's item, so it isn't in the name list. */
    static ItemStack of(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.ITEM_NAME, Component.translatable("block.othermod.marble"));
        return stack;
    }
}
