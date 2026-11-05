package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.Oboecraft;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

public class ModLoadedGameTest {
    @GameTest
    public void modIsLoadedAndInitialized(GameTestHelper helper) {
        helper.assertTrue(FabricLoader.getInstance().isModLoaded(Oboecraft.MOD_ID),
                Component.literal("The mod is not loaded"));
        helper.assertTrue(OboecraftMod.isInitialized(),
                Component.literal("The mod's initializer has not run"));
        helper.succeed();
    }
}
