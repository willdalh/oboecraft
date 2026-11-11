package io.github.willdalh.oboecraft.core.tsukumogami;

import io.github.willdalh.oboecraft.core.settings.Settings;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The rules of the tsukumogami: when a player is haunted and their tsukumogami should exist, its health, its review
 * pause and its wait for safety. Whether a player can be followed at all, by game mode, dimension and difficulty, is a
 * Minecraft question, so the adapter decides it. These numbers are fixed rules, not settings.
 */
public final class TsukumogamiRules {
    /** Half a Minecraft day of elapsed game ticks after a kill. */
    public static final long KILL_RELIEF_TICKS = 12_000;
    /** Its health at the appear threshold and below. */
    public static final int BASE_HEALTH = 20;
    /** The health it gains for each overdue item above the appear threshold. */
    public static final int HEALTH_PER_ITEM = 6;
    /** The most health it can have. */
    public static final int MAX_HEALTH = 100;
    /** How long it waits after it was reviewed away before it can come back. */
    public static final Duration RETURN_DELAY = Duration.ofSeconds(30);
    /**
     * How long it waits after its owner joins, respawns, returns to the Overworld or to survival or adventure mode,
     * so it never appears on top of a player who just arrived.
     */
    public static final Duration ARRIVAL_DELAY = Duration.ofSeconds(10);
    /** How long it keeps standing still after a review ends, so its owner isn't hit the moment they are back. */
    public static final Duration STILL_AFTER_REVIEW = Duration.ofSeconds(2);
    /** How long its owner must be free of threats before attacks resume. */
    public static final Duration SAFE_AFTER_THREAT = Duration.ofSeconds(5);
    /** The range around its owner in which a targeting mob counts as a threat, in blocks. */
    public static final double THREAT_RANGE = 32;
    /** The most overdue items that orbit it. */
    public static final int MAX_ORBITING_ITEMS = 12;

    private TsukumogamiRules() {
    }

    /** Whether the tsukumogami is turned on. An appear threshold of 0 turns it off. */
    public static boolean isOn(Settings settings) {
        return settings.tsukumogamiAppearThreshold() > 0;
    }

    /**
     * Whether a player is haunted now. A player becomes haunted when the overdue count reaches the appear threshold,
     * and stays haunted until reviews bring it down to the fade threshold or below, so the tsukumogami doesn't appear
     * and fade around a single count. Nobody is haunted while the tsukumogami is turned off.
     *
     * @param wasHaunted   whether the player was haunted at the last check
     * @param overdueCount the player's overdue count
     */
    public static boolean isHaunted(boolean wasHaunted, int overdueCount, Settings settings) {
        return isOn(settings) && (wasHaunted
                ? overdueCount > settings.tsukumogamiFadeThreshold()
                : overdueCount >= settings.tsukumogamiAppearThreshold());
    }

    /**
     * Whether a player's tsukumogami should exist, for a player it can follow. It does while the player is haunted,
     * unless kill relief is active or it is waiting.
     *
     * @param haunted       whether the player is haunted, see {@link #isHaunted}
     * @param gameTime      the Overworld's elapsed game time, in ticks
     * @param killedAtTick  the game tick it was last killed at, if it was
     * @param waitsUntil    when it may appear again after being reviewed away or after its owner arrived
     */
    public static boolean shouldExist(boolean haunted, long gameTime, OptionalLong killedAtTick, Instant now,
            Instant waitsUntil) {
        Objects.requireNonNull(killedAtTick, "killedAtTick");
        return haunted
                && !(killedAtTick.isPresent() && gameTime - killedAtTick.getAsLong() < KILL_RELIEF_TICKS)
                && !now.isBefore(waitsUntil);
    }

    /**
     * Its max health for an overdue count: {@link #BASE_HEALTH} at the appear threshold, plus {@link #HEALTH_PER_ITEM}
     * for each overdue item above it, up to {@link #MAX_HEALTH}. A count below the appear threshold, while the player
     * is still haunted, gives the base health.
     */
    public static int maxHealth(int overdueCount, Settings settings) {
        long above = Math.max(0, (long) overdueCount - settings.tsukumogamiAppearThreshold());
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

    /** Threat protection is independent of the review freeze and its settings. */
    public static boolean waitsForSafety(Optional<Instant> lastThreatAt, Instant now) {
        return lastThreatAt.map(last -> now.isBefore(last.plus(SAFE_AFTER_THREAT))).orElse(false);
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
