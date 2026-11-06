package io.github.willdalh.oboecraft.fabric.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.willdalh.oboecraft.core.srs.DisplayState;
import io.github.willdalh.oboecraft.fabric.client.display.ClientLockTimerTooltip;
import io.github.willdalh.oboecraft.fabric.client.display.ItemDisplay;
import io.github.willdalh.oboecraft.fabric.client.display.TooltipDisplayContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GuiGraphicsExtractor.class)
abstract class GraphicsTooltipMixin {
    @WrapMethod(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V")
    private void oboecraft$displayedStack(Font font, ItemStack stack, int x, int y, Operation<Void> original) {
        TooltipDisplayContext.withStack(stack, () -> original.call(font, stack, x, y));
    }

    /** Add a separate client component after vanilla assembles its components; never replace the item's image. */
    @ModifyArg(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setTooltipForNextFrameInternal(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;ZZ)V"), index = 1)
    private List<ClientTooltipComponent> oboecraft$appendTimer(List<ClientTooltipComponent> components) {
        ItemStack stack = TooltipDisplayContext.stack();
        if (!stack.isEmpty() && !stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT).hideTooltip()
                && ItemDisplay.of(stack) instanceof DisplayState.Timed timed) {
            List<ClientTooltipComponent> withTimer = new ArrayList<>(components);
            withTimer.add(new ClientLockTimerTooltip(timed.timer()));
            return withTimer;
        }
        return components;
    }

    /** Bundle captions are read later, when deferred tooltip images are actually drawn. */
    @WrapMethod(method = "tooltip")
    private void oboecraft$drawNestedNames(Font font, List<ClientTooltipComponent> lines, int x, int y,
            ClientTooltipPositioner positioner, @Nullable Identifier style, boolean extraSpace, Operation<Void> original) {
        TooltipDisplayContext.withNames(() -> original.call(font, lines, x, y, positioner, style, extraSpace));
    }
}
