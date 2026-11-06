package io.github.willdalh.oboecraft.fabric.lock;

import io.github.willdalh.oboecraft.core.lock.LockDecision;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.network.ShowReview;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** What the server does when an interaction hook gets a decision from the lock check. */
public final class ServerLockHooks {
    private ServerLockHooks() {
    }

    /**
     * Checks placing the stack in the player's hand. When the item is locked, the hand's slot is sent again, since the
     * client may already have placed the block and used up the stack. Unless a review is going, the client is also
     * told to open the answer menu.
     *
     * @return whether placing is stopped
     */
    public static boolean stopsPlacing(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        LockResult result = OboecraftMod.locks().check(OboecraftMod.serverProgress(), player, stack);
        if (result.decision() == LockDecision.ALLOW) {
            return false;
        }
        int slot = hand == InteractionHand.MAIN_HAND
                ? player.getInventory().getSelectedSlot()
                : Inventory.SLOT_OFFHAND;
        player.connection.send(new ClientboundSetPlayerInventoryPacket(slot, stack.copy()));
        if (OboecraftMod.serverReviews().activeItem(player).isEmpty()) {
            ServerPlayNetworking.send(player, new ShowReview(OboecraftMod.clock().instant(), hand));
        }
        return true;
    }
}
