package io.github.willdalh.oboecraft.fabric.client;

import io.github.willdalh.oboecraft.fabric.lock.HeldStacks;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionResult;

/**
 * The client's hooks that use Fabric's events: attacking, mining, and using a stack on an entity. On the client, a
 * failed result stops the action without sending it to the server.
 */
final class ClientLockHooks {
    private ClientLockHooks() {
    }

    static void register(ClientReviews reviews) {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                player instanceof LocalPlayer localPlayer
                        && reviews.stops(localPlayer, HeldStacks.attacking(localPlayer))
                        ? InteractionResult.FAIL
                        : InteractionResult.PASS);
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                player instanceof LocalPlayer localPlayer && reviews.stops(localPlayer, HeldStacks.mining(localPlayer))
                        ? InteractionResult.FAIL
                        : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                player instanceof LocalPlayer localPlayer
                        && reviews.stops(localPlayer, HeldStacks.usingOnEntity(localPlayer, hand))
                        ? InteractionResult.FAIL
                        : InteractionResult.PASS);
    }
}
