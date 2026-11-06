package io.github.willdalh.oboecraft.fabric.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The anvil only renames name tags. A fixed rule, not a setting. It goes by the stack's Minecraft item, not its name
 * list entry: an item name from a data pack or a command can make another item read as a name tag, or a name tag read
 * as something else, but neither changes what the stack is.
 */
public final class AnvilRenaming {
    private AnvilRenaming() {
    }

    /** Whether an anvil may rename the stack. */
    public static boolean mayRename(ItemStack stack) {
        return stack.is(Items.NAME_TAG);
    }
}
