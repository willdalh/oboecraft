package io.github.willdalh.oboecraft.fabric.review;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.punishment.Recipe;
import io.github.willdalh.oboecraft.fabric.item.ItemMapping;
import io.github.willdalh.oboecraft.fabric.lock.MenuSlots;
import io.github.willdalh.oboecraft.fabric.lock.StackLocation;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * What the player was doing when a review started, which decides the current view for a punishment. The server works
 * it out itself from the menu the player has open and where the stack is. The client only names the location, and
 * {@link ServerReviews#start} only starts a review where the server stopped an action or the lock check stops the
 * stack, never at a location the client merely picked.
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

    /**
     * The result slot of a menu that makes its result from input slots, like the crafting grid.
     *
     * @param recipe what the menu's input slots held when the review started, which a fail removes from
     */
    record Crafting(int containerId, Recipe recipe) implements Trigger {
        public Crafting {
            Objects.requireNonNull(recipe, "recipe");
        }

        @Override
        public boolean needsMenu(int menuId) {
            return containerId == menuId;
        }
    }

    /**
     * The trigger of a review of the stack at {@code location}, which must be a valid location for the player, see
     * {@link StackLocation#stackIn}. A crafting trigger notes the recipe from the menu's input slots.
     */
    static Trigger of(ServerPlayer player, StackLocation location, ItemMapping itemMapping) {
        AbstractContainerMenu menu = player.containerMenu;
        if (location instanceof StackLocation.MenuSlot slot
                && MenuSlots.isCraftingResult(menu, menu.getSlot(slot.slot()))) {
            return new Crafting(menu.containerId, recipeOf(menu, itemMapping));
        }
        return menu == player.inventoryMenu ? new Inventory() : new Container(menu.containerId);
    }

    /**
     * The recipe a menu's input slots make its result from. Each stack in an input slot is one use of its item, as
     * one craft takes one item from each slot of a crafting grid. Stacks that aren't items count towards the total
     * only, since they can't be removed.
     */
    private static Recipe recipeOf(AbstractContainerMenu menu, ItemMapping itemMapping) {
        Map<ItemId, Integer> uses = new HashMap<>();
        int totalUses = 0;
        for (Slot slot : menu.slots) {
            if (!MenuSlots.isInCurrentView(menu, slot) || slot.getItem().isEmpty()) {
                continue;
            }
            totalUses++;
            Optional<NameListEntry> entry = itemMapping.entryOf(slot.getItem());
            entry.ifPresent(item -> uses.merge(item.id(), 1, Integer::sum));
        }
        return new Recipe(uses, totalUses);
    }

    /** Whether the review needs the menu with this container id to stay open, since its current view is in it. */
    boolean needsMenu(int menuId);
}
