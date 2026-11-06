package io.github.willdalh.oboecraft.tools.namelist;

import java.util.Objects;

/**
 * A translation key that Minecraft names a stack with.
 *
 * @param translationKey the key, like {@code item.minecraft.potion.effect.healing}
 * @param item           the Minecraft item whose stacks get the name, like {@code minecraft:potion}, or null if it
 *                       isn't known
 */
record ItemNameKey(String translationKey, String item) {
    ItemNameKey {
        Objects.requireNonNull(translationKey, "translationKey");
    }
}
