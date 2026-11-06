package io.github.willdalh.oboecraft.fabric.mixin;

import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A denied pickup leaves the entity on the ground for other players. */
@Mixin(ItemEntity.class)
abstract class ItemEntityMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void oboecraft$checkPickup(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer
                && !OboecraftMod.locks().mayPickUp(
                        OboecraftMod.serverProgress(), serverPlayer, ((ItemEntity) (Object) this).getItem())) {
            ci.cancel();
        }
    }
}
