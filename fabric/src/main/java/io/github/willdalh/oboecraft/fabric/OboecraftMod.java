package io.github.willdalh.oboecraft.fabric;

import io.github.willdalh.oboecraft.core.Oboecraft;
import io.github.willdalh.oboecraft.fabric.item.ItemMapping;
import io.github.willdalh.oboecraft.fabric.item.NameListFile;
import io.github.willdalh.oboecraft.fabric.lock.Locks;
import io.github.willdalh.oboecraft.fabric.lock.ServerLockHooks;
import io.github.willdalh.oboecraft.fabric.network.ServerNetworking;
import io.github.willdalh.oboecraft.fabric.progress.ProgressUploads;
import io.github.willdalh.oboecraft.fabric.progress.ServerProgress;
import io.github.willdalh.oboecraft.fabric.review.ServerReviews;
import io.github.willdalh.oboecraft.fabric.settings.ServerSettingsFile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sets up the parts of the mod that run on both the server and the client, and holds the shared services. Game tests
 * get the services from here, and can replace the clock's source.
 */
public final class OboecraftMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(Oboecraft.MOD_ID);

    private static Services services;

    /** The services both sides share. The server's are only used on the server thread. */
    private record Services(
            ModClock clock,
            ItemMapping itemMapping,
            Locks locks,
            ServerProgress serverProgress,
            ServerReviews serverReviews) {
    }

    @Override
    public void onInitialize() {
        ModClock clock = new ModClock();
        ItemMapping itemMapping = new ItemMapping(NameListFile.load());
        ProgressUploads progressUploads = new ProgressUploads();
        ServerProgress serverProgress = new ServerProgress(clock);
        Locks locks = new Locks(itemMapping);
        ServerReviews serverReviews = new ServerReviews(itemMapping, locks, serverProgress);
        services = new Services(clock, itemMapping, locks, serverProgress, serverReviews);

        ServerNetworking.registerPayloadTypes();
        ServerNetworking.registerReceivers(serverReviews, progressUploads);
        ServerNetworking.registerConfiguration(progressUploads);
        ServerLockHooks.register();
        registerProgressEvents(serverProgress, progressUploads, serverReviews);

        LOGGER.info("Oboecraft initialized with {} items in the name list", itemMapping.nameList().size());
    }

    private static void registerProgressEvents(
            ServerProgress serverProgress, ProgressUploads progressUploads, ServerReviews serverReviews) {
        // Read before any player joins, so every player gets the server's settings with their progress.
        ServerLifecycleEvents.SERVER_STARTING.register(server ->
                serverProgress.useSettings(ServerSettingsFile.load(ServerSettingsFile.path())));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                progressUploads.take(handler.player.getUUID())
                        .ifPresent(progress -> serverProgress.start(handler.player, progress)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            serverReviews.forget(handler.player.getUUID());
            serverProgress.end(handler);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer player) {
                serverReviews.cancel(player);
            }
        });
    }

    public static ModClock clock() {
        return services.clock();
    }

    public static ItemMapping itemMapping() {
        return services.itemMapping();
    }

    public static Locks locks() {
        return services.locks();
    }

    public static ServerProgress serverProgress() {
        return services.serverProgress();
    }

    public static ServerReviews serverReviews() {
        return services.serverReviews();
    }
}
