package io.github.willdalh.oboecraft.fabric.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.willdalh.oboecraft.fabric.client.AnswerScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps an open menu's screen intact while the answer menu opens over it. Replacing a screen tells it that it is
 * removed, and a menu's screen then runs the menu's {@code removed} on the client, which empties its result slot. The
 * answer menu goes back to that screen when it closes, so it isn't removed.
 */
@Mixin(Gui.class)
abstract class GuiMixin {
    @WrapWithCondition(method = "setScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;removed()V"))
    private boolean oboecraft$keepMenuScreenUnderAnswerMenu(Screen replaced, @Nullable Screen screen) {
        return !(screen instanceof AnswerScreen answerMenu && answerMenu.menuScreen() == replaced);
    }
}
