package io.github.willdalh.oboecraft.fabric.client.display;

import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** Lexically scoped drawing context. Bindings are restored even on exceptions and never reach background indexes. */
public final class TooltipDisplayContext {
    private static final ScopedValue<Boolean> NAMES = ScopedValue.newInstance();
    private static final ScopedValue<ItemStack> STACK = ScopedValue.newInstance();

    private TooltipDisplayContext() {
    }

    public static boolean hidesNames() {
        return NAMES.isBound() && Minecraft.getInstance().isSameThread();
    }

    public static ItemStack stack() {
        return STACK.isBound() ? STACK.get() : ItemStack.EMPTY;
    }

    public static void withStack(ItemStack stack, Runnable draw) {
        if (Minecraft.getInstance().isSameThread()) {
            ScopedValue.where(NAMES, true).where(STACK, stack).run(draw);
        } else {
            draw.run();
        }
    }

    public static <T> T withNames(Supplier<T> draw) {
        return Minecraft.getInstance().isSameThread()
                ? ScopedValue.where(NAMES, true).call(draw::get) : draw.get();
    }
}
