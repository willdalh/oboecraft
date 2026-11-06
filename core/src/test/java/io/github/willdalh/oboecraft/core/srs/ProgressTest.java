package io.github.willdalh.oboecraft.core.srs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.willdalh.oboecraft.core.item.ItemId;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProgressTest {
    private static final ItemId OAK_LOG = new ItemId("block.minecraft.oak_log");
    private static final SrsState LEVEL_ONE =
            new SrsState.Leveled(1, Optional.of(Instant.parse("2026-11-06T12:10:00Z")));

    @Test
    void anItemWithNoStateIsNew() {
        assertEquals(SrsState.Leveled.NEW, Progress.EMPTY.state(OAK_LOG));
    }

    @Test
    void withReplacesTheItemsStateAndLeavesTheOldProgressAlone() {
        Progress progress = Progress.EMPTY.with(OAK_LOG, LEVEL_ONE);

        assertEquals(LEVEL_ONE, progress.state(OAK_LOG));
        assertEquals(SrsState.Leveled.NEW, Progress.EMPTY.state(OAK_LOG));
    }

    @Test
    void newStatesArentStored() {
        Progress progress = new Progress(Map.of(OAK_LOG, LEVEL_ONE)).with(OAK_LOG, SrsState.Leveled.NEW);

        assertTrue(progress.states().isEmpty());
    }
}
