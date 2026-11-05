package io.github.willdalh.oboecraft.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OboecraftTest {
    @Test
    void modIdIsAValidFabricModId() {
        // Fabric requires 2 to 64 characters: a lowercase letter, then lowercase letters, digits, - or _.
        assertTrue(Oboecraft.MOD_ID.matches("[a-z][a-z0-9_-]{1,63}"), Oboecraft.MOD_ID);
    }
}
