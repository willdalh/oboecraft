package io.github.willdalh.oboecraft.fabric.lock;

import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.lock.Interaction;
import io.github.willdalh.oboecraft.core.lock.LockCheck;
import io.github.willdalh.oboecraft.core.lock.LockDecision;
import io.github.willdalh.oboecraft.core.settings.Settings;
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
     * Decides what happens when the player tries {@code interaction} with {@code stack}, on {@code view}'s side. A
     * stack that isn't an item is always allowed.
     */
    private LockResult check(ProgressView view, Player player, ItemStack stack, Interaction interaction) {
        Optional<NameListEntry> entry = itemMapping.entryOf(stack);
        if (entry.isEmpty()) {
            return new LockResult(entry, LockDecision.ALLOW);
        }
        boolean locksActive = !player.isCreative() && !player.isSpectator() && view.hasProgress(player);
        LockDecision decision = LockCheck.check(
                view.state(player, entry.get().id()), interaction, view.now(), locksActive, settings);
        return new LockResult(entry, decision);
    }

    /**
     * The first of the stacks an action would interact with that the lock check stops, in order. If there is one,
     * the whole action is stopped.
     */
    public Optional<StoppedStack> firstStopped(ProgressView view, Player player, List<CheckedStack> stacks) {
        for (CheckedStack checked : stacks) {
            LockResult result = check(view, player, checked.stack(), checked.interaction());
            if (result.decision() != LockDecision.ALLOW) {
                return Optional.of(new StoppedStack(checked, result.entry().orElseThrow(), result.decision()));
            }
        }
        return Optional.empty();
    }
}
