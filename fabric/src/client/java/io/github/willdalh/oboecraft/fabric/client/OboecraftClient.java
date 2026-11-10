package io.github.willdalh.oboecraft.fabric.client;

import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.core.item.LearningLanguage;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import io.github.willdalh.oboecraft.fabric.client.display.ClientDisplayHooks;
import io.github.willdalh.oboecraft.fabric.client.display.PassNotification;
import io.github.willdalh.oboecraft.fabric.client.mixin.ClientCommonPacketListenerImplAccessor;
import io.github.willdalh.oboecraft.fabric.client.settings.ClientSettings;
import io.github.willdalh.oboecraft.fabric.client.settings.ClientSettingsFile;
import io.github.willdalh.oboecraft.fabric.client.tsukumogami.TsukumogamiRenderer;
import io.github.willdalh.oboecraft.fabric.network.ProgressSync;
import io.github.willdalh.oboecraft.fabric.progress.ProgressStore;
import io.github.willdalh.oboecraft.fabric.network.ProtocolCheck;
import io.github.willdalh.oboecraft.fabric.network.ReviewAnswered;
import io.github.willdalh.oboecraft.fabric.network.ReviewCancelled;
import io.github.willdalh.oboecraft.fabric.network.ReviewRefused;
import io.github.willdalh.oboecraft.fabric.network.RequestProgress;
import io.github.willdalh.oboecraft.fabric.network.ReviewStarted;
import io.github.willdalh.oboecraft.fabric.network.ShowReview;
import io.github.willdalh.oboecraft.fabric.network.StateUpdate;
import java.time.Instant;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;

/** Sets up the parts of the mod that only run on the client. */
public final class OboecraftClient implements ClientModInitializer {
    private static ClientProgress progress;
    private static ProgressStore progressStore;
    private static ClientReviews reviews;
    private static PassNotification notification;
    private static ReadingClips readingClips;
    private static ClientSettings clientSettings;
    private static ItemIcons itemIcons;
    private static String lastGameLanguage;

    @Override
    public void onInitializeClient() {
        progress = new ClientProgress();
        progressStore = new ProgressStore(ProgressStore.folder(FabricLoader.getInstance().getGameDir()),
                ProgressStore.SAVE_DELAY, () -> showMessage(Component.translatable("oboecraft.progress.save_failed")));
        clientSettings = ClientSettingsFile.read(ClientSettingsFile.path());
        notification = new PassNotification();
        readingClips = new ReadingClips();
        itemIcons = new ItemIcons(OboecraftMod.itemMappings());
        reviews = new ClientReviews(
                OboecraftMod.itemMappings(), OboecraftMod.locks(), progress, notification, readingClips);
        registerReceivers();
        ReviewKeys.register();
        WordListButton.register();
        TsukumogamiRenderer.register();
        ClientLockHooks.register(reviews);
        ClientDisplayHooks.register();
        registerProgressStoreEvents();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> lastGameLanguage = client.options.languageCode);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || !progress.hasProgress(client.player)) return;
            String code = client.options.languageCode;
            if (!code.equals(lastGameLanguage)) {
                lastGameLanguage = code;
                showMessage(Component.translatable("oboecraft.learning_language.next_join",
                        Component.translatable("oboecraft.language." + LearningLanguage.fromGameLanguage(code).code())));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            progress.clear();
            notification.clear();
            readingClips.stop();
            itemIcons.clear();
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

    /** The icons of the items, which the word list draws and client game tests read. */
    public static ItemIcons itemIcons() {
        return itemIcons;
    }

    /** Plays the reading clips, after a pass and from the word list. Only used on the client thread. */
    public static ReadingClips readingClips() {
        return readingClips;
    }

    /** The client's side of reviews, which the client's interaction hooks call. */
    public static ClientReviews reviews() {
        return reviews;
    }

    /**
     * A session of the progress store belongs to a connection, from the server's request until the player leaves, also
     * when they leave while configured, and its file is saved when the game stops. The player is told about a broken
     * file once in the world.
     */
    private static void registerProgressStoreEvents() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> progressStore.end(handler.getConnection()));
        ClientConfigurationConnectionEvents.DISCONNECT.register((handler, client) ->
                progressStore.end(connection(handler)));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> progressStore.close());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> progressStore.takeBrokenFileKeptAs()
                .ifPresent(keptAs -> showMessage(Component.translatable("oboecraft.progress.broken", keptAs))));
    }

    private static Connection connection(ClientConfigurationPacketListenerImpl listener) {
        return ((ClientCommonPacketListenerImplAccessor) listener).oboecraft$connection();
    }

    /** Shows the player a message in chat, from any thread. */
    private static void showMessage(Component message) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> client.gui.chatListener().handleSystemMessage(message, false));
    }

    /**
     * Fabric runs the play receivers on the client thread, and the configuration receivers on the network thread. Each
     * play message keeps the client's idea of the server's time fresh.
     */
    private static void registerReceivers() {
        // Registering a receiver tells the server this client runs the same network protocol. It is never sent.
        ClientConfigurationNetworking.registerGlobalReceiver(ProtocolCheck.TYPE, (payload, context) -> {
        });
        ClientConfigurationNetworking.registerGlobalReceiver(RequestProgress.TYPE, (payload, context) ->
                context.client().execute(() -> progressStore.start(connection(context.packetListener()), context.client().getUser().getProfileId(),
                        LearningLanguage.fromGameLanguage(context.client().options.languageCode), context.responseSender()::sendPacket)));
        ClientPlayNetworking.registerGlobalReceiver(ProgressSync.TYPE, (payload, context) -> {
            ClientPacketListener connection = context.player().connection;
            progress.sync(connection, payload.language(), payload.progress(), payload.settings(), payload.serverTime());
            progressStore.changed(connection.getConnection(), payload.progress());
        });
        ClientPlayNetworking.registerGlobalReceiver(StateUpdate.TYPE, (payload, context) -> {
            ClientPacketListener connection = context.player().connection;
            progress.update(connection, payload.item(), payload.state(), payload.serverTime())
                    .ifPresent(updated -> progressStore.changed(connection.getConnection(), updated));
        });
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
