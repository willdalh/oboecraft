package io.github.willdalh.oboecraft.fabric.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.willdalh.oboecraft.fabric.client.display.ItemDisplay;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Masks only the selected item's label, with the current state on every frame. */
@Mixin(Hud.class)
abstract class HudMixin {
    @WrapOperation(method = "extractSelectedItemName", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getHoverName()Lnet/minecraft/network/chat/Component;"))
    private Component oboecraft$hideLockedName(ItemStack stack, Operation<Component> original) {
        return ItemDisplay.name(stack, original.call(stack));
    }
}
