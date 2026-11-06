package io.github.willdalh.oboecraft.core.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class RenamingTest {
    @Test
    void onlyANameTagMayBeRenamed() {
        assertTrue(Renaming.mayRename(Optional.of(new ItemId("item.minecraft.name_tag"))));
        assertFalse(Renaming.mayRename(Optional.of(new ItemId("item.minecraft.diamond_sword"))));
        assertFalse(Renaming.mayRename(Optional.of(new ItemId("block.minecraft.oak_log"))));
    }

    @Test
    void aStackThatIsntAnItemMayNotBeRenamed() {
        assertFalse(Renaming.mayRename(Optional.empty()));
    }
}
