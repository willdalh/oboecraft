package io.github.willdalh.oboecraft.fabric;

import io.github.willdalh.oboecraft.core.Oboecraft;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sets up the parts of the mod that run on both the server and the client. */
public final class OboecraftMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(Oboecraft.MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Oboecraft initialized");
    }
}
