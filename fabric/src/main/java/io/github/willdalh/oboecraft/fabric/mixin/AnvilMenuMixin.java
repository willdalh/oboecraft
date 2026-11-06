package io.github.willdalh.oboecraft.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Takes renaming out of the anvil for everything except name tags, on both sides. When the anvil works out its
 * result, the name the player typed reads as the input's own name for any other item, so the result keeps the input's
 * name, custom or not, and costs nothing for it. Repairing and combining enchantments are unchanged.
 */
@Mixin(AnvilMenu.class)
abstract class AnvilMenuMixin {
    @ModifyExpressionValue(method = "createResult", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/inventory/AnvilMenu;itemName:Ljava/lang/String;"))
    private String oboecraft$keepNameUnlessNameTag(String typedName) {
        ItemStack input = ((AnvilMenu) (Object) this).getSlot(AnvilMenu.INPUT_SLOT).getItem();
        return OboecraftMod.itemMapping().mayRename(input) ? typedName : input.getHoverName().getString();
    }

    /** An input whose own name is blank would otherwise count as having its name taken away. */
    @ModifyExpressionValue(method = "createResult", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/StringUtil;isBlank(Ljava/lang/String;)Z"))
    private boolean oboecraft$neverTakeNameAwayUnlessNameTag(boolean blank) {
        ItemStack input = ((AnvilMenu) (Object) this).getSlot(AnvilMenu.INPUT_SLOT).getItem();
        return blank && OboecraftMod.itemMapping().mayRename(input);
    }
}
