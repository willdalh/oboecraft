package io.github.willdalh.oboecraft.fabric.client.display;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.rendering.v1.ExtractItemDecorationsCallback;

/** Registers the client-only slot marker drawing. */
public final class ClientDisplayHooks {
    public static final int TIMER_COLOR = 0xFF55CCCC;
    public static final int WARNING_COLOR = 0xFFFFBB44;

    public static final Identifier NEW_SPRITE = Identifier.fromNamespaceAndPath("oboecraft", "slot_marker/new");
    public static final Identifier OVERDUE_SPRITE = Identifier.fromNamespaceAndPath("oboecraft", "slot_marker/overdue");

    private ClientDisplayHooks() {
    }

    public static void register() {
        ExtractItemDecorationsCallback.EVENT.register((graphics, font, stack, x, y) -> {
            switch (ItemDisplay.slotMarker(stack)) {
                case NONE -> { }
                case NEW -> graphics.blitSprite(RenderPipelines.GUI_TEXTURED, NEW_SPRITE, x, y, 5, 7);
                case OVERDUE -> graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OVERDUE_SPRITE, x, y, 5, 7);
                case NEAR_LOCK -> {
                    graphics.fill(x, y, x + 5, y + 5, 0xFF171717);
                    graphics.fill(x + 1, y + 1, x + 4, y + 4, WARNING_COLOR);
                }
            }
        });
    }
}
