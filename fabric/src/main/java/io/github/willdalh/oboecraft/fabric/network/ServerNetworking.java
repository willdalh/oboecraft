package io.github.willdalh.oboecraft.fabric.network;

import io.github.willdalh.oboecraft.fabric.progress.ProgressUploads;
import io.github.willdalh.oboecraft.fabric.progress.ServerProgress;
import io.github.willdalh.oboecraft.fabric.review.ServerReviews;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;

/** Registers the mod's payloads, the server's receivers, and what the server does while a player is configured. */
public final class ServerNetworking {
    private ServerNetworking() {
    }

    /** Registers every payload type. Both sides call this, so both know every payload. */
    public static void registerPayloadTypes() {
        PayloadTypeRegistry.clientboundConfiguration().register(ProtocolCheck.TYPE, ProtocolCheck.STREAM_CODEC);
        PayloadTypeRegistry.clientboundConfiguration().register(RequestProgress.TYPE, RequestProgress.STREAM_CODEC);
        PayloadTypeRegistry.serverboundConfiguration()
                .registerLarge(UploadProgress.TYPE, UploadProgress.STREAM_CODEC, UploadProgress.MAX_SIZE);
        PayloadTypeRegistry.serverboundConfiguration()
                .register(ProgressUnavailable.TYPE, ProgressUnavailable.STREAM_CODEC);

        PayloadTypeRegistry.clientboundPlay().register(ProgressSync.TYPE, ProgressSync.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StateUpdate.TYPE, StateUpdate.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ShowReview.TYPE, ShowReview.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ReviewStarted.TYPE, ReviewStarted.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ReviewRefused.TYPE, ReviewRefused.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ReviewAnswered.TYPE, ReviewAnswered.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ReviewCancelled.TYPE, ReviewCancelled.STREAM_CODEC);

        PayloadTypeRegistry.serverboundPlay().register(ReviewAction.TYPE, ReviewAction.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RevertItem.TYPE, RevertItem.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(StartReview.TYPE, StartReview.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SubmitAnswer.TYPE, SubmitAnswer.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CloseReview.TYPE, CloseReview.STREAM_CODEC);
    }

    /**
     * Registers the server's receivers. Fabric runs them on the server thread, and each one acts only for the player
     * who sent the payload.
     */
    public static void registerReceivers(ServerReviews reviews, ProgressUploads uploads) {
        ServerConfigurationNetworking.registerGlobalReceiver(UploadProgress.TYPE,
                (payload, context) -> uploads.received(context.packetListener(), payload.progress()));
        ServerConfigurationNetworking.registerGlobalReceiver(ProgressUnavailable.TYPE,
                (payload, context) -> uploads.unavailable(context.packetListener(), payload.reason()));
        ServerPlayNetworking.registerGlobalReceiver(ReviewAction.TYPE,
                (payload, context) -> reviews.action(context.player(), payload.reviewId(), payload.action()));
        ServerPlayNetworking.registerGlobalReceiver(RevertItem.TYPE,
                (payload, context) -> reviews.revert(context.player(), payload.item()));
        ServerPlayNetworking.registerGlobalReceiver(StartReview.TYPE,
                (payload, context) -> reviews.start(context.player(), payload.reviewId(), payload.location()));
        ServerPlayNetworking.registerGlobalReceiver(SubmitAnswer.TYPE,
                (payload, context) -> reviews.answer(context.player(), payload.reviewId(), payload.answer()));
        ServerPlayNetworking.registerGlobalReceiver(CloseReview.TYPE,
                (payload, context) -> reviews.close(context.player(), payload.reviewId()));
    }

    /**
     * Disconnects players whose client doesn't have the mod, or has another network protocol version, and asks the
     * others for their own progress when the server uses it. Fabric fires this event once it knows which payloads the
     * client can receive. A player who goes back to configuration from the world already has their progress.
     */
    public static void registerConfiguration(ServerProgress progress, ProgressUploads uploads) {
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (!ServerConfigurationNetworking.canSend(handler, ProtocolCheck.TYPE)) {
                handler.disconnect(Component.translatableWithFallback("oboecraft.protocol.mismatch",
                        "This server needs the Oboecraft mod, with network protocol version %s. "
                                + "Install the same version of Oboecraft as the server.",
                        ProtocolCheck.PROTOCOL_VERSION));
            } else if (!ServerConfigurationNetworking.isReconfiguring(handler)) {
                uploads.configure(handler, progress.settings().useOwnProgress());
            }
        });
        ServerConfigurationConnectionEvents.DISCONNECT.register((handler, server) -> uploads.disconnected(handler));
    }
}
