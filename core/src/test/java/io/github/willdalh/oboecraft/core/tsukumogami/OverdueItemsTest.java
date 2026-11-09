package io.github.willdalh.oboecraft.core.tsukumogami;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OverdueItemsTest {
    private static final Instant NOW = Instant.parse("2026-11-09T22:00:00Z");
    private static final Settings SETTINGS = Settings.DEFAULTS;
    private static final ItemId OAK_LOG = new ItemId("block.minecraft.oak_log");
    private static final ItemId STONE = new ItemId("block.minecraft.stone");
    private static final ItemId DIRT = new ItemId("block.minecraft.dirt");
    private static final ItemId TORCH = new ItemId("block.minecraft.torch");

    private static SrsState.Leveled lockedAt(int level) {
        return new SrsState.Leveled(level, Optional.of(NOW.minusSeconds(1)));
    }

    @Test
    void aLockedItemFromLevelOneIsOverdue() {
        assertTrue(OverdueItems.isOverdue(lockedAt(1), NOW, SETTINGS));
        assertTrue(OverdueItems.isOverdue(lockedAt(8), NOW, SETTINGS));
    }

    @Test
    void anItemIsOverdueFromTheMomentItLocks() {
        SrsState locksNow = new SrsState.Leveled(2, Optional.of(NOW));

        assertTrue(OverdueItems.isOverdue(locksNow, NOW, SETTINGS));
        assertFalse(OverdueItems.isOverdue(locksNow, NOW.minusMillis(1), SETTINGS));
    }

    @Test
    void aBannedItemIsStillOverdue() {
        SrsState banned = new SrsState.Leveled(
                1, Optional.of(NOW.minusSeconds(60)), true, 0, Optional.of(NOW.plus(Duration.ofMinutes(5))));

        assertTrue(OverdueItems.isOverdue(banned, NOW, SETTINGS));
    }

    @Test
    void levelZeroUnlockedMasteredKnownAndIgnoredItemsArentOverdue() {
        assertFalse(OverdueItems.isOverdue(SrsState.Leveled.NEW, NOW, SETTINGS));
        assertFalse(OverdueItems.isOverdue(new SrsState.Leveled(3, Optional.of(NOW.plusSeconds(1))), NOW, SETTINGS));
        assertFalse(OverdueItems.isOverdue(new SrsState.Leveled(SETTINGS.topLevel(), Optional.empty()), NOW, SETTINGS));
        assertFalse(OverdueItems.isOverdue(new SrsState.Known(), NOW, SETTINGS));
        assertFalse(OverdueItems.isOverdue(new SrsState.Ignored(), NOW, SETTINGS));
    }

    @Test
    void eachOverdueItemCountsOnceInTheOrderItIsFirstCarried() {
        Map<ItemId, SrsState> states = Map.of(OAK_LOG, lockedAt(1), STONE, lockedAt(2), DIRT, SrsState.Leveled.NEW);

        List<ItemId> overdue = OverdueItems.of(List.of(STONE, DIRT, OAK_LOG, STONE, OAK_LOG, TORCH),
                item -> states.getOrDefault(item, SrsState.Leveled.NEW), NOW, SETTINGS);

        assertEquals(List.of(STONE, OAK_LOG), overdue);
    }

    @Test
    void nothingCarriedIsNothingOverdue() {
        assertEquals(List.of(), OverdueItems.of(List.of(), item -> lockedAt(1), NOW, SETTINGS));
    }
}
