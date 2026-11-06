package io.github.willdalh.oboecraft.fabric.client.display;

import net.fabricmc.fabric.api.client.rendering.v1.ExtractItemDecorationsCallback;

/** Registers the client-only slot marker drawing. */
public final class ClientDisplayHooks {
    public static final int TIMER_COLOR = 0xFF55CCCC;
    public static final int WARNING_COLOR = 0xFFFFBB44;

    private ClientDisplayHooks() {
    }

    public static void register() {
        ExtractItemDecorationsCallback.EVENT.register((graphics, font, stack, x, y) -> {
            if (ItemDisplay.closeToLocking(stack)) {
                graphics.fill(x, y, x + 5, y + 5, 0xFF171717);
                graphics.fill(x + 1, y + 1, x + 4, y + 4, WARNING_COLOR);
            }
        });
    }
}
