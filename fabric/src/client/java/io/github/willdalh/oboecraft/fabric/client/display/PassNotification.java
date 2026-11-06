package io.github.willdalh.oboecraft.fabric.client.display;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

/**
 * The notification after a pass: the item's Japanese name as plain text, centered at the top of the screen. It is
 * shown for a few seconds and fades out at the end. A new pass replaces it. Only used on the client thread.
 */
public final class PassNotification {
    /** How long the notification is shown, in client ticks: 3 seconds. */
    public static final int SHOWN_TICKS = 60;
    /** The last part of {@link #SHOWN_TICKS}, in which it fades out: 1 second. */
    private static final int FADE_TICKS = 20;
    private static final float SCALE = 2;
    private static final int TOP = 12;
    /** Text at an alpha below this is drawn opaque by Minecraft's font, so it isn't drawn at all. */
    private static final float MIN_ALPHA = 4 / 255f;

    private @Nullable String japaneseName;
    private int ticksLeft;

    /** Shows {@code japaneseName}, replacing the notification that is shown, if any. */
    public void show(String japaneseName) {
        this.japaneseName = Objects.requireNonNull(japaneseName, "japaneseName");
        ticksLeft = SHOWN_TICKS;
    }

    /** The Japanese name that is shown now. */
    public Optional<String> shown() {
        return Optional.ofNullable(japaneseName);
    }

    /** Counts down the time left, once every client tick. */
    public void tick() {
        if (japaneseName != null && --ticksLeft <= 0) {
            clear();
        }
    }

    /** Hides the notification, as when the player leaves the world or server. */
    public void clear() {
        japaneseName = null;
        ticksLeft = 0;
    }

    /** Draws the notification as a HUD element, so it is hidden with the HUD. */
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (japaneseName == null) {
            return;
        }
        float alpha = alpha(ticksLeft - delta.getGameTimeDeltaPartialTick(false));
        if (alpha < MIN_ALPHA) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().scale(SCALE);
        graphics.centeredText(Minecraft.getInstance().font, japaneseName,
                Math.round(graphics.guiWidth() / 2f / SCALE), Math.round(TOP / SCALE), ARGB.white(alpha));
        graphics.pose().popMatrix();
    }

    /** Fully opaque until the last {@link #FADE_TICKS}, then fading linearly to nothing. */
    private static float alpha(float ticksLeft) {
        return Math.clamp(ticksLeft / FADE_TICKS, 0f, 1f);
    }
}
