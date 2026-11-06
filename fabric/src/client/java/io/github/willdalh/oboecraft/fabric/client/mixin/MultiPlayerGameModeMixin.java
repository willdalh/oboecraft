package io.github.willdalh.oboecraft.fabric.client.mixin;

import io.github.willdalh.oboecraft.fabric.client.OboecraftClient;
import io.github.willdalh.oboecraft.fabric.lock.HeldStacks;
import io.github.willdalh.oboecraft.fabric.lock.MenuClicks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Checks menu clicks, dropping with the drop key and stabbing with a spear on the client, before the client predicts
 * them and sends them to the server. A stopped action is never sent, and the server checks every action it gets.
 */
@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
    @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
    private void oboecraft$checkLockBeforeClick(
            int containerId, int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
        if (player instanceof LocalPlayer localPlayer && player.containerMenu.containerId == containerId
                && OboecraftClient.reviews().stops(
                        localPlayer, MenuClicks.stacksOf(player.containerMenu, slot, button, input, player))) {
            ci.cancel();
        }
    }

    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
    private void oboecraft$checkLockBeforeDropping(LocalPlayer player, boolean all, CallbackInfo ci) {
        if (OboecraftClient.reviews().stops(player, HeldStacks.dropping(player))) {
            ci.cancel();
        }
    }

    @Inject(method = "piercingAttack", at = @At("HEAD"), cancellable = true)
    private void oboecraft$checkLockBeforeStabbing(
            SwingAnimation swingAnimation, PiercingWeapon weapon, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && OboecraftClient.reviews().stops(player, HeldStacks.attacking(player))) {
            ci.cancel();
        }
    }
}
