package io.github.willdalh.oboecraft.fabric.lock;

import io.github.willdalh.oboecraft.core.lock.Interaction;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The stacks a click in a menu would interact with, worked out from the click before Minecraft carries it out. Both
 * sides use the same rules, so the client stops the same clicks as the server. Where Minecraft's own outcome depends
 * on more than the slot, like where a shift-click moves a stack, the click is checked as if the stack moves.
 */
public final class MenuClicks {
    /** The slot index Minecraft uses for a click outside the menu, which drops what the player carries. */
    private static final int OUTSIDE = AbstractContainerMenu.SLOT_CLICKED_OUTSIDE;
    /** The stage of a drag that adds a slot to it. */
    private static final int DRAG_ADD_SLOT = 1;

    private MenuClicks() {
    }

    /**
     * The stacks the click would interact with, in order.
     *
     * @param menu   the menu the player clicks in
     * @param slot   the index of the clicked slot in the menu, or {@code -999} for outside it
     * @param button the button, or for {@link ContainerInput#SWAP} the inventory slot to swap with
     * @param input  what kind of click it is
     */
    public static List<CheckedStack> stacksOf(
            AbstractContainerMenu menu, int slot, int button, ContainerInput input, Player player) {
        List<CheckedStack> stacks = new ArrayList<>(2);
        Clicked clicked = new Clicked(menu, stacks);
        if (slot == OUTSIDE) {
            if (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE) {
                clicked.carried(Interaction.DROP);
            }
            return stacks;
        }
        if (slot < 0 || slot >= menu.slots.size()) {
            return stacks;
        }
        Slot target = menu.getSlot(slot);
        switch (input) {
            case PICKUP -> {
                clicked.taken(target, player);
                clicked.put(target, menu.getCarried(), new StackLocation.Carried(menu.containerId));
            }
            case QUICK_MOVE -> clicked.quickMoved(target, player);
            case SWAP -> {
                clicked.taken(target, player);
                if (button >= 0 && button < Inventory.SELECTION_SIZE || button == Inventory.SLOT_OFFHAND) {
                    clicked.put(target, player.getInventory().getItem(button), new StackLocation.InventorySlot(button));
                }
            }
            case THROW -> {
                if (MenuSlots.isPlayerInventory(target)) {
                    clicked.add(target.getItem(), Interaction.DROP, clicked.locationOf(target));
                } else {
                    clicked.taken(target, player);
                }
            }
            case QUICK_CRAFT -> {
                if (AbstractContainerMenu.getQuickcraftHeader(button) == DRAG_ADD_SLOT) {
                    clicked.put(target, menu.getCarried(), new StackLocation.Carried(menu.containerId));
                }
            }
            case PICKUP_ALL -> clicked.gathered(player);
            case CLONE -> {
                // Only creative mode copies a stack, and locks aren't active there.
            }
        }
        return stacks;
    }

    /** Collects the stacks of one click. */
    private record Clicked(AbstractContainerMenu menu, List<CheckedStack> stacks) {
        StackLocation locationOf(Slot target) {
            return new StackLocation.MenuSlot(menu.containerId, target.index);
        }

        void add(ItemStack stack, Interaction interaction, StackLocation location) {
            if (!stack.isEmpty()) {
                stacks.add(new CheckedStack(stack, interaction, location));
            }
        }

        void carried(Interaction interaction) {
            add(menu.getCarried(), interaction, new StackLocation.Carried(menu.containerId));
        }

        /** The slot's stack would leave it, if the player may take it. */
        void taken(Slot target, Player player) {
            if (target.hasItem() && target.mayPickup(player)) {
                MenuSlots.taking(menu, target).ifPresent(interaction ->
                        add(target.getItem(), interaction, locationOf(target)));
            }
        }

        /** The stack would go into the slot, if the slot takes it. */
        void put(Slot target, ItemStack stack, StackLocation from) {
            if (!stack.isEmpty() && target.mayPlace(stack)) {
                MenuSlots.putting(menu, target).ifPresent(interaction -> add(stack, interaction, from));
            }
        }

        /**
         * A shift-click moves the slot's stack out of a container slot into the inventory, or out of the inventory into
         * the first other slot that takes it.
         */
        void quickMoved(Slot target, Player player) {
            if (!target.hasItem() || !target.mayPickup(player)) {
                return;
            }
            if (!MenuSlots.isPlayerInventory(target)) {
                taken(target, player);
                return;
            }
            ItemStack stack = target.getItem();
            for (Slot other : menu.slots) {
                if (other != target && !MenuSlots.isPlayerInventory(other) && other.mayPlace(stack)) {
                    Optional<Interaction> interaction = MenuSlots.putting(menu, other);
                    if (interaction.isPresent()) {
                        add(stack, interaction.get(), locationOf(target));
                        return;
                    }
                }
            }
        }

        /** A double-click gathers stacks like the carried one into it, also from container slots. */
        void gathered(Player player) {
            ItemStack carried = menu.getCarried();
            if (carried.isEmpty()) {
                return;
            }
            for (Slot other : menu.slots) {
                if (!MenuSlots.isPlayerInventory(other) && other.hasItem() && other.mayPickup(player)
                        && AbstractContainerMenu.canItemQuickReplace(other, carried, true)
                        && menu.canTakeItemForPickAll(carried, other)) {
                    MenuSlots.taking(menu, other).ifPresent(interaction ->
                            add(carried, interaction, new StackLocation.Carried(menu.containerId)));
                    return;
                }
            }
        }
    }
}
