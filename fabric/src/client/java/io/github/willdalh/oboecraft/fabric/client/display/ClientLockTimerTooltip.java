package io.github.willdalh.oboecraft.fabric.client.display;

import io.github.willdalh.oboecraft.core.srs.LockTimer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

/** A time label and draining bar. Vanilla tooltip images remain separate components. */
public final class ClientLockTimerTooltip implements ClientTooltipComponent {
    private static final int HEIGHT = 18;
    private final LockTimer timer;
    private final Component label;

    public ClientLockTimerTooltip(LockTimer timer) {
        this.timer = timer;
        label = Component.translatable("oboecraft.display.locks_in", TimerText.format(timer.remaining()));
    }

    @Override
    public int getHeight(Font font) {
        return HEIGHT;
    }

    @Override
    public int getWidth(Font font) {
        return Math.max(80, font.width(label));
    }

    @Override
    public void extractText(GuiGraphicsExtractor graphics, Font font, int x, int y) {
        graphics.text(font, label, x, y, 0xFFAAAAAA);
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        int barWidth = getWidth(font);
        int filled = Math.max(1, (int) Math.ceil(barWidth * timer.fractionRemaining()));
        graphics.fill(x, y + 11, x + barWidth, y + 15, 0xFF303030);
        graphics.fill(x, y + 11, x + filled, y + 15,
                timer.closeToLocking() ? ClientDisplayHooks.WARNING_COLOR : ClientDisplayHooks.TIMER_COLOR);
    }
}
