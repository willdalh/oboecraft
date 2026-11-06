package io.github.willdalh.oboecraft.fabric.client.display;

import io.github.willdalh.oboecraft.core.srs.NotificationTiming;
import io.github.willdalh.oboecraft.fabric.client.AnswerScreen;
import io.github.willdalh.oboecraft.fabric.client.mixin.BossHealthOverlayAccessor;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * The notification after a pass: the item's Japanese name as plain text, centered at the top of the screen, below
 * any boss bars. It is shown for {@link NotificationTiming#SHOWN} of real time and fades out at the end. A new pass
 * replaces it. It is drawn after the screen, so a menu's screen that a review went back to doesn't dim it. Only used
 * on the client thread.
 */
public final class PassNotification {
    private static final float MAX_SCALE = 2;
    /** The space kept free on each side of the name, in GUI pixels. */
    private static final int SIDE_MARGIN = 4;
    /** The top of the name when no boss bar is shown, in GUI pixels. */
    private static final int TOP = 12;
    /** Vanilla's boss bars: the first bar's top, and the height of each bar with its name. */
    private static final int BOSS_BAR_TOP = 12;
    private static final int BOSS_BAR_STEP = 19;
    /** Vanilla draws a boss bar's name this far above the bar's top. */
    private static final int BOSS_BAR_NAME_OFFSET = 9;
    /** The space between the last boss bar and the name, in GUI pixels. */
    private static final int BOSS_BAR_GAP = 2;
    /** Text at an alpha below this is drawn opaque by Minecraft's font, so it isn't drawn at all. */
    private static final float MIN_ALPHA = 4 / 255f;

    private @Nullable String japaneseName;
    private long shownAtMillis;

    /** Shows {@code japaneseName}, replacing the notification that is shown, if any. */
    public void show(String japaneseName) {
        this.japaneseName = Objects.requireNonNull(japaneseName, "japaneseName");
        shownAtMillis = Util.getMillis();
    }

    /** The Japanese name that is shown now, until the notification is over. */
    public Optional<String> shown() {
        if (japaneseName != null && NotificationTiming.isOver(elapsed())) {
            clear();
        }
        return Optional.ofNullable(japaneseName);
    }

    /** Hides the notification, as when the player leaves the world or server. */
    public void clear() {
        japaneseName = null;
    }

    /**
     * Draws the notification on top of the HUD and the screen. Hidden with the HUD (F1), and while an answer menu is
     * open, so the next review's menu never shows the last item's name.
     */
    public void extractRenderState(Minecraft minecraft, GuiGraphicsExtractor graphics) {
        Optional<String> name = shown();
        if (name.isEmpty() || minecraft.gui.hud.isHidden() || minecraft.gui.screen() instanceof AnswerScreen) {
            return;
        }
        float alpha = NotificationTiming.opacity(elapsed());
        if (alpha < MIN_ALPHA) {
            return;
        }
        Font font = minecraft.font;
        int width = font.width(name.get());
        float scale = Math.min(MAX_SCALE, (graphics.guiWidth() - 2f * SIDE_MARGIN) / Math.max(1, width));
        int top = top(minecraft, graphics.guiHeight());
        graphics.pose().pushMatrix();
        graphics.pose().translate(graphics.guiWidth() / 2f, top);
        graphics.pose().scale(scale);
        graphics.text(font, name.get(), -width / 2, 0, ARGB.white(alpha));
        graphics.pose().popMatrix();
    }

    /** Below the boss bars that vanilla draws, which stop at a third of the screen's height. */
    private static int top(Minecraft minecraft, int guiHeight) {
        int bars = ((BossHealthOverlayAccessor) minecraft.gui.hud.getBossOverlay()).oboecraft$events().size();
        int nextBarTop = BOSS_BAR_TOP;
        for (int bar = 0; bar < bars; bar++) {
            nextBarTop += BOSS_BAR_STEP;
            if (nextBarTop >= guiHeight / 3) {
                break;
            }
        }
        return nextBarTop == BOSS_BAR_TOP ? TOP : nextBarTop - BOSS_BAR_NAME_OFFSET + BOSS_BAR_GAP;
    }

    private Duration elapsed() {
        return Duration.ofMillis(Util.getMillis() - shownAtMillis);
    }
}
