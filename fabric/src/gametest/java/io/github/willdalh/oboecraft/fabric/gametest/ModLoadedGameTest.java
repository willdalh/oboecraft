package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.Oboecraft;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

public class ModLoadedGameTest {
    /** The server started with the mod, and the mod's main entrypoint is registered. */
    @GameTest
    public void mainEntrypointIsRegistered(GameTestHelper helper) {
        boolean registered = FabricLoader.getInstance()
                .getEntrypointContainers("main", ModInitializer.class)
                .stream()
                .anyMatch(container -> container.getProvider().getMetadata().getId().equals(Oboecraft.MOD_ID)
                        && container.getEntrypoint() instanceof OboecraftMod);
        helper.assertTrue(registered, Component.literal("OboecraftMod is not a main entrypoint of the mod"));
        helper.succeed();
    }
}
