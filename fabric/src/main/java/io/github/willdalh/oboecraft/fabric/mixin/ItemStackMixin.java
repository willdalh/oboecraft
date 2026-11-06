package io.github.willdalh.oboecraft.fabric.mixin;

import io.github.willdalh.oboecraft.fabric.lock.ServerLockHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Checks placing on the server. {@code ItemStack.useOn} is where a stack is used on a block, after the block itself
 * had its chance to react, so opening a chest or crafting table with a locked item in hand still works.
 */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void oboecraft$checkLockBeforePlacing(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (context.getPlayer() instanceof ServerPlayer player
                && ServerLockHooks.stopsPlacing(player, context.getHand(), (ItemStack) (Object) this)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
