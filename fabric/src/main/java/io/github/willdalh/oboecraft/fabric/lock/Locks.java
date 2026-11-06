package io.github.willdalh.oboecraft.fabric.lock;

import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.lock.LockCheck;
import io.github.willdalh.oboecraft.core.lock.LockDecision;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.srs.DisplayRules;
import io.github.willdalh.oboecraft.core.srs.DisplayState;
import io.github.willdalh.oboecraft.fabric.item.ItemMapping;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The lock check entry point that every interaction hook on both sides calls. It gathers the inputs from the game
 * and asks core {@link LockCheck}.
 */
public final class Locks {
    private final ItemMapping itemMapping;
    private final Settings settings;

    public Locks(ItemMapping itemMapping, Settings settings) {
        this.itemMapping = Objects.requireNonNull(itemMapping, "itemMapping");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * The first of the stacks an action would interact with that the lock check stops, in order. If there is one,
     * the whole action is stopped.
     */
    public Optional<StoppedStack> firstStopped(ProgressView view, Player player, List<CheckedStack> stacks) {
        for (CheckedStack checked : stacks) {
            Optional<StoppedStack> stopped = stopped(view, player, checked);
            if (stopped.isPresent()) {
                return stopped;
            }
        }
        return Optional.empty();
    }

    /** Whether locks and their displays apply to this player on this side of the connection. */
    public static boolean active(ProgressView view, Player player) {
        return !player.isCreative() && !player.isSpectator() && view.hasProgress(player);
    }

    /** Uses exactly the same mapping, game-mode guards and settings as interactions. */
    public DisplayState display(ProgressView view, Player player, ItemStack stack) {
        return activeEntry(view, player, stack)
                .map(entry -> DisplayRules.of(view.state(player, entry.id()), view.now(), settings))
                .orElse(DisplayState.Normal.INSTANCE);
    }

    /** The hot path for slot decorations: no timer or full display result is built. */
    public boolean closeToLocking(ProgressView view, Player player, ItemStack stack) {
        return activeEntry(view, player, stack)
                .map(entry -> DisplayRules.closeToLocking(view.state(player, entry.id()), view.now(), settings))
                .orElse(false);
    }

    private Optional<NameListEntry> activeEntry(ProgressView view, Player player, ItemStack stack) {
        return active(view, player) ? itemMapping.entryOf(stack) : Optional.empty();
    }

    /** The stack, if the lock check stops the player's interaction with it on {@code view}'s side. */
    private Optional<StoppedStack> stopped(ProgressView view, Player player, CheckedStack checked) {
        Optional<NameListEntry> entry = activeEntry(view, player, checked.stack());
        if (entry.isEmpty()) {
            return Optional.empty();
        }
        LockDecision decision = LockCheck.check(
                view.state(player, entry.get().id()), checked.interaction(), view.now(), true, settings);
        return decision == LockDecision.ALLOW
                ? Optional.empty()
                : Optional.of(new StoppedStack(checked, entry.get(), decision));
    }
}
