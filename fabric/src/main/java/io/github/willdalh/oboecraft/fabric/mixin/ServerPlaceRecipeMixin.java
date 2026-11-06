package io.github.willdalh.oboecraft.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.willdalh.oboecraft.core.lock.Interaction;
import io.github.willdalh.oboecraft.fabric.lock.CheckedStack;
import io.github.willdalh.oboecraft.fabric.lock.ServerLockHooks;
import io.github.willdalh.oboecraft.fabric.lock.StackLocation;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Checks the ingredients the recipe book moves from the inventory into a crafting grid. A locked ingredient isn't
 * moved, as if the inventory didn't have it, and the client is told to open the answer menu for it.
 */
@Mixin(ServerPlaceRecipe.class)
abstract class ServerPlaceRecipeMixin {
    @WrapOperation(method = "moveItemToGrid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;findSlotMatchingCraftingIngredient("
                    + "Lnet/minecraft/core/Holder;Lnet/minecraft/world/item/ItemStack;)I"))
    private int oboecraft$skipLockedIngredient(
            Inventory inventory, Holder<Item> item, ItemStack existing, Operation<Integer> original) {
        int slot = original.call(inventory, item, existing);
        if (slot != Inventory.NOT_FOUND_INDEX && inventory.player instanceof ServerPlayer player
                && ServerLockHooks.stops(player, List.of(new CheckedStack(
                        inventory.getItem(slot), Interaction.INGREDIENT, new StackLocation.InventorySlot(slot))))) {
            return Inventory.NOT_FOUND_INDEX;
        }
        return slot;
    }
}
