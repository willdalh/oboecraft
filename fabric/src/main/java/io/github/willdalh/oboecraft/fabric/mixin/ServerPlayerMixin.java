package io.github.willdalh.oboecraft.fabric.mixin;

import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.lock.HeldStacks;
import io.github.willdalh.oboecraft.fabric.lock.ServerLockHooks;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Checks dropping the selected stack with the drop key, and cancels a review whose menu closes on the server or whose
 * player changes to a game mode without locks.
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
    @Inject(method = "drop(Z)V", at = @At("HEAD"), cancellable = true)
    private void oboecraft$checkLockBeforeDropping(boolean all, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (ServerLockHooks.stops(player, HeldStacks.dropping(player))) {
            ci.cancel();
        }
    }

    /** Every way a menu closes on the server ends here: the player closing it, walking away, or another menu. */
    @Inject(method = "doCloseContainer", at = @At("HEAD"))
    private void oboecraft$cancelReviewOfClosingMenu(CallbackInfo ci) {
        OboecraftMod.serverReviews().menuClosing((ServerPlayer) (Object) this);
    }

    /** Every change of a player's game mode on the server goes through here. */
    @Inject(method = "setGameMode", at = @At("RETURN"))
    private void oboecraft$cancelReviewWithoutLocks(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            OboecraftMod.serverReviews().gameModeChanged((ServerPlayer) (Object) this);
        }
    }
}
