package io.github.willdalh.oboecraft.fabric.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.entity.BannerPattern;

/**
 * The stacks the game names through its code: one of each Minecraft item, and the stacks whose name the game's code
 * takes from their components. Names that the game's data gives stacks, like the camp maps, aren't among them.
 */
public final class NamedStacks {
    private NamedStacks() {
    }

    /** The stacks, made fresh on each call. The banner patterns come from the world's registries. */
    public static List<ItemStack> all(HolderGetter<BannerPattern> bannerPatterns) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
                stacks.add(new ItemStack(item));
            }
        }
        BuiltInRegistries.POTION.listElements().forEach(potion -> {
            for (Item item : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION, Items.TIPPED_ARROW)) {
                stacks.add(PotionContents.createItemStack(item, potion));
            }
        });
        for (DyeColor color : DyeColor.values()) {
            ItemStack shield = new ItemStack(Items.SHIELD);
            shield.set(DataComponents.BASE_COLOR, color);
            stacks.add(shield);
        }
        ItemStack lodestoneCompass = new ItemStack(Items.COMPASS);
        lodestoneCompass.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.empty(), true));
        stacks.add(lodestoneCompass);
        ItemStack namedHead = new ItemStack(Items.PLAYER_HEAD);
        namedHead.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved("Steve"));
        stacks.add(namedHead);
        stacks.add(Raid.getOminousBannerInstance(bannerPatterns));
        return stacks;
    }
}
