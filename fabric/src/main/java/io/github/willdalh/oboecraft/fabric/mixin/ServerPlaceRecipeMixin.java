package io.github.willdalh.oboecraft.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.willdalh.oboecraft.core.lock.Interaction;
import io.github.willdalh.oboecraft.fabric.lock.CheckedStack;
import io.github.willdalh.oboecraft.fabric.lock.ServerLockHooks;
import io.github.willdalh.oboecraft.fabric.lock.StackLocation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Checks the ingredients the recipe book would move from the inventory into a crafting grid or a furnace. Locked
 * stacks are left out of what the recipe book counts as available, so it uses unlocked stacks where it can, and
 * otherwise places nothing and shows the recipe's ghost, as for missing ingredients. The client is then told once to
 * open the answer menu for a locked ingredient.
 */
@Mixin(ServerPlaceRecipe.class)
abstract class ServerPlaceRecipeMixin {
    @WrapOperation(method = "placeRecipe", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;"
                    + "fillStackedContents(Lnet/minecraft/world/entity/player/StackedItemContents;)V"))
    private static void oboecraft$leaveOutLockedIngredients(
            Inventory inventory, StackedItemContents contents, Operation<Void> original) {
        if (!(inventory.player instanceof ServerPlayer player)) {
            original.call(inventory, contents);
            return;
        }
        for (int index = 0; index < Inventory.INVENTORY_SIZE; index++) {
            ItemStack stack = inventory.getItem(index);
            if (!stack.isEmpty() && !ServerLockHooks.wouldStop(player, ingredient(stack, index))) {
                contents.accountSimpleStack(stack);
            }
        }
    }

    @Inject(method = "placeRecipe", at = @At("RETURN"))
    private static void oboecraft$showReviewOfLockedIngredient(
            ServerPlaceRecipe.CraftingMenuAccess<?> menu, int gridWidth, int gridHeight, List<Slot> inputGridSlots,
            List<Slot> slotsToClear, Inventory inventory, RecipeHolder<?> recipe, boolean useMaxItems,
            boolean allowDroppingItemsToClear, CallbackInfoReturnable<RecipeBookMenu.PostPlaceAction> cir) {
        if (cir.getReturnValue() != RecipeBookMenu.PostPlaceAction.PLACE_GHOST_RECIPE
                || !(inventory.player instanceof ServerPlayer player)) {
            return;
        }
        List<Ingredient> ingredients = recipe.value().placementInfo().ingredients();
        List<CheckedStack> candidates = new ArrayList<>();
        for (int index = 0; index < Inventory.INVENTORY_SIZE; index++) {
            ItemStack stack = inventory.getItem(index);
            if (!stack.isEmpty() && ingredients.stream().anyMatch(ingredient -> ingredient.test(stack))) {
                candidates.add(ingredient(stack, index));
            }
        }
        ServerLockHooks.stops(player, candidates);
    }

    private static CheckedStack ingredient(ItemStack stack, int inventorySlot) {
        return new CheckedStack(stack, Interaction.INGREDIENT, new StackLocation.InventorySlot(inventorySlot));
    }
}
