package io.github.willdalh.oboecraft.fabric.lock;

import io.github.willdalh.oboecraft.core.lock.Interaction;
import java.util.Optional;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ArmorSlot;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.FurnaceFuelSlot;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;

/** What moving a stack into or out of a slot of a menu does, for the lock check. */
public final class MenuSlots {
    /** The brewing stand's fuel slot, in its container. */
    private static final int BREWING_FUEL_SLOT = 4;

    private MenuSlots() {
    }

    /**
     * What putting a stack into the slot does, or empty if it is free: moving a stack within the inventory is never
     * checked. Putting on armor, in the inventory or on a mount, is equipping it.
     */
    public static Optional<Interaction> putting(AbstractContainerMenu menu, Slot slot) {
        if (slot instanceof ArmorSlot) {
            return Optional.of(Interaction.EQUIP);
        }
        if (isPlayerInventory(slot)) {
            return Optional.empty();
        }
        if (slot instanceof FurnaceFuelSlot
                || menu instanceof BrewingStandMenu && slot.getContainerSlot() == BREWING_FUEL_SLOT) {
            return Optional.of(Interaction.FUEL);
        }
        if (slot.container instanceof MerchantContainer) {
            return Optional.of(Interaction.TRADE);
        }
        if (makesResultFromInputs(menu)) {
            return Optional.of(Interaction.INGREDIENT);
        }
        return Optional.of(Interaction.PUT_IN_CONTAINER);
    }

    /**
     * What taking a stack out of the slot into the inventory does, or empty if it is free: moving a stack within the
     * inventory and taking off armor are never checked.
     */
    public static Optional<Interaction> taking(AbstractContainerMenu menu, Slot slot) {
        if (slot instanceof ArmorSlot || isPlayerInventory(slot)) {
            return Optional.empty();
        }
        if (slot instanceof MerchantResultSlot) {
            return Optional.of(Interaction.TRADE);
        }
        if (isCraftingResult(menu, slot)) {
            return Optional.of(Interaction.CRAFT);
        }
        return Optional.of(Interaction.TAKE_FROM_CONTAINER);
    }

    /**
     * Whether the slot is the result of a menu that makes it from input slots, like the crafting grid, a stonecutter
     * or a smithing table. A furnace's output isn't: it is a slot of the furnace's container.
     */
    public static boolean isCraftingResult(AbstractContainerMenu menu, Slot slot) {
        return slot.container instanceof ResultContainer
                || menu instanceof LoomMenu loom && slot == loom.getResultSlot();
    }

    /**
     * Whether the slot is part of the current view besides the player's own inventory: any slot of the menu's own
     * containers other than a result. In a menu that makes its result from input slots, these are its inputs. A
     * furnace's output is a slot of the furnace's container, so it is part of the current view. A mount's saddle and
     * armor are worn, so they are in use and never part of it.
     */
    public static boolean isInCurrentView(AbstractContainerMenu menu, Slot slot) {
        return !isPlayerInventory(slot) && !(slot instanceof ArmorSlot) && !isCraftingResult(menu, slot)
                && !(slot instanceof MerchantResultSlot);
    }

    /** Whether the slot is one of the player's own inventory, including worn armor and the offhand. */
    public static boolean isPlayerInventory(Slot slot) {
        return slot.container instanceof Inventory;
    }

    /** Menus whose result is made from what is in their input slots, so those slots hold ingredients. */
    private static boolean makesResultFromInputs(AbstractContainerMenu menu) {
        return menu instanceof AbstractCraftingMenu
                || menu instanceof CrafterMenu
                || menu instanceof ItemCombinerMenu
                || menu instanceof StonecutterMenu
                || menu instanceof LoomMenu
                || menu instanceof CartographyTableMenu
                || menu instanceof GrindstoneMenu;
    }
}
