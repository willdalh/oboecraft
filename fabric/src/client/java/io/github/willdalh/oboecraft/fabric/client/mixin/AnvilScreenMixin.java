package io.github.willdalh.oboecraft.fabric.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Only a name tag can be renamed, so the anvil's name field can only be typed in for a name tag. */
@Mixin(AnvilScreen.class)
abstract class AnvilScreenMixin {
    @WrapOperation(method = "slotChanged", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/EditBox;setEditable(Z)V"))
    private void oboecraft$editableOnlyForNameTag(EditBox name, boolean editable, Operation<Void> original,
            AbstractContainerMenu menu, int slot, ItemStack stack) {
        original.call(name, editable && OboecraftMod.itemMapping().mayRename(stack));
    }
}
