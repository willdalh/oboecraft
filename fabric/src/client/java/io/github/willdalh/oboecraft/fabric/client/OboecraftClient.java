package io.github.willdalh.oboecraft.fabric.client;

import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.client.display.ClientDisplayHooks;
import io.github.willdalh.oboecraft.fabric.client.display.PassNotification;
import io.github.willdalh.oboecraft.fabric.client.settings.ClientSettings;
import io.github.willdalh.oboecraft.fabric.client.settings.ClientSettingsFile;
import io.github.willdalh.oboecraft.fabric.network.ProgressSync;
import io.github.willdalh.oboecraft.fabric.network.ProtocolCheck;
import io.github.willdalh.oboecraft.fabric.network.ReviewAnswered;
import io.github.willdalh.oboecraft.fabric.network.ReviewCancelled;
import io.github.willdalh.oboecraft.fabric.network.ReviewRefused;
import io.github.willdalh.oboecraft.fabric.network.ReviewStarted;
import io.github.willdalh.oboecraft.fabric.network.ShowReview;
import io.github.willdalh.oboecraft.fabric.network.StateUpdate;
import java.time.Instant;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Sets up the parts of the mod that only run on the client. */
public final class OboecraftClient implements ClientModInitializer {
    private static ClientProgress progress;
    private static ClientReviews reviews;
    private static PassNotification notification;
    private static ReadingClips readingClips;
    private static ClientSettings clientSettings;

    @Override
    public void onInitializeClient() {
        progress = new ClientProgress();
        clientSettings = ClientSettingsFile.read(ClientSettingsFile.path());
        notification = new PassNotification();
        readingClips = new ReadingClips();
        reviews = new ClientReviews(
                OboecraftMod.itemMapping(), OboecraftMod.locks(), progress, notification, readingClips);
        registerReceivers();
        ReviewKeys.register();
        ClientLockHooks.register(reviews);
        ClientDisplayHooks.register();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            progress.clear();
            notification.clear();
            readingClips.stop();
        });
        OboecraftMod.LOGGER.info("Oboecraft client initialized");
    }

    /** The SRS states the server sent. Client game tests read them. */
    public static ClientProgress progress() {
        return progress;
    }

    /** The client settings, which the answer menu reads. Only used on the client thread. */
    public static ClientSettings clientSettings() {
        return clientSettings;
    }

    /** Uses new client settings from now on. The settings screen saves them first. */
    public static void useClientSettings(ClientSettings settings) {
        clientSettings = java.util.Objects.requireNonNull(settings, "settings");
    }

    /** The notification after a pass, which the GUI draws and client game tests read. */
    public static PassNotification notification() {
        return notification;
    }

    /** The client's side of reviews, which the client's interaction hooks call. */
    public static ClientReviews reviews() {
        return reviews;
    }

    /** Fabric runs these on the client thread. Each message keeps the client's idea of the server's time fresh. */
    private static void registerReceivers() {
        // Registering a receiver tells the server this client runs the same network protocol. It is never sent.
        ClientConfigurationNetworking.registerGlobalReceiver(ProtocolCheck.TYPE, (payload, context) -> {
        });
        ClientPlayNetworking.registerGlobalReceiver(ProgressSync.TYPE, (payload, context) ->
                progress.sync(context.player().connection, payload.progress(), payload.settings(),
                        payload.serverTime()));
        ClientPlayNetworking.registerGlobalReceiver(StateUpdate.TYPE, (payload, context) ->
                progress.update(context.player().connection, payload.item(), payload.state(), payload.serverTime()));
        ClientPlayNetworking.registerGlobalReceiver(ShowReview.TYPE, (payload, context) -> {
            updateServerTime(context, payload.serverTime());
            reviews.showReview(payload.location());
        });
        ClientPlayNetworking.registerGlobalReceiver(ReviewStarted.TYPE, (payload, context) -> {
            updateServerTime(context, payload.serverTime());
            reviews.reviewStarted(payload.reviewId(), payload.item(), payload.level(), payload.attemptsLeft());
        });
        ClientPlayNetworking.registerGlobalReceiver(ReviewRefused.TYPE, (payload, context) -> {
            updateServerTime(context, payload.serverTime());
            reviews.reviewRefused(payload.reviewId(), payload.reason());
        });
        ClientPlayNetworking.registerGlobalReceiver(ReviewAnswered.TYPE, (payload, context) -> {
            updateServerTime(context, payload.serverTime());
            reviews.reviewAnswered(payload.reviewId(), payload.outcome(), payload.answer());
        });
        ClientPlayNetworking.registerGlobalReceiver(ReviewCancelled.TYPE, (payload, context) -> {
            updateServerTime(context, payload.serverTime());
            reviews.reviewEnded(payload.reviewId());
        });
    }

    private static void updateServerTime(ClientPlayNetworking.Context context, Instant serverTime) {
        progress.updateServerTime(context.player().connection, serverTime);
    }
}
