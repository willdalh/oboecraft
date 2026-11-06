package io.github.willdalh.oboecraft.fabric.mixin;

import io.github.willdalh.oboecraft.fabric.lock.HeldStacks;
import io.github.willdalh.oboecraft.fabric.lock.ServerLockHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.PiercingWeapon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Checks a stab with a spear on the server, which attacks without going through {@code Player.attack}. */
@Mixin(PiercingWeapon.class)
abstract class PiercingWeaponMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void oboecraft$checkLockBeforeStabbing(LivingEntity attacker, EquipmentSlot hand, CallbackInfo ci) {
        if (attacker instanceof ServerPlayer player && ServerLockHooks.stops(player, HeldStacks.attacking(player))) {
            ci.cancel();
        }
    }
}
