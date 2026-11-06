package io.github.willdalh.oboecraft.fabric.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.willdalh.oboecraft.fabric.client.OboecraftClient;
import io.github.willdalh.oboecraft.fabric.client.display.ItemDisplay;
import io.github.willdalh.oboecraft.fabric.client.display.LockTimerTooltip;
import io.github.willdalh.oboecraft.fabric.lock.HeldStacks;
import java.util.Optional;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Checks placing and using a held stack on the client, before the client predicts it. Minecraft still sends the use
 * to the server, which checks again and has the final say. Also masks the title at the tooltip call site, without
 * changing the stack's name elsewhere.
 */
@Mixin(ItemStack.class)
abstract class ClientItemStackMixin {
    @WrapOperation(method = "getTooltipLines", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getStyledHoverName()Lnet/minecraft/network/chat/Component;"))
    private Component oboecraft$hideTooltipName(ItemStack stack, Operation<Component> original) {
        return ItemDisplay.of(stack).name(original.call(stack));
    }

    /** Both menu screens and direct item hovers read this image. Preserve the original below the timer. */
    @Inject(method = "getTooltipImage", at = @At("RETURN"), cancellable = true)
    private void oboecraft$addTooltipTimer(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT).hideTooltip()) {
            ItemDisplay.of(stack).timer().ifPresent(timer ->
                    cir.setReturnValue(Optional.of(new LockTimerTooltip(timer, cir.getReturnValue()))));
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
