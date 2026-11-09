package io.github.willdalh.oboecraft.fabric.tsukumogami;

import io.github.willdalh.oboecraft.core.Oboecraft;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.tsukumogami.TsukumogamiRules;
import io.github.willdalh.oboecraft.fabric.network.NetworkCodecs;
import java.util.List;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityDataRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The tsukumogami's entity type, {@code oboecraft:tsukumogami}, and what it needs registered with it. It is never
 * saved with the world, can't be in Peaceful, drops nothing and doesn't burn.
 */
public final class TsukumogamiType {
    public static final ResourceKey<EntityType<?>> KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Oboecraft.MOD_ID, "tsukumogami"));

    /**
     * The overdue items that orbit it, which every player who sees it is sent. Only their item ids are sent, never the
     * owner's stacks, and at most {@link TsukumogamiRules#MAX_ORBITING_ITEMS}.
     */
    public static final EntityDataSerializer<List<ItemId>> ORBITING_ITEMS = EntityDataSerializer.forValueType(
            NetworkCodecs.ITEM_ID.apply(ByteBufCodecs.list(TsukumogamiRules.MAX_ORBITING_ITEMS)));

    public static final EntityType<Tsukumogami> TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY,
            EntityType.Builder.of(Tsukumogami::new, MobCategory.MONSTER)
                    .sized(0.8F, 2.8F)
                    .eyeHeight(2.45F)
                    .clientTrackingRange(10)
                    .noSave()
                    .noLootTable()
                    .fireImmune()
                    .notInPeaceful()
                    .build(KEY));

    private TsukumogamiType() {
    }

    /** Registers the entity type, its attributes, its synced data and its sounds, when the mod starts. */
    public static void register() {
        FabricEntityDataRegistry.register(
                Identifier.fromNamespaceAndPath(Oboecraft.MOD_ID, "orbiting_items"), ORBITING_ITEMS);
        FabricDefaultAttributeRegistry.register(TYPE, Tsukumogami.createAttributes());
        TsukumogamiSounds.register();
    }
}
