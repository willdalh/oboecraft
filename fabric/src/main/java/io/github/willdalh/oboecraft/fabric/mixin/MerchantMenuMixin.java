package io.github.willdalh.oboecraft.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.willdalh.oboecraft.core.lock.Interaction;
import io.github.willdalh.oboecraft.fabric.lock.CheckedStack;
import io.github.willdalh.oboecraft.fabric.lock.ServerLockHooks;
import io.github.willdalh.oboecraft.fabric.lock.StackLocation;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Checks the payment that choosing a trade moves from the inventory into the payment slots. A locked stack isn't
 * moved, as if it didn't pay, and the client is told to open the answer menu for it. The client moves the payment
 * too, and Minecraft sends it the menu's real contents on the next tick.
 */
@Mixin(MerchantMenu.class)
abstract class MerchantMenuMixin {
    @WrapOperation(method = "moveFromInventoryToPaymentSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/trading/ItemCost;test(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean oboecraft$skipLockedPayment(ItemCost cost, ItemStack stack, Operation<Boolean> original) {
        if (!original.call(cost, stack)) {
            return false;
        }
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        for (Slot slot : menu.slots) {
            if (slot.getItem() == stack && slot.container instanceof Inventory inventory
                    && inventory.player instanceof ServerPlayer player) {
                return !ServerLockHooks.stops(player, List.of(new CheckedStack(
                        stack, Interaction.TRADE, new StackLocation.MenuSlot(menu.containerId, slot.index))));
            }
        }
        return true;
    }
}
