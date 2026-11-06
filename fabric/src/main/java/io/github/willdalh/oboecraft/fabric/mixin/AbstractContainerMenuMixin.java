package io.github.willdalh.oboecraft.fabric.mixin;

import io.github.willdalh.oboecraft.fabric.lock.ServerLockHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Checks clicks in a menu on the server, before Minecraft carries them out. A stopped click changes nothing, and
 * Minecraft then sends the client the menu's real contents, which undoes what the client predicted.
 */
@Mixin(AbstractContainerMenu.class)
abstract class AbstractContainerMenuMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void oboecraft$checkLockBeforeClick(
            int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer && ServerLockHooks.stopsClick(
                serverPlayer, (AbstractContainerMenu) (Object) this, slot, button, input)) {
            ci.cancel();
        }
    }
}
