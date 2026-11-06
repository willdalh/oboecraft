package io.github.willdalh.oboecraft.fabric.lock;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.lock.Interaction;
import io.github.willdalh.oboecraft.core.lock.LockCheck;
import io.github.willdalh.oboecraft.core.lock.LockDecision;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Drops loose inventory stacks, leaving worn armor and container contents in place. */
public final class IgnoredItems {
    private IgnoredItems() {}

    public static boolean mayPickUp(ServerPlayer player, ItemStack stack) {
        var progress = OboecraftMod.serverProgress();
        return OboecraftMod.itemMapping().entryOf(stack).map(entry -> LockCheck.check(
                progress.state(player, entry.id()), Interaction.PICK_UP, progress.now(),
                Locks.active(progress, player), OboecraftMod.settings()) == LockDecision.ALLOW).orElse(true);
    }

    public static void drop(ServerPlayer player, ItemId item) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) dropSlot(player, item, slot);
        dropSlot(player, item, Inventory.SLOT_OFFHAND);
        ItemStack carried = player.containerMenu.getCarried();
        if (matches(carried, item) && player.drop(carried.copy(), false, Prediction.SERVER_ONLY) != null) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.sendAllDataToRemote();
    }

    private static void dropSlot(ServerPlayer player, ItemId item, int slot) {
        ItemStack stack = player.getInventory().getItem(slot);
        if (matches(stack, item) && player.drop(stack.copy(), false, Prediction.SERVER_ONLY) != null) {
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
    }

    private static boolean matches(ItemStack stack, ItemId item) {
        return OboecraftMod.itemMapping().entryOf(stack).map(entry -> entry.id().equals(item)).orElse(false);
    }
}
