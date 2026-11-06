package io.github.willdalh.oboecraft.fabric.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.willdalh.oboecraft.fabric.client.OboecraftClient;
import io.github.willdalh.oboecraft.fabric.client.display.ItemDisplay;
import net.minecraft.client.DeltaTracker;
import io.github.willdalh.oboecraft.fabric.client.AnswerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps an open menu's screen intact while the answer menu opens over it. Replacing a screen tells it that it is
 * removed, and a menu's screen then runs the menu's {@code removed} on the client, which empties its result slot. The
 * answer menu goes back to that screen when it closes, so it isn't removed. Captures display progress and time once
 * around the GUI's frame extraction, and draws the notification after a pass on top of the HUD and the screen.
 */
@Mixin(Gui.class)
abstract class GuiMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @WrapMethod(method = "extractRenderState")
    private void oboecraft$captureDisplayFrame(DeltaTracker delta, boolean renderLevel, boolean renderScreen, Operation<Void> original) {
        ItemDisplay.inFrame(() -> original.call(delta, renderLevel, renderScreen));
    }

    @WrapWithCondition(method = "setScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;removed()V"))
    private boolean oboecraft$keepMenuScreenUnderAnswerMenu(Screen replaced, @Nullable Screen screen) {
        return !(screen instanceof AnswerScreen answerMenu && answerMenu.menuScreen() == replaced);
    }

    /** After the screen, so a menu's screen doesn't dim it, and before the toasts and the debug overlay. */
    @Inject(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;extractSavingIndicator(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    private void oboecraft$drawPassNotification(DeltaTracker delta, boolean renderLevel, boolean renderScreen,
            CallbackInfo ci, @Local GuiGraphicsExtractor graphics) {
        OboecraftClient.notification().extractRenderState(minecraft, graphics);
    }
}
