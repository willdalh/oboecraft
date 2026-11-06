package io.github.willdalh.oboecraft.fabric.mixin;

import io.github.willdalh.oboecraft.fabric.lock.ServerLockHooks;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Refuses, during a review, the packets other than menu clicks that move stacks in the open menu or out of the
 * inventory, so the current view stays as it was when the review started. Menu clicks are refused in
 * {@code AbstractContainerMenuMixin}. Each check runs once the handler is on the server thread, where reviews live.
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {
    private static final String ON_SERVER_THREAD = "Lnet/minecraft/network/protocol/PacketUtils;"
            + "ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;"
            + "Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V";

    @Shadow
    public ServerPlayer player;

    /** The recipe book moves ingredients into the crafting grid. The client predicts nothing, so nothing is resent. */
    @Inject(method = "handlePlaceRecipe", at = @At(value = "INVOKE", target = ON_SERVER_THREAD, shift = At.Shift.AFTER),
            cancellable = true)
    private void oboecraft$refuseRecipeDuringReview(ServerboundPlaceRecipePacket packet, CallbackInfo ci) {
        if (ServerLockHooks.refusedDuringReview(player)) {
            ci.cancel();
        }
    }

    /** Choosing a trade moves the payment. The client moved it too, so the menu's real contents are resent. */
    @Inject(method = "handleSelectTrade", at = @At(value = "INVOKE", target = ON_SERVER_THREAD, shift = At.Shift.AFTER),
            cancellable = true)
    private void oboecraft$refuseTradeDuringReview(ServerboundSelectTradePacket packet, CallbackInfo ci) {
        if (ServerLockHooks.refusedDuringReview(player)) {
            player.containerMenu.sendAllDataToRemote();
            ci.cancel();
        }
    }

    /** A menu's buttons, like enchanting, use up stacks. The client may have changed the menu, so it is resent. */
    @Inject(method = "handleContainerButtonClick",
            at = @At(value = "INVOKE", target = ON_SERVER_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void oboecraft$refuseMenuButtonDuringReview(ServerboundContainerButtonClickPacket packet, CallbackInfo ci) {
        if (ServerLockHooks.refusedDuringReview(player)) {
            player.containerMenu.sendAllDataToRemote();
            ci.cancel();
        }
    }

    /**
     * Dropping and swapping hands move stacks out of the hands. The client may already have dropped the stack, so
     * both hands are resent.
     */
    @Inject(method = "handlePlayerAction",
            at = @At(value = "INVOKE", target = ON_SERVER_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void oboecraft$refuseDropOrSwapDuringReview(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        boolean movesHeldStacks = switch (packet.getAction()) {
            case DROP_ITEM, DROP_ALL_ITEMS, SWAP_ITEM_WITH_OFFHAND -> true;
            default -> false;
        };
        if (movesHeldStacks && ServerLockHooks.refusedDuringReview(player)) {
            Inventory inventory = player.getInventory();
            player.connection.send(new ClientboundSetPlayerInventoryPacket(
                    inventory.getSelectedSlot(), inventory.getSelectedItem().copy()));
            player.connection.send(new ClientboundSetPlayerInventoryPacket(
                    Inventory.SLOT_OFFHAND, inventory.getItem(Inventory.SLOT_OFFHAND).copy()));
            ci.cancel();
        }
    }
}
