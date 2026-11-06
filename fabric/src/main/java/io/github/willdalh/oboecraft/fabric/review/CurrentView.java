package io.github.willdalh.oboecraft.fabric.review;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.fabric.item.ItemMapping;
import io.github.willdalh.oboecraft.fabric.lock.MenuSlots;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The stacks a punishment counts and removes from: the player's inventory, the stack they carry, and the slots of the
 * menu the review's trigger needs, other than results. With only the inventory open, that menu's slots are the
 * inventory's crafting grid. Worn armor is in use, so it is never part of it. Only used on the server thread, while
 * the review's menu is still open.
 */
final class CurrentView {
    /** One slot of a container in the current view. */
    private record ViewSlot(Container container, int index) {
        ItemStack stack() {
            return container.getItem(index);
        }
    }

    private final ServerPlayer player;
    private final ItemMapping itemMapping;
    private final List<ViewSlot> slots;

    private CurrentView(ServerPlayer player, ItemMapping itemMapping, List<ViewSlot> slots) {
        this.player = player;
        this.itemMapping = itemMapping;
        this.slots = slots;
    }

    /**
     * The current view of the player's review with this trigger. The menu's own slots come first, so a punishment
     * takes from the container or crafting grid the player was using before their inventory.
     */
    static CurrentView of(ServerPlayer player, Trigger trigger, ItemMapping itemMapping) {
        Objects.requireNonNull(itemMapping, "itemMapping");
        AbstractContainerMenu menu = trigger.needsMenu(player.containerMenu.containerId)
                ? player.containerMenu
                : player.inventoryMenu;
        List<ViewSlot> slots = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (MenuSlots.isInCurrentView(menu, slot)) {
                slots.add(new ViewSlot(slot.container, slot.getContainerSlot()));
            }
        }
        Inventory inventory = player.getInventory();
        for (int index = 0; index < Inventory.INVENTORY_SIZE; index++) {
            slots.add(new ViewSlot(inventory, index));
        }
        slots.add(new ViewSlot(inventory, Inventory.SLOT_OFFHAND));
        return new CurrentView(player, itemMapping, List.copyOf(slots));
    }

    /** How many of each item the current view has, including the carried stack. */
    Map<ItemId, Integer> counts() {
        Map<ItemId, Integer> counts = new HashMap<>();
        for (ViewSlot slot : slots) {
            count(counts, slot.stack());
        }
        count(counts, player.containerMenu.getCarried());
        return counts;
    }

    private void count(Map<ItemId, Integer> counts, ItemStack stack) {
        itemMapping.entryOf(stack).ifPresent(entry -> counts.merge(entry.id(), stack.getCount(), Integer::sum));
    }

    /**
     * Removes this many of each item from the current view, stack by stack in order, and the carried stack last.
     * Then sends the changed slots to the client.
     */
    void remove(Map<ItemId, Integer> removed) {
        if (removed.isEmpty()) {
            return;
        }
        Map<ItemId, Integer> left = new HashMap<>(removed);
        for (ViewSlot slot : slots) {
            ItemStack stack = slot.stack();
            int taken = take(left, stack);
            if (taken > 0) {
                slot.container().setItem(slot.index(), shrunk(stack, taken));
                slot.container().setChanged();
            }
        }
        ItemStack carried = player.containerMenu.getCarried();
        int taken = take(left, carried);
        if (taken > 0) {
            player.containerMenu.setCarried(shrunk(carried, taken));
        }
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }

    private static ItemStack shrunk(ItemStack stack, int taken) {
        return taken >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - taken);
    }

    /** How many to take from the stack, and counts that down in {@code left}. */
    private int take(Map<ItemId, Integer> left, ItemStack stack) {
        return itemMapping.entryOf(stack).map(entry -> {
            int wanted = left.getOrDefault(entry.id(), 0);
            int taken = Math.min(wanted, stack.getCount());
            if (taken > 0) {
                left.put(entry.id(), wanted - taken);
            }
            return taken;
        }).orElse(0);
    }
}
