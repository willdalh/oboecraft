package io.github.willdalh.oboecraft.fabric.lock;

import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.network.ShowReview;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

/**
 * What the server does when an interaction hook asks the lock check. The server has the final say: it checks every
 * action again, also those the client already stopped, since Minecraft sends some of them anyway.
 */
public final class ServerLockHooks {
    private ServerLockHooks() {
    }

    /** Registers the hooks that use Fabric's events: attacking, mining, and using a stack on an entity. */
    public static void register() {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                player instanceof ServerPlayer serverPlayer && stops(serverPlayer, HeldStacks.attacking(serverPlayer))
                        ? InteractionResult.FAIL
                        : InteractionResult.PASS);
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                player instanceof ServerPlayer serverPlayer && stops(serverPlayer, HeldStacks.mining(serverPlayer))
                        ? InteractionResult.FAIL
                        : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                player instanceof ServerPlayer serverPlayer
                        && stops(serverPlayer, HeldStacks.usingOnEntity(serverPlayer, hand))
                        ? InteractionResult.FAIL
                        : InteractionResult.PASS);
    }

    /**
     * Checks an action that would interact with these stacks. When the lock check stops it, a stack in the inventory
     * is sent again, since the client may already have used it up, and unless a review is going, the client is told
     * to open the answer menu. A menu click needs no such resend: Minecraft sends the menu's real contents after
     * every click.
     *
     * @return whether the action is stopped
     */
    public static boolean stops(ServerPlayer player, List<CheckedStack> stacks) {
        Optional<StoppedStack> stopped =
                OboecraftMod.locks().firstStopped(OboecraftMod.serverProgress(), player, stacks);
        if (stopped.isEmpty()) {
            return false;
        }
        StackLocation location = stopped.get().checked().location();
        if (location instanceof StackLocation.InventorySlot slot) {
            player.connection.send(new ClientboundSetPlayerInventoryPacket(
                    slot.index(), player.getInventory().getItem(slot.index()).copy()));
        }
        if (!OboecraftMod.serverReviews().isReviewing(player)) {
            ServerPlayNetworking.send(player, new ShowReview(OboecraftMod.clock().instant(), location));
        }
        return true;
    }

    /**
     * Checks a click in the player's open menu. While a review is going, every click is refused, so the current view
     * can't be rearranged before the review ends.
     *
     * @return whether the click is stopped
     */
    public static boolean stopsClick(
            ServerPlayer player, AbstractContainerMenu menu, int slot, int button, ContainerInput input) {
        return OboecraftMod.serverReviews().isReviewing(player)
                || stops(player, MenuClicks.stacksOf(menu, slot, button, input, player));
    }
}
