package io.github.willdalh.oboecraft.core.srs;

import java.time.Duration;
import java.util.Objects;

/**
 * How long the notification after a pass is shown, and how it fades out, in real time since it was shown. A fixed
 * rule, not a setting.
 */
public final class NotificationTiming {
    /** How long the notification is shown. */
    public static final Duration SHOWN = Duration.ofSeconds(3);
    /** The last part of {@link #SHOWN}, in which it fades out. */
    public static final Duration FADE = Duration.ofSeconds(1);

    private NotificationTiming() {
    }

    /** Whether the notification is over, {@code elapsed} after it was shown. */
    public static boolean isOver(Duration elapsed) {
        return elapsed.compareTo(SHOWN) >= 0;
    }

    /**
     * How opaque the notification is, {@code elapsed} after it was shown: 1 until the fade starts, then falling
     * linearly to 0 when it is over.
     */
    public static float opacity(Duration elapsed) {
        Objects.requireNonNull(elapsed, "elapsed");
        if (isOver(elapsed)) {
            return 0;
        }
        Duration left = SHOWN.minus(elapsed);
        return left.compareTo(FADE) >= 0 ? 1 : (float) left.toNanos() / FADE.toNanos();
    }
}
