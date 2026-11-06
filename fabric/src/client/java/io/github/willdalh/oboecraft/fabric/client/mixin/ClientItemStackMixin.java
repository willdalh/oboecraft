package io.github.willdalh.oboecraft.fabric.client.mixin;

import io.github.willdalh.oboecraft.fabric.client.OboecraftClient;
import io.github.willdalh.oboecraft.fabric.client.display.ItemDisplay;
import io.github.willdalh.oboecraft.fabric.client.display.TooltipDisplayContext;
import io.github.willdalh.oboecraft.fabric.lock.HeldStacks;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
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
 * Checks placing and using a held stack on the client, before the client predicts it. Minecraft still sends the use
 * to the server, which checks again and has the final say. Names are masked only inside a displayed tooltip's scope,
 * including names read by nested container and bundle previews.
 */
@Mixin(ItemStack.class)
abstract class ClientItemStackMixin {
    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void oboecraft$hideDisplayedName(CallbackInfoReturnable<Component> cir) {
        if (TooltipDisplayContext.hidesNames()) {
            cir.setReturnValue(ItemDisplay.name((ItemStack) (Object) this, cir.getReturnValue()));
        }
    }

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void oboecraft$checkLockBeforePlacingOnClient(
            UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (context.getLevel().isClientSide()
                && context.getPlayer() instanceof LocalPlayer player
                && OboecraftClient.reviews().stops(
                        player, HeldStacks.placing(player, context.getHand(), (ItemStack) (Object) this))) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void oboecraft$checkLockBeforeUsingOnClient(
            Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (level.isClientSide()
                && player instanceof LocalPlayer localPlayer
                && OboecraftClient.reviews().stops(
                        localPlayer, HeldStacks.using(localPlayer, hand, (ItemStack) (Object) this))) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
