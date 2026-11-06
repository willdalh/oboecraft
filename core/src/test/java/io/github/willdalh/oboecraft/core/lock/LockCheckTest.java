package io.github.willdalh.oboecraft.core.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LockCheckTest {
    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");
    private static final SrsState UNLOCKED = new SrsState.Leveled(1, Optional.of(NOW.plusSeconds(60)));
    private static final SrsState LOCKED = new SrsState.Leveled(1, Optional.of(NOW));
    private static final SrsState BANNED = new SrsState.Leveled(
            1, Optional.of(NOW.minusSeconds(60)), true, 0, Optional.of(NOW.plusSeconds(1)));
    private static final Settings DROP_IS_INTERACTION = Settings.DEFAULTS.withDropCountsAsInteraction(true);

    private static LockDecision check(SrsState state, Interaction interaction, boolean locksActive) {
        return LockCheck.check(state, interaction, NOW, locksActive, Settings.DEFAULTS);
    }

    @ParameterizedTest
    @EnumSource(Interaction.class)
    void everythingIsAllowedWhenLocksArentActive(Interaction interaction) {
        assertEquals(LockDecision.ALLOW, check(LOCKED, interaction, false));
        assertEquals(LockDecision.ALLOW, check(SrsState.Leveled.NEW, interaction, false));
        assertEquals(LockDecision.ALLOW,
                LockCheck.check(LOCKED, interaction, NOW, false, DROP_IS_INTERACTION));
    }

    @ParameterizedTest
    @EnumSource(Interaction.class)
    void anUnlockedItemIsAllowed(Interaction interaction) {
        assertEquals(LockDecision.ALLOW, check(UNLOCKED, interaction, true));
    }

    @ParameterizedTest
    @EnumSource(value = Interaction.class, names = {"DROP", "PICK_UP"}, mode = EnumSource.Mode.EXCLUDE)
    void aLockedItemOpensTheAnswerMenu(Interaction interaction) {
        assertEquals(LockDecision.REVIEW, check(LOCKED, interaction, true));
    }

    @Test
    void aNewItemOpensTheAnswerMenu() {
        assertEquals(LockDecision.REVIEW, check(SrsState.Leveled.NEW, Interaction.PLACE, true));
    }

    @Test
    void aLockedItemCanBeDroppedWhenDroppingIsntAnInteraction() {
        assertEquals(LockDecision.ALLOW, check(LOCKED, Interaction.DROP, true));
        assertEquals(LockDecision.ALLOW, check(SrsState.Leveled.NEW, Interaction.DROP, true));
    }

    @Test
    void droppingALockedItemOpensTheAnswerMenuWhenDroppingIsAnInteraction() {
        assertEquals(LockDecision.REVIEW,
                LockCheck.check(LOCKED, Interaction.DROP, NOW, true, DROP_IS_INTERACTION));
        assertEquals(LockDecision.ALLOW,
                LockCheck.check(UNLOCKED, Interaction.DROP, NOW, true, DROP_IS_INTERACTION));
    }

    @ParameterizedTest
    @EnumSource(value = Interaction.class, names = {"DROP", "PICK_UP"}, mode = EnumSource.Mode.EXCLUDE)
    void aBannedItemIsStoppedWithoutTheAnswerMenu(Interaction interaction) {
        assertEquals(LockDecision.BANNED, check(BANNED, interaction, true));
    }

    @Test
    void aBannedItemCanBePickedUpAndDroppedAndIsAllowedWhenLocksArentActive() {
        assertEquals(LockDecision.ALLOW, check(BANNED, Interaction.PICK_UP, true));
        assertEquals(LockDecision.ALLOW, check(BANNED, Interaction.DROP, true));
        assertEquals(LockDecision.ALLOW, check(BANNED, Interaction.PLACE, false));
        assertEquals(LockDecision.BANNED, LockCheck.check(BANNED, Interaction.DROP, NOW, true, DROP_IS_INTERACTION));
    }

    @Test
    void anItemOpensTheAnswerMenuOnceItsBanHasEnded() {
        assertEquals(LockDecision.REVIEW,
                LockCheck.check(BANNED, Interaction.PLACE, NOW.plusSeconds(1), true, Settings.DEFAULTS));
    }

    @Test
    void aBanOnAnUnlockedItemDoesntMatter() {
        SrsState unlockedAfterBan = new SrsState.Leveled(
                1, Optional.of(NOW.plusSeconds(60)), false, 0, Optional.of(NOW.plusSeconds(30)));

        assertEquals(LockDecision.ALLOW, check(unlockedAfterBan, Interaction.PLACE, true));
    }
}
