package io.github.willdalh.oboecraft.fabric.client.settings;

/**
 * The settings each player chooses on their own computer.
 *
 * @param clickableButtons whether the answer menu shows a button for each action, besides the keys
 * @param reviewItemScalePercent the size of the item above the answer field, relative to its normal size
 */
public record ClientSettings(boolean clickableButtons, int reviewItemScalePercent) {
    public static final int MIN_REVIEW_ITEM_SCALE = 50;
    public static final int MAX_REVIEW_ITEM_SCALE = 400;
    public static final int DEFAULT_REVIEW_ITEM_SCALE = 100;

    /** The default client settings, used when no client settings file says otherwise. */
    public static final ClientSettings DEFAULTS = new ClientSettings(false, DEFAULT_REVIEW_ITEM_SCALE);

    public ClientSettings {
        if (reviewItemScalePercent < MIN_REVIEW_ITEM_SCALE || reviewItemScalePercent > MAX_REVIEW_ITEM_SCALE) {
            throw new IllegalArgumentException("Review item size must be from " + MIN_REVIEW_ITEM_SCALE
                    + "% to " + MAX_REVIEW_ITEM_SCALE + "%");
        }
    }
}
