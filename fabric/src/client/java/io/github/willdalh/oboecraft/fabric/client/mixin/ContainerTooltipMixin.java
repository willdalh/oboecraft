package io.github.willdalh.oboecraft.fabric.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.willdalh.oboecraft.fabric.client.display.TooltipDisplayContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Menu tooltips assemble their own lines and image, rather than using the graphics stack overload. */
@Mixin(AbstractContainerScreen.class)
abstract class ContainerTooltipMixin {
    @Shadow protected @Nullable Slot hoveredSlot;

    @WrapMethod(method = "extractTooltip")
    private void oboecraft$displayedStack(GuiGraphicsExtractor graphics, int x, int y, Operation<Void> original) {
        ItemStack stack = hoveredSlot == null ? ItemStack.EMPTY : hoveredSlot.getItem();
        TooltipDisplayContext.withStack(stack, () -> original.call(graphics, x, y));
    }
}
