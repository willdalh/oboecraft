package io.github.willdalh.oboecraft.fabric.client.settings;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** The player's preferred size for every item shown above the answer field. */
public final class ReviewItemScaleSlider extends AbstractSliderButton {
    public ReviewItemScaleSlider(int x, int y, int width, int scalePercent) {
        super(x, y, width, 20, Component.empty(),
                (double) (scalePercent - ClientSettings.MIN_REVIEW_ITEM_SCALE)
                        / (ClientSettings.MAX_REVIEW_ITEM_SCALE - ClientSettings.MIN_REVIEW_ITEM_SCALE));
        setTooltip(Tooltip.create(Component.translatable("oboecraft.settings.review_item_scale_hint")));
        updateMessage();
    }

    public int scalePercent() {
        return ClientSettings.MIN_REVIEW_ITEM_SCALE + (int) Math.round(value
                * (ClientSettings.MAX_REVIEW_ITEM_SCALE - ClientSettings.MIN_REVIEW_ITEM_SCALE));
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.translatable("oboecraft.settings.review_item_scale", scalePercent()));
    }

    @Override
    protected void applyValue() {
        // The settings screen applies and saves the value only when the player chooses Done.
    }
}
