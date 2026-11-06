package io.github.willdalh.oboecraft.fabric.network;

import io.github.willdalh.oboecraft.fabric.review.ServerReviews;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;

/** Registers the mod's payloads, the server's receivers, and the network protocol check. */
public final class ServerNetworking {
    private ServerNetworking() {
    }

    /** Registers every payload type. Both sides call this, so both know every payload. */
    public static void registerPayloadTypes() {
        PayloadTypeRegistry.clientboundConfiguration().register(ProtocolCheck.TYPE, ProtocolCheck.STREAM_CODEC);

        PayloadTypeRegistry.clientboundPlay().register(ProgressSync.TYPE, ProgressSync.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StateUpdate.TYPE, StateUpdate.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ShowReview.TYPE, ShowReview.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ReviewStarted.TYPE, ReviewStarted.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ReviewRefused.TYPE, ReviewRefused.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ReviewAnswered.TYPE, ReviewAnswered.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ReviewCancelled.TYPE, ReviewCancelled.STREAM_CODEC);

        PayloadTypeRegistry.serverboundPlay().register(StartReview.TYPE, StartReview.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SubmitAnswer.TYPE, SubmitAnswer.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CloseReview.TYPE, CloseReview.STREAM_CODEC);
    }

    /**
     * Registers the server's receivers. Fabric runs them on the server thread, and each one acts only for the player
     * who sent the payload.
     */
    public static void registerReceivers(ServerReviews reviews) {
        ServerPlayNetworking.registerGlobalReceiver(StartReview.TYPE,
                (payload, context) -> reviews.start(context.player(), payload.reviewId(), payload.location()));
        ServerPlayNetworking.registerGlobalReceiver(SubmitAnswer.TYPE,
                (payload, context) -> reviews.answer(context.player(), payload.reviewId(), payload.answer()));
        ServerPlayNetworking.registerGlobalReceiver(CloseReview.TYPE,
                (payload, context) -> reviews.close(context.player(), payload.reviewId()));
    }

    /**
     * Disconnects players whose client doesn't have the mod, or has another network protocol version. Fabric fires
     * this event once it knows which payloads the client can receive.
     */
    public static void registerProtocolCheck() {
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (!ServerConfigurationNetworking.canSend(handler, ProtocolCheck.TYPE)) {
                handler.disconnect(Component.translatableWithFallback("oboecraft.protocol.mismatch",
                        "This server needs the Oboecraft mod, with network protocol version %s. "
                                + "Install the same version of Oboecraft as the server.",
                        ProtocolCheck.PROTOCOL_VERSION));
            }
        });
    }
}
