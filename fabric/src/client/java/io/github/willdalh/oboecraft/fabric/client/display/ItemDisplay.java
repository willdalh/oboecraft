package io.github.willdalh.oboecraft.fabric.client.display;

import io.github.willdalh.oboecraft.core.lock.Interaction;
import io.github.willdalh.oboecraft.core.lock.LockCheck;
import io.github.willdalh.oboecraft.core.lock.LockDecision;
import io.github.willdalh.oboecraft.core.srs.LockTimer;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.client.OboecraftClient;
import io.github.willdalh.oboecraft.fabric.lock.Locks;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ExtractItemDecorationsCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Local display decisions, read afresh from the current connection and its estimate of server time. */
public record ItemDisplay(boolean hidden, Optional<LockTimer> timer) {
    private static final ItemDisplay NORMAL = new ItemDisplay(false, Optional.empty());
    private static final ItemDisplay HIDDEN = new ItemDisplay(true, Optional.empty());
    public static final int TIMER_COLOR = 0xFF55CCCC;
    public static final int WARNING_COLOR = 0xFFFFBB44;

    public static ItemDisplay of(ItemStack stack) {
        var player = Minecraft.getInstance().player;
        var progress = OboecraftClient.progress();
        if (player == null || progress == null || !Locks.active(progress, player)) {
            return NORMAL;
        }
        var entry = OboecraftMod.itemMapping().entryOf(stack);
        if (entry.isEmpty()) {
            return NORMAL;
        }
        var state = progress.state(player, entry.get().id());
        var now = progress.now();
        var settings = OboecraftMod.settings();
        if (LockCheck.check(state, Interaction.USE, now, true, settings) != LockDecision.ALLOW) {
            return HIDDEN;
        }
        return new ItemDisplay(false, LockTimer.of(state, now, settings));
    }

    /** Only callers displaying a tooltip or the selected hotbar item use this; stack names stay intact. */
    public Component name(Component original) {
        return hidden ? Component.literal("???").setStyle(original.getStyle()) : original;
    }

    public static void register() {
        ClientTooltipComponentCallback.EVENT.register(component -> component instanceof LockTimerTooltip tooltip
                ? new ClientLockTimerTooltip(tooltip) : null);
        ExtractItemDecorationsCallback.EVENT.register((graphics, font, stack, x, y) -> {
            if (of(stack).timer().map(LockTimer::closeToLocking).orElse(false)) {
                // Top left, clear of the stack count and durability bar. A dark border keeps it visible on icons.
                graphics.fill(x, y, x + 5, y + 5, 0xFF171717);
                graphics.fill(x + 1, y + 1, x + 4, y + 4, WARNING_COLOR);
            }
        });
    }
}
