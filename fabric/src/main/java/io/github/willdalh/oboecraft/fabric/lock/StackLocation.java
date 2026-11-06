package io.github.willdalh.oboecraft.fabric.lock;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Where a stack is, so both sides can find it: a slot of the player's inventory, a slot of their open menu, or the
 * stack they carry in it. Each side reads the stack from its own state, so a location never says which item it is.
 */
public sealed interface StackLocation {
    /**
     * A slot of the player's inventory, by its index there: the hotbar and main inventory, or the offhand. Worn armor
     * isn't part of the current view, so it is never a location.
     */
    record InventorySlot(int index) implements StackLocation {
        static final StreamCodec<ByteBuf, InventorySlot> STREAM_CODEC =
                ByteBufCodecs.VAR_INT.map(InventorySlot::new, InventorySlot::index);

        @Override
        public Optional<ItemStack> stackIn(Player player) {
            boolean inCurrentView = index >= 0 && index < Inventory.INVENTORY_SIZE || index == Inventory.SLOT_OFFHAND;
            return inCurrentView ? Optional.of(player.getInventory().getItem(index)) : Optional.empty();
        }
    }

    /** A slot of the menu the player has open, by its index in the menu. */
    record MenuSlot(int containerId, int slot) implements StackLocation {
        static final StreamCodec<ByteBuf, MenuSlot> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, MenuSlot::containerId,
                ByteBufCodecs.VAR_INT, MenuSlot::slot,
                MenuSlot::new);

        @Override
        public Optional<ItemStack> stackIn(Player player) {
            AbstractContainerMenu menu = player.containerMenu;
            return menu.containerId == containerId && slot >= 0 && slot < menu.slots.size()
                    ? Optional.of(menu.getSlot(slot).getItem())
                    : Optional.empty();
        }
    }

    /** The stack the player carries with the mouse in the menu they have open. */
    record Carried(int containerId) implements StackLocation {
        static final StreamCodec<ByteBuf, Carried> STREAM_CODEC =
                ByteBufCodecs.VAR_INT.map(Carried::new, Carried::containerId);

        @Override
        public Optional<ItemStack> stackIn(Player player) {
            AbstractContainerMenu menu = player.containerMenu;
            return menu.containerId == containerId ? Optional.of(menu.getCarried()) : Optional.empty();
        }
    }

    /** Decoding refuses a kind of location that doesn't exist. */
    StreamCodec<ByteBuf, StackLocation> STREAM_CODEC = ByteBufCodecs.VAR_INT.<StackLocation>dispatch(
            location -> switch (location) {
                case InventorySlot inventorySlot -> 0;
                case MenuSlot menuSlot -> 1;
                case Carried carried -> 2;
            },
            kind -> switch (kind) {
                case 0 -> InventorySlot.STREAM_CODEC;
                case 1 -> MenuSlot.STREAM_CODEC;
                case 2 -> Carried.STREAM_CODEC;
                default -> throw new IllegalArgumentException("Unknown kind of stack location " + kind);
            });

    /** The slot of the stack in the player's hand. */
    static StackLocation hand(Player player, InteractionHand hand) {
        return new InventorySlot(hand == InteractionHand.MAIN_HAND
                ? player.getInventory().getSelectedSlot()
                : Inventory.SLOT_OFFHAND);
    }

    /**
     * The stack at this location on the player's side, or empty if the location isn't valid for the player: not a
     * slot in the current view, or not in the menu they have open.
     */
    Optional<ItemStack> stackIn(Player player);
}
