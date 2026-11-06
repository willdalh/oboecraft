package io.github.willdalh.oboecraft.fabric.client.display;

import java.time.Duration;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** The two largest nonzero time units, joined using the player's language. */
public final class TimerText {
    private TimerText() {
    }

    public static Component format(Duration remaining) {
        long seconds = remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1);
        long[] values = {seconds / 86400, seconds / 3600 % 24, seconds / 60 % 60, seconds % 60};
        String[] units = {"days", "hours", "minutes", "seconds"};
        MutableComponent text = Component.empty();
        int shown = 0;
        for (int i = 0; i < units.length && shown < 2; i++) {
            if (values[i] > 0) {
                if (shown > 0) {
                    text.append(Component.translatable("oboecraft.display.time_separator"));
                }
                text.append(Component.translatable("oboecraft.display." + units[i], values[i]));
                shown++;
            }
        }
        return text;
    }
}
