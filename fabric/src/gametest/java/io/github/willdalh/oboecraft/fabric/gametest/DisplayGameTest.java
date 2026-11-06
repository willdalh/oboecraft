package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.lock.Interaction;
import io.github.willdalh.oboecraft.core.settings.LevelSettings;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.srs.DisplayState;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.lock.CheckedStack;
import io.github.willdalh.oboecraft.fabric.lock.ProgressView;
import io.github.willdalh.oboecraft.fabric.lock.StackLocation;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class DisplayGameTest {
    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");

    private record FixedProgress(SrsState state, Settings settings) implements ProgressView {
        @Override public boolean hasProgress(Player player) { return true; }
        @Override public SrsState state(Player player, ItemId item) { return state; }
        @Override public Instant now() { return NOW; }
    }

    @GameTest
    public void displayAndInteractionsUseTheViewsSettings(GameTestHelper helper) {
        var player = TestPlayers.join(helper, UUID.randomUUID());
        try {
            var settings = Settings.DEFAULTS.withLevels(List.of(new LevelSettings(Duration.ofSeconds(100), 1)));
            var locks = OboecraftMod.locks();
            var stack = new ItemStack(Items.OAK_LOG);
            var mastered = new FixedProgress(new SrsState.Leveled(2, Optional.of(NOW.minusSeconds(1))), settings);
            helper.assertTrue(locks.display(mastered, player, stack) == DisplayState.Normal.INSTANCE,
                    "the configured top level controls display");
            helper.assertTrue(locks.firstStopped(mastered, player, List.of(new CheckedStack(stack, Interaction.USE,
                    new StackLocation.InventorySlot(0)))).isEmpty(), "the same top level controls interactions");
            var timed = new FixedProgress(new SrsState.Leveled(1, Optional.of(NOW.plusSeconds(20))), settings);
            var display = (DisplayState.Timed) locks.display(timed, player, stack);
            helper.assertTrue(display.timer().interval().equals(Duration.ofSeconds(100)), "display uses the view's interval");
            helper.assertTrue(!locks.closeToLocking(timed, player, stack), "marker uses the view's threshold");
            var timedWithDefaults = new FixedProgress(timed.state(), Settings.DEFAULTS);
            helper.assertTrue(locks.closeToLocking(timedWithDefaults, player, stack),
                    "default settings have a different threshold");
            helper.succeed();
        } finally {
            TestPlayers.leave(player);
        }
    }
}
