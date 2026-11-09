package io.github.willdalh.oboecraft.core.tsukumogami;

import io.github.willdalh.oboecraft.core.settings.Settings;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The rules of the tsukumogami: when a player's tsukumogami should exist, its health and when it stands still for a
 * review. Whether a player can be followed at all, by game mode, dimension and difficulty, is a Minecraft question,
 * so the adapter decides it. These numbers are fixed rules, not settings.
 */
public final class TsukumogamiRules {
    /** Its health at the threshold. */
    public static final int BASE_HEALTH = 20;
    /** The health it gains for each overdue item above the threshold. */
    public static final int HEALTH_PER_ITEM = 6;
    /** The most health it can have. */
    public static final int MAX_HEALTH = 100;
    /** How long it waits after it was reviewed away before it can come back the same night. */
    public static final Duration RETURN_DELAY = Duration.ofSeconds(30);
    /**
     * How long it waits after its owner joins, respawns, returns to the Overworld or to survival or adventure mode,
     * so it never appears on top of a player who just arrived.
     */
    public static final Duration ARRIVAL_DELAY = Duration.ofSeconds(10);
    /** How long it keeps standing still after a review ends, so its owner isn't hit the moment they are back. */
    public static final Duration STILL_AFTER_REVIEW = Duration.ofSeconds(2);
    /** The most overdue items that orbit it. */
    public static final int MAX_ORBITING_ITEMS = 12;

    private TsukumogamiRules() {
    }

    /** Whether the tsukumogami is turned on. A threshold of 0 turns it off. */
    public static boolean isOn(Settings settings) {
        return settings.tsukumogamiThreshold() > 0;
    }

    /**
     * Whether a player's tsukumogami should exist, for a player it can follow. It does at night while the overdue
     * count is at the threshold or above, unless it was killed this night or is waiting.
     *
     * @param overdueCount  the player's overdue count
     * @param dayTime       the Overworld's day time, in ticks
     * @param killedOnNight the night it was last killed on, see {@link Night#nightOf}, if it was
     * @param waitsUntil    when it may appear again after being reviewed away or after its owner arrived
     */
    public static boolean shouldExist(int overdueCount, long dayTime, OptionalLong killedOnNight, Instant now,
            Instant waitsUntil, Settings settings) {
        Objects.requireNonNull(killedOnNight, "killedOnNight");
        return isOn(settings)
                && overdueCount >= settings.tsukumogamiThreshold()
                && Night.isNight(dayTime)
                && !(killedOnNight.isPresent() && killedOnNight.getAsLong() == Night.nightOf(dayTime))
                && !now.isBefore(waitsUntil);
    }

    /**
     * Its max health for an overdue count: {@link #BASE_HEALTH} at the threshold, plus {@link #HEALTH_PER_ITEM} for
     * each overdue item above it, up to {@link #MAX_HEALTH}. A count below the threshold gives the base health.
     */
    public static int maxHealth(int overdueCount, Settings settings) {
        long above = Math.max(0, (long) overdueCount - settings.tsukumogamiThreshold());
        return (int) Math.min(MAX_HEALTH, BASE_HEALTH + above * HEALTH_PER_ITEM);
    }

    /**
     * Its health after its max health changes from {@code oldMaxHealth} to {@code newMaxHealth}, keeping the same
     * fraction, so a pass in the middle of a fight weakens it at once.
     */
    public static float scaledHealth(float health, float oldMaxHealth, float newMaxHealth) {
        if (oldMaxHealth <= 0) {
            return newMaxHealth;
        }
        return Math.clamp(health / oldMaxHealth * newMaxHealth, 0, newMaxHealth);
    }

    /**
     * Whether it stands still and watches its owner instead of following and attacking. While its owner has a review
     * going, it does for at most the review freeze from the start of that review. After a review ends, it does for
     * {@link #STILL_AFTER_REVIEW}. With a review freeze of 0, it never does.
     *
     * @param reviewStartedAt when the owner's review that is going started, if one is
     * @param reviewEndedAt   when the owner's latest review ended, if one did
     */
    public static boolean standsStill(Optional<Instant> reviewStartedAt, Optional<Instant> reviewEndedAt, Instant now,
            Settings settings) {
        Duration freeze = settings.tsukumogamiReviewFreeze();
        if (freeze.isZero()) {
            return false;
        }
        if (reviewStartedAt.isPresent()) {
            return now.isBefore(reviewStartedAt.get().plus(freeze));
        }
        return reviewEndedAt.map(ended -> now.isBefore(ended.plus(STILL_AFTER_REVIEW))).orElse(false);
    }
}
