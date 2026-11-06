package io.github.willdalh.oboecraft.fabric.mixin;

import io.github.willdalh.oboecraft.fabric.lock.HeldStacks;
import io.github.willdalh.oboecraft.fabric.lock.ServerLockHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Checks placing and using a held stack on the server. {@code ItemStack.useOn} is where a stack is used on a block,
 * after the block itself had its chance to react, so opening a chest or crafting table with a locked item in hand
 * still works. {@code ItemStack.use} is every use in the air, like eating, throwing, shooting and putting on armor.
 */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void oboecraft$checkLockBeforePlacing(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (context.getPlayer() instanceof ServerPlayer player && ServerLockHooks.stops(
                player, HeldStacks.placing(player, context.getHand(), (ItemStack) (Object) this))) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void oboecraft$checkLockBeforeUsing(
            Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (player instanceof ServerPlayer serverPlayer && ServerLockHooks.stops(
                serverPlayer, HeldStacks.using(serverPlayer, hand, (ItemStack) (Object) this))) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
