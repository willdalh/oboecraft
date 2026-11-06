package io.github.willdalh.oboecraft.fabric.lock;

import io.github.willdalh.oboecraft.core.lock.Interaction;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;

/**
 * A stack that an action would interact with, which the lock check decides.
 *
 * @param stack       the stack
 * @param interaction what the action does with it
 * @param location    where the stack is, so the answer menu can be opened for it
 */
public record CheckedStack(ItemStack stack, Interaction interaction, StackLocation location) {
    public CheckedStack {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(interaction, "interaction");
        Objects.requireNonNull(location, "location");
    }
}
