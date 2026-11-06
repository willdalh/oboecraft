package io.github.willdalh.oboecraft.fabric.lock;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.lock.Interaction;
import io.github.willdalh.oboecraft.core.lock.LockCheck;
import io.github.willdalh.oboecraft.core.lock.LockDecision;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.item.ItemMapping;
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

    /** Decides what happens when the player tries {@code interaction} with {@code stack}, on {@code view}'s side. */
    public LockResult check(ProgressView view, Player player, ItemStack stack, Interaction interaction) {
        Optional<NameListEntry> entry = itemMapping.entryOf(stack);
        if (entry.isEmpty()) {
            return new LockResult(entry, LockDecision.ALLOW);
        }
        ItemId item = entry.get().id();
        boolean locksActive = !player.isCreative() && !player.isSpectator() && view.hasProgress(player);
        SrsState state = view.state(player, item);
        LockDecision decision =
                LockCheck.check(Optional.of(item), interaction, state, view.now(), locksActive, settings);
        return new LockResult(entry, decision);
    }
}
