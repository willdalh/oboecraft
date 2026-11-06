package io.github.willdalh.oboecraft.fabric.client.display;

import io.github.willdalh.oboecraft.core.srs.DisplayState;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.client.OboecraftClient;
import io.github.willdalh.oboecraft.fabric.lock.ProgressView;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Gathers the client's frame inputs; the shared Locks service owns mapping and settings. */
public final class ItemDisplay {
    private static final ScopedValue<Frame> FRAME = ScopedValue.newInstance();

    private ItemDisplay() {
    }

    private record Frame(@Nullable Player player, @Nullable ProgressView progress) {
    }

    private static Frame capture() {
        var client = Minecraft.getInstance();
        // Search/indexing callers must never read mutable player or connection state off the client thread.
        if (!client.isSameThread() || OboecraftClient.progress() == null) {
            return new Frame(null, null);
        }
        return new Frame(client.player, OboecraftClient.progress().capture());
    }

    public static void inFrame(Runnable draw) {
        ScopedValue.where(FRAME, capture()).run(draw);
    }

    private static Frame frame() {
        return FRAME.isBound() ? FRAME.get() : capture();
    }

    public static DisplayState of(ItemStack stack) {
        Frame frame = frame();
        return frame.player() == null || frame.progress() == null ? DisplayState.Normal.INSTANCE
                : OboecraftMod.locks().display(frame.progress(), frame.player(), stack);
    }

    public static boolean closeToLocking(ItemStack stack) {
        Frame frame = frame();
        return frame.player() != null && frame.progress() != null
                && OboecraftMod.locks().closeToLocking(frame.progress(), frame.player(), stack);
    }

    public static Component name(ItemStack stack, Component original) {
        return of(stack) instanceof DisplayState.Hidden
                ? Component.literal("???").setStyle(original.getStyle()) : original;
    }
}
