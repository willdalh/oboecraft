package io.github.willdalh.oboecraft.fabric.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a menu's screen draws the menu, so the word list button can be placed from the inventory's edges. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int oboecraft$leftPos();

    @Accessor("topPos")
    int oboecraft$topPos();
}
