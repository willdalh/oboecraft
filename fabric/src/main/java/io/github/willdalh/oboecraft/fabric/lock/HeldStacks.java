package io.github.willdalh.oboecraft.fabric.lock;

import io.github.willdalh.oboecraft.core.lock.Interaction;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;

/**
 * The stacks that actions with a held stack interact with, for the lock check. Both sides use the same rules, so the
 * client stops the same actions as the server.
 */
public final class HeldStacks {
    /** Whether an item class has its own {@code use}, worked out once per class. */
    private static final ClassValue<Boolean> OVERRIDES_USE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> itemClass) {
            try {
                return itemClass.getMethod("use", Level.class, Player.class, InteractionHand.class)
                        .getDeclaringClass() != Item.class;
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("Item.use isn't there", e);
            }
        }
    };

    private HeldStacks() {
    }

    /** Using the stack in the hand on a block. */
    public static List<CheckedStack> placing(Player player, InteractionHand hand, ItemStack stack) {
        return List.of(new CheckedStack(stack, Interaction.PLACE, StackLocation.hand(player, hand)));
    }

    /**
     * Using the stack in the hand in the air: putting it on if using it equips it, otherwise a use. A bow, or a
     * crossbow that isn't loaded, also uses the ammo it would shoot. A stack whose item has no use in the air, like a
     * block, does nothing there, so it isn't checked.
     */
    public static List<CheckedStack> using(Player player, InteractionHand hand, ItemStack stack) {
        if (!hasUse(stack)) {
            return List.of();
        }
        List<CheckedStack> stacks = new ArrayList<>(2);
        Interaction interaction = equipsWhenUsed(stack) ? Interaction.EQUIP : Interaction.USE;
        stacks.add(new CheckedStack(stack, interaction, StackLocation.hand(player, hand)));
        if (stack.getItem() instanceof ProjectileWeaponItem && !CrossbowItem.isCharged(stack)) {
            ItemStack ammo = player.getProjectile(stack);
            inventorySlotOf(player, ammo).ifPresent(slot -> stacks.add(new CheckedStack(ammo, Interaction.AMMO, slot)));
        }
        return stacks;
    }

    /** Using the stack in the hand on an entity. */
    public static List<CheckedStack> usingOnEntity(Player player, InteractionHand hand) {
        return List.of(new CheckedStack(player.getItemInHand(hand), Interaction.USE, StackLocation.hand(player, hand)));
    }

    /** Hitting with the stack in the main hand. Only a weapon does anything when it hits, so only a weapon counts. */
    public static List<CheckedStack> attacking(Player player) {
        return player.getMainHandItem().has(DataComponents.WEAPON) ? mainHand(player, Interaction.ATTACK) : List.of();
    }

    /** Breaking a block with the stack in the main hand. Only a tool changes how a block breaks, so only a tool counts. */
    public static List<CheckedStack> mining(Player player) {
        return player.getMainHandItem().has(DataComponents.TOOL) ? mainHand(player, Interaction.MINE) : List.of();
    }

    /** Dropping the stack in the selected hotbar slot. */
    public static List<CheckedStack> dropping(Player player) {
        return mainHand(player, Interaction.DROP);
    }

    private static List<CheckedStack> mainHand(Player player, Interaction interaction) {
        return List.of(new CheckedStack(
                player.getMainHandItem(), interaction, StackLocation.hand(player, InteractionHand.MAIN_HAND)));
    }

    /**
     * Whether using the stack in the air can do something. Minecraft's default {@code Item.use} only does something
     * for these components, and items with their own use, like bows, buckets and snowballs, override it.
     */
    private static boolean hasUse(ItemStack stack) {
        return OVERRIDES_USE.get(stack.getItem().getClass())
                || stack.has(DataComponents.CONSUMABLE)
                || stack.has(DataComponents.EQUIPPABLE)
                || stack.has(DataComponents.BLOCKS_ATTACKS)
                || stack.has(DataComponents.KINETIC_WEAPON);
    }

    /** Using these items equips them, like armor, unless they are eaten or drunk first. */
    private static boolean equipsWhenUsed(ItemStack stack) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return !stack.has(DataComponents.CONSUMABLE) && equippable != null && equippable.swappable();
    }

    /**
     * The inventory slot that holds this very stack, like the ammo {@link Player#getProjectile} found in the
     * inventory. Empty for an empty stack.
     */
    private static Optional<StackLocation> inventorySlotOf(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        Inventory inventory = player.getInventory();
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            if (inventory.getItem(index) == stack) {
                return Optional.of(new StackLocation.InventorySlot(index));
            }
        }
        return Optional.empty();
    }
}
