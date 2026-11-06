package io.github.willdalh.oboecraft.fabric.review;

import io.github.willdalh.oboecraft.fabric.lock.MenuSlots;
import io.github.willdalh.oboecraft.fabric.lock.StackLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * What the player was doing when a review started, which decides the current view for a punishment. The server works
 * it out itself from the menu the player has open and where the stack is, never from what the client says.
 */
public sealed interface Trigger {
    /** A stack in a hand or the inventory, with no other menu open. */
    record Inventory() implements Trigger {
        @Override
        public boolean needsMenu(int menuId) {
            return false;
        }
    }

    /** A stack in an open menu or in the inventory while a menu is open, other than a crafting result. */
    record Container(int containerId) implements Trigger {
        @Override
        public boolean needsMenu(int menuId) {
            return containerId == menuId;
        }
    }

    /** The result slot of a menu that makes its result from input slots, like the crafting grid. */
    record Crafting(int containerId) implements Trigger {
        @Override
        public boolean needsMenu(int menuId) {
            return containerId == menuId;
        }
    }

    /**
     * The trigger of a review of the stack at {@code location}, which must be a valid location for the player, see
     * {@link StackLocation#stackIn}.
     */
    static Trigger of(ServerPlayer player, StackLocation location) {
        AbstractContainerMenu menu = player.containerMenu;
        if (location instanceof StackLocation.MenuSlot slot
                && MenuSlots.isCraftingResult(menu, menu.getSlot(slot.slot()))) {
            return new Crafting(menu.containerId);
        }
        return menu == player.inventoryMenu ? new Inventory() : new Container(menu.containerId);
    }

    /** Whether the review needs the menu with this container id to stay open, since its current view is in it. */
    boolean needsMenu(int menuId);
}
