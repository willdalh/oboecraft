package io.github.willdalh.oboecraft.fabric.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.willdalh.oboecraft.core.srs.DisplayState;
import io.github.willdalh.oboecraft.fabric.client.display.ItemDisplay;
import io.github.willdalh.oboecraft.fabric.item.AnvilRenaming;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The anvil's name field: it shows a locked item's name as {@code ???}, like its tooltip, and it can only be typed in
 * for a name tag whose name isn't hidden, since only a name tag can be renamed.
 */
@Mixin(AnvilScreen.class)
abstract class AnvilScreenMixin {
    @WrapOperation(method = "slotChanged", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getHoverName()Lnet/minecraft/network/chat/Component;"))
    private Component oboecraft$hideLockedName(ItemStack stack, Operation<Component> original) {
        return ItemDisplay.name(stack, original.call(stack));
    }

    @WrapOperation(method = "slotChanged", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/EditBox;setEditable(Z)V"))
    private void oboecraft$editableOnlyForNameTag(EditBox name, boolean editable, Operation<Void> original,
            AbstractContainerMenu menu, int slot, ItemStack stack) {
        original.call(name, editable && AnvilRenaming.mayRename(stack)
                && !(ItemDisplay.of(stack) instanceof DisplayState.Hidden));
    }
}
