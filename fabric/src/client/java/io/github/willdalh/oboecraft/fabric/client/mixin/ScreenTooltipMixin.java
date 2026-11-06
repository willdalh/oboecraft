package io.github.willdalh.oboecraft.fabric.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.willdalh.oboecraft.fabric.client.display.TooltipDisplayContext;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** The UI tooltip helper, also used by recipe buttons and ghost slots. Raw tooltip getters stay untouched. */
@Mixin(Screen.class)
abstract class ScreenTooltipMixin {
    @WrapMethod(method = "getTooltipFromItem")
    private static List<Component> oboecraft$displayedNames(Minecraft client, ItemStack stack, Operation<List<Component>> original) {
        return TooltipDisplayContext.withNames(() -> original.call(client, stack));
    }
}
