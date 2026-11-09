package io.github.willdalh.oboecraft.core.tsukumogami;

/**
 * When it is night, from the sky's time of day in ticks, like Minecraft's day time. Night is the time a player can
 * sleep in clear weather. Weather doesn't make it night early.
 */
public final class Night {
    /** The length of a whole day, in ticks. */
    public static final long DAY_LENGTH = 24_000;
    /** The first tick of the day that is night. */
    public static final long START = 12_542;
    /** The first tick of the day after the night, at dawn. */
    public static final long END = 23_460;

    private Night() {
    }

    /** Whether it is night at the day time {@code dayTime}. */
    public static boolean isNight(long dayTime) {
        long timeOfDay = Math.floorMod(dayTime, DAY_LENGTH);
        return timeOfDay >= START && timeOfDay < END;
    }

    /**
     * Which night the day time {@code dayTime} is in, as the number of the day it starts on. Only meaningful while
     * {@link #isNight} is true.
     */
    public static long nightOf(long dayTime) {
        return Math.floorDiv(dayTime, DAY_LENGTH);
    }
}
