package io.github.willdalh.oboecraft.fabric.lock;

import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.network.ShowReview;
import io.github.willdalh.oboecraft.fabric.review.ServerReviews;
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
     * Checks an action that would interact with these stacks. When the lock check stops it, the client is sent the
     * stack's real place again, since it may already have used up the stack or moved it, and unless a review is going,
     * it is told to open the answer menu for the stack. The resend comes first, so the client finds the stack where
     * the server says it is. {@link ServerReviews} remembers the location,
     * so the review the client then starts there needs no second check.
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
        switch (location) {
            case StackLocation.InventorySlot slot -> player.connection.send(new ClientboundSetPlayerInventoryPacket(
                    slot.index(), player.getInventory().getItem(slot.index()).copy()));
            case StackLocation.MenuSlot menuSlot -> player.containerMenu.sendAllDataToRemote();
            case StackLocation.Carried carried -> player.containerMenu.sendAllDataToRemote();
        }
        ServerReviews reviews = OboecraftMod.serverReviews();
        if (!reviews.isReviewing(player)) {
            reviews.serverStopped(player, location);
            ServerPlayNetworking.send(player, new ShowReview(OboecraftMod.clock().instant(), location));
        }
        return true;
    }

    /**
     * Whether the lock check would stop an interaction with the stack, without stopping anything or telling the
     * client. For moves the server makes itself, which skip a locked stack and tell the client once afterwards.
     */
    public static boolean wouldStop(ServerPlayer player, CheckedStack checked) {
        return OboecraftMod.locks().firstStopped(OboecraftMod.serverProgress(), player, List.of(checked)).isPresent();
    }

    /**
     * Whether an action that could change the current view is refused because the player has a review going, so the
     * current view stays as it was when the review started. A normal client can't act during a review, since the
     * answer menu covers the screen, so this only stops clients that don't play by the rules.
     */
    public static boolean refusedDuringReview(ServerPlayer player) {
        return OboecraftMod.serverReviews().isReviewing(player);
    }

    /**
     * Checks a click in the player's open menu. While a review is going, every click is refused, see
     * {@link #refusedDuringReview}.
     *
     * @return whether the click is stopped
     */
    public static boolean stopsClick(
            ServerPlayer player, AbstractContainerMenu menu, int slot, int button, ContainerInput input) {
        return refusedDuringReview(player) || stops(player, MenuClicks.stacksOf(menu, slot, button, input, player));
    }
}
