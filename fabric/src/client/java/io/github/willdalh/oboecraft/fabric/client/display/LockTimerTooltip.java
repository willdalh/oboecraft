package io.github.willdalh.oboecraft.fabric.client.display;

import io.github.willdalh.oboecraft.core.srs.LockTimer;
import java.util.Optional;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/** Adds the timer without discarding an item's own tooltip image, such as a bundle's contents. */
public record LockTimerTooltip(LockTimer timer, Optional<TooltipComponent> original) implements TooltipComponent {
}
