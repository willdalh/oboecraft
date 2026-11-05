package io.github.willdalh.oboecraft.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CoreIsolationTest {
    @ParameterizedTest
    @ValueSource(strings = {"net.minecraft.server.MinecraftServer", "net.fabricmc.api.ModInitializer"})
    void runsWithoutMinecraftOrFabric(String className) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className));
    }
}
