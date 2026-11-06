package io.github.willdalh.oboecraft.fabric.mixin;

import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Arrows and thrown tridents are pickups too, even though they aren't item entities. */
@Mixin(AbstractArrow.class)
abstract class ArrowPickupMixin {
    @Shadow protected abstract ItemStack getPickupItem();

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void oboecraft$checkPickup(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer
                && !OboecraftMod.locks().mayPickUp(OboecraftMod.serverProgress(), serverPlayer, getPickupItem())) {
            ci.cancel();
        }
    }
}
