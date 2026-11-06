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
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Checks the payment that choosing a trade moves from the inventory into the payment slots, on the server. A locked
 * stack isn't moved, as if it didn't pay. Once every payment has moved, the client is sent the menu's real contents,
 * which undoes its own move, and is told to open the answer menu for the first locked stack.
 */
@Mixin(MerchantMenu.class)
abstract class MerchantMenuMixin {
    /** The first payment the lock check stopped while the current trade's payment moves. */
    @Unique
    private @Nullable ServerPlayer oboecraft$stoppedPlayer;
    @Unique
    private @Nullable CheckedStack oboecraft$stoppedPayment;

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
                CheckedStack payment = new CheckedStack(
                        stack, Interaction.TRADE, new StackLocation.MenuSlot(menu.containerId, slot.index));
                if (!ServerLockHooks.wouldStop(player, payment)) {
                    return true;
                }
                if (oboecraft$stoppedPayment == null) {
                    oboecraft$stoppedPlayer = player;
                    oboecraft$stoppedPayment = payment;
                }
                return false;
            }
        }
        return true;
    }

    @Inject(method = "tryMoveItems", at = @At("RETURN"))
    private void oboecraft$showReviewOfStoppedPayment(int selectedOffer, CallbackInfo ci) {
        if (oboecraft$stoppedPayment != null && oboecraft$stoppedPlayer != null) {
            ServerLockHooks.stops(oboecraft$stoppedPlayer, List.of(oboecraft$stoppedPayment));
        }
        oboecraft$stoppedPlayer = null;
        oboecraft$stoppedPayment = null;
    }
}
