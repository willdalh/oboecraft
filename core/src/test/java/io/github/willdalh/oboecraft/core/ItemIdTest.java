package io.github.willdalh.oboecraft.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemIdTest {
    @ParameterizedTest
    @ValueSource(strings = {"minecraft:oak_log", "minecraft:diamond_sword", "another_mod:tools/example-1.0"})
    void preservesNamespacedIdentifiers(String value) {
        assertEquals(value, new ItemId(value).value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "oak_log", ":oak_log", "minecraft:", "Minecraft:oak_log",
            "minecraft:Oak_Log", "minecraft:oak log", "minecraft:oak:log"})
    void rejectsIdentifiersThatCannotComeFromTheRegistry(String value) {
        assertThrows(IllegalArgumentException.class, () -> new ItemId(value));
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> new ItemId(null));
    }
}
