package io.github.willdalh.oboecraft.fabric.client;

import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import net.fabricmc.api.ClientModInitializer;

/** Sets up the parts of the mod that only run on the client. */
public final class OboecraftClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        OboecraftMod.LOGGER.info("Oboecraft client initialized");
    }
}
