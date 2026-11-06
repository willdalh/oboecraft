package io.github.willdalh.oboecraft.core.settings;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Durations as people write them in the settings file and the settings screen: whole days, hours, minutes and seconds
 * from the largest unit down, separated by spaces, like {@code 10m}, {@code 1h 30m} or {@code 14d}.
 */
public final class DurationText {
    private static final char[] UNITS = {'d', 'h', 'm', 's'};
    private static final long[] UNIT_SECONDS = {86_400, 3_600, 60, 1};

    private DurationText() {
    }

    /**
     * Reads a duration. Each part is a whole number and a unit, {@code d}, {@code h}, {@code m} or {@code s}, and the
     * units come from the largest down, each at most once. {@code 0s} is zero.
     *
     * @throws IllegalArgumentException if the text isn't a duration
     */
    public static Duration parse(String text) {
        String trimmed = Objects.requireNonNull(text, "text").trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("A duration can't be empty");
        }
        long seconds = 0;
        int nextUnit = 0;
        for (String part : trimmed.split("\\s+")) {
            int unit = unitIndex(part.charAt(part.length() - 1));
            if (unit < nextUnit) {
                throw new IllegalArgumentException("The units in \"" + text + "\" must go from days down to seconds, "
                        + "each at most once");
            }
            String number = part.substring(0, part.length() - 1);
            if (number.isEmpty() || number.length() > 9 || !number.chars().allMatch(c -> c >= '0' && c <= '9')) {
                throw new IllegalArgumentException("\"" + part + "\" isn't a whole number with a unit");
            }
            seconds += Long.parseLong(number) * UNIT_SECONDS[unit];
            nextUnit = unit + 1;
        }
        return Duration.ofSeconds(seconds);
    }

    private static int unitIndex(char unit) {
        for (int index = 0; index < UNITS.length; index++) {
            if (UNITS[index] == unit) {
                return index;
            }
        }
        throw new IllegalArgumentException("A duration's unit must be d, h, m or s, not \"" + unit + "\"");
    }

    /**
     * Writes a duration in whole seconds, rounded down, with every unit that isn't zero, like {@code 1h 30m}. Zero is
     * {@code 0s}.
     *
     * @throws IllegalArgumentException if the duration is negative
     */
    public static String format(Duration duration) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException("A duration can't be negative, not " + duration);
        }
        long seconds = duration.toSeconds();
        List<String> parts = new ArrayList<>();
        for (int index = 0; index < UNITS.length; index++) {
            long count = seconds / UNIT_SECONDS[index];
            seconds %= UNIT_SECONDS[index];
            if (count > 0) {
                parts.add(count + String.valueOf(UNITS[index]));
            }
        }
        return parts.isEmpty() ? "0s" : String.join(" ", parts);
    }
}
