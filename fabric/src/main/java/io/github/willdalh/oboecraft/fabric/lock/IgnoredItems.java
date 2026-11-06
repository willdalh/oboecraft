package io.github.willdalh.oboecraft.fabric.lock;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Drops loose inventory and personal crafting stacks, leaving worn armor and external containers in place. */
public final class IgnoredItems {
    private IgnoredItems() {}

    public static void drop(ServerPlayer player, ItemId item) {
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) dropSlot(player, item, player.getInventory(), slot);
        dropSlot(player, item, player.getInventory(), Inventory.SLOT_OFFHAND);
        var crafting = player.inventoryMenu.getCraftSlots();
        for (int slot = 0; slot < crafting.getContainerSize(); slot++) dropSlot(player, item, crafting, slot);
        ItemStack carried = player.containerMenu.getCarried();
        if (matches(carried, item) && player.drop(carried.copy(), false, Prediction.SERVER_ONLY) != null) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.sendAllDataToRemote();
    }

    private static void dropSlot(ServerPlayer player, ItemId item, Container container, int slot) {
        ItemStack stack = container.getItem(slot);
        if (matches(stack, item) && player.drop(stack.copy(), false, Prediction.SERVER_ONLY) != null) {
            container.setItem(slot, ItemStack.EMPTY);
        }
    }

    private static boolean matches(ItemStack stack, ItemId item) {
        return OboecraftMod.itemMapping().entryOf(stack).map(entry -> entry.id().equals(item)).orElse(false);
    }
}
