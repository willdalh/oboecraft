package io.github.willdalh.oboecraft.fabric.client.display;

import io.github.willdalh.oboecraft.core.srs.LockTimer;
import java.time.Duration;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/** A time label and draining bar, followed by the stack's original tooltip image if it has one. */
public final class ClientLockTimerTooltip implements ClientTooltipComponent {
    private static final int HEIGHT = 18;
    private final LockTimer timer;
    private final Component label;
    private final @Nullable ClientTooltipComponent original;

    public ClientLockTimerTooltip(LockTimerTooltip tooltip) {
        timer = tooltip.timer();
        label = Component.translatable("oboecraft.display.locks_in", timeText(timer.remaining()));
        original = tooltip.original().map(ClientTooltipComponent::create).orElse(null);
    }

    @Override
    public int getHeight(Font font) {
        return HEIGHT + (original == null ? 0 : original.getHeight(font));
    }

    @Override
    public int getWidth(Font font) {
        return Math.max(Math.max(80, font.width(label)), original == null ? 0 : original.getWidth(font));
    }

    @Override
    public boolean showTooltipWithItemInHand() {
        return original != null && original.showTooltipWithItemInHand();
    }

    @Override
    public void extractText(GuiGraphicsExtractor graphics, Font font, int x, int y) {
        graphics.text(font, label, x, y, 0xFFAAAAAA);
        if (original != null) {
            original.extractText(graphics, font, x, y + HEIGHT);
        }
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        int barWidth = getWidth(font);
        int filled = Math.max(1, (int) Math.ceil(barWidth * timer.fractionRemaining()));
        graphics.fill(x, y + 11, x + barWidth, y + 15, 0xFF303030);
        graphics.fill(x, y + 11, x + filled, y + 15,
                timer.closeToLocking() ? ItemDisplay.WARNING_COLOR : ItemDisplay.TIMER_COLOR);
        if (original != null) {
            original.extractImage(font, x, y + HEIGHT, width, height - HEIGHT, graphics);
        }
    }

    private static Component timeText(Duration remaining) {
        // Round up so an unlocked item never says zero seconds remain.
        long seconds = remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1);
        MutableComponent text = Component.empty();
        appendUnit(text, seconds / 86400, "days");
        appendUnit(text, seconds / 3600 % 24, "hours");
        appendUnit(text, seconds / 60 % 60, "minutes");
        appendUnit(text, seconds % 60, "seconds");
        return text;
    }

    private static void appendUnit(MutableComponent text, long value, String unit) {
        if (value > 0) {
            if (!text.getSiblings().isEmpty()) {
                text.append(" ");
            }
            text.append(Component.translatable("oboecraft.display." + unit, value));
        }
    }
}
