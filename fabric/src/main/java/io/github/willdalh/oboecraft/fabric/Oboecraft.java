package io.github.willdalh.oboecraft.fabric;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entrypoint used by both the client and dedicated server. */
public final class Oboecraft implements ModInitializer {
    public static final String MOD_ID = "oboecraft";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Oboecraft initialized");
    }
}
