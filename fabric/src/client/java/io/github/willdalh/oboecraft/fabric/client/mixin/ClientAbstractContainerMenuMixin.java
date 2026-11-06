package io.github.willdalh.oboecraft.fabric.client.mixin;

import io.github.willdalh.oboecraft.fabric.client.OboecraftClient;
import io.github.willdalh.oboecraft.fabric.lock.MenuClicks;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Checks a shift-click in the inventory on the client, where the menu's own code moves the stack while the client
 * predicts the click. Minecraft still sends the click, and the server checks it again.
 */
@Mixin(AbstractContainerMenu.class)
abstract class ClientAbstractContainerMenuMixin {
    @Inject(method = "moveItemStackTo", at = @At("HEAD"), cancellable = true)
    private void oboecraft$checkLockBeforeShiftClickMoveOnClient(
            ItemStack stack, int startSlot, int endSlot, boolean backwards, CallbackInfoReturnable<Boolean> cir) {
        MenuClicks.moveIntoMenu((AbstractContainerMenu) (Object) this, stack, startSlot).ifPresent(move -> {
            if (move.player() instanceof LocalPlayer player
                    && OboecraftClient.reviews().stops(player, List.of(move.checked()))) {
                cir.setReturnValue(false);
            }
        });
    }
}
