package io.github.willdalh.oboecraft.fabric.client;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.fabric.item.ItemMapping;
import io.github.willdalh.oboecraft.fabric.item.NamedStacks;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The icon of each item, the first of {@link NamedStacks} that maps to it. Items that only the game's data names, like
 * the camp maps, have none. The icons are made once for the world's registries and kept until the player leaves. Only
 * used on the client thread.
 */
public final class ItemIcons {
    private final ItemMapping mapping;
    private @Nullable RegistryAccess madeFor;
    private Map<ItemId, ItemStack> icons = Map.of();

    ItemIcons(ItemMapping mapping) {
        this.mapping = Objects.requireNonNull(mapping, "mapping");
    }

    /** The item's icon in the world with these registries. Callers only draw the stack and never change it. */
    public Optional<ItemStack> icon(RegistryAccess registries, ItemId item) {
        if (registries != madeFor) {
            icons = iconsByItem(registries);
            madeFor = registries;
        }
        return Optional.ofNullable(icons.get(item));
    }

    /** Forgets the icons, when the player leaves the world. */
    void clear() {
        madeFor = null;
        icons = Map.of();
    }

    private Map<ItemId, ItemStack> iconsByItem(RegistryAccess registries) {
        Map<ItemId, ItemStack> found = new HashMap<>();
        for (ItemStack stack : NamedStacks.all(registries.lookupOrThrow(Registries.BANNER_PATTERN))) {
            mapping.entryOf(stack).ifPresent(entry -> found.putIfAbsent(entry.id(), stack));
        }
        return Map.copyOf(found);
    }
}
