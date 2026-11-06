package io.github.willdalh.oboecraft.fabric.client.mixin;

import io.github.willdalh.oboecraft.fabric.client.OboecraftClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Checks placing on the client, before the client predicts the placement. Minecraft still sends the use to the
 * server, which checks again and has the final say.
 */
@Mixin(ItemStack.class)
abstract class ClientItemStackMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void oboecraft$checkLockBeforePlacingOnClient(
            UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (context.getLevel().isClientSide()
                && context.getPlayer() instanceof LocalPlayer player
                && OboecraftClient.reviews().stopsPlacing(player, context.getHand(), (ItemStack) (Object) this)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
