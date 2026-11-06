package io.github.willdalh.oboecraft.fabric.client.display;

import io.github.willdalh.oboecraft.core.Oboecraft;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ExtractItemDecorationsCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

/** Registers the client-only drawing: the slot marker and the notification after a pass. */
public final class ClientDisplayHooks {
    public static final int TIMER_COLOR = 0xFF55CCCC;
    public static final int WARNING_COLOR = 0xFFFFBB44;

    private ClientDisplayHooks() {
    }

    public static void register(PassNotification notification) {
        // Below the chat, so the notification never covers what other players write.
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(Oboecraft.MOD_ID, "pass_notification"), notification::extractRenderState);
        ClientTickEvents.END_CLIENT_TICK.register(client -> notification.tick());
        ExtractItemDecorationsCallback.EVENT.register((graphics, font, stack, x, y) -> {
            if (ItemDisplay.closeToLocking(stack)) {
                graphics.fill(x, y, x + 5, y + 5, 0xFF171717);
                graphics.fill(x + 1, y + 1, x + 4, y + 4, WARNING_COLOR);
            }
        });
    }
}
