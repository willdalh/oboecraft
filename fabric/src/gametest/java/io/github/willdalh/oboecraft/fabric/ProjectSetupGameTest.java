package io.github.willdalh.oboecraft.fabric;

import io.github.willdalh.oboecraft.core.ItemId;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;

public final class ProjectSetupGameTest {
    @GameTest
    public void loadsModAndPassesRegistryIdentifiersToCore(GameTestHelper helper) {
        helper.assertTrue(FabricLoader.getInstance().isModLoaded(Oboecraft.MOD_ID),
                "Oboecraft must load on the dedicated test server");
        ItemId oakLog = new ItemId(BuiltInRegistries.ITEM.getKey(Items.OAK_LOG).toString());
        helper.assertTrue(oakLog.value().equals("minecraft:oak_log"),
                "The Fabric adapter must pass a registry identifier to the core");
        helper.succeed();
    }
}
