package io.github.willdalh.oboecraft.fabric.client.tsukumogami;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.tsukumogami.TsukumogamiRules;
import io.github.willdalh.oboecraft.fabric.client.ItemIcons;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.core.RegistryAccess;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The ring of overdue items around one tsukumogami's shoulders, on the client. The items spin slowly, and when one
 * leaves the ring, after a pass or when the tsukumogami fades out, it falls away while the others close up. Only used
 * on the client thread.
 */
final class OrbitingItems {
    /** The most items falling away at once: a whole ring when it fades out, and a few passed just before. */
    static final int MAX_FALLING = TsukumogamiRules.MAX_ORBITING_ITEMS * 2;
    /** How fast the ring turns, in radians a tick. */
    private static final float SPIN = 0.025F;
    /** How quickly an item moves to its new place in the ring when the ring changes, a share of the way a tick. */
    private static final float SETTLE = 0.08F;
    /** How long an item takes to fall away, in ticks. */
    private static final float FALL_TICKS = 25;
    private static final float RADIUS = 1.15F;
    /** The height of the ring above the tsukumogami's feet, at its stooped shoulders. */
    private static final float HEIGHT = 1.75F;
    /** How far in front of its feet the ring is, since it stoops forward. */
    private static final float FORWARD = 0.35F;
    /** The size of an item, on top of the size of an item on the ground. */
    private static final float SCALE = 1.1F;

    /** An item that left the ring at {@code angle} and falls away from {@code startAge}. */
    private record Falling(ItemId item, float angle, float startAge) {
    }

    private List<ItemId> inRing = List.of();
    /** The place in the ring of each item of {@link #inRing}, at the same index, before the ring's spin, in radians. */
    private final float[] places = new float[TsukumogamiRules.MAX_ORBITING_ITEMS];
    private final List<Falling> falling = new ArrayList<>();
    private final Placed placed = new Placed();
    private float lastAge = Float.NaN;

    /**
     * The items the ring shows for a frame, each with its place around the tsukumogami's feet, its turn and its size.
     * Each ring keeps one and fills it again for each frame, so a frame makes no new objects for the items.
     */
    static final class Placed {
        static final int CAPACITY = TsukumogamiRules.MAX_ORBITING_ITEMS + MAX_FALLING;
        final ItemStackRenderState[] stacks = new ItemStackRenderState[CAPACITY];
        final float[] x = new float[CAPACITY];
        final float[] y = new float[CAPACITY];
        final float[] z = new float[CAPACITY];
        final float[] yRot = new float[CAPACITY];
        final float[] scale = new float[CAPACITY];
        int count;

        Placed() {
            for (int i = 0; i < CAPACITY; i++) {
                stacks[i] = new ItemStackRenderState();
            }
        }
    }

    /**
     * Follows the items the tsukumogami shows at {@code age}, in ticks. An item that left the ring starts falling, and
     * the others move towards even places. When it fades out, every item falls.
     */
    void update(List<ItemId> items, boolean fading, float age) {
        float elapsed = Float.isNaN(lastAge) ? 0 : Math.max(0, age - lastAge);
        lastAge = age;
        List<ItemId> now = fading ? List.of() : items;
        if (!now.equals(inRing)) {
            changeRing(now, age);
        }
        float share = Math.min(1, elapsed * SETTLE);
        for (int i = 0; i < inRing.size(); i++) {
            float toEven = Mth.wrapDegrees((evenPlace(i, inRing.size()) - places[i]) * Mth.RAD_TO_DEG);
            places[i] += toEven * Mth.DEG_TO_RAD * share;
        }
        for (Iterator<Falling> it = falling.iterator(); it.hasNext(); ) {
            if (age - it.next().startAge() > FALL_TICKS) {
                it.remove();
            }
        }
    }

    /**
     * Makes the ring show {@code now}, a list of at most {@link TsukumogamiRules#MAX_ORBITING_ITEMS}: the items that
     * left start falling, the items that stay keep their places, and new items start at an even place.
     */
    private void changeRing(List<ItemId> now, float age) {
        float[] oldPlaces = places.clone();
        for (int i = 0; i < inRing.size(); i++) {
            if (!now.contains(inRing.get(i)) && falling.size() < MAX_FALLING) {
                falling.add(new Falling(inRing.get(i), oldPlaces[i] + age * SPIN, age));
            }
        }
        for (int i = 0; i < now.size(); i++) {
            int before = inRing.indexOf(now.get(i));
            places[i] = before >= 0 ? oldPlaces[before] : evenPlace(i, now.size());
        }
        inRing = now;
    }

    private static float evenPlace(int index, int count) {
        return Mth.TWO_PI * index / count;
    }

    /**
     * Places the items for a frame, around the feet of the tsukumogami facing {@code bodyRot}, and returns them. Items
     * without an icon, like the camp maps, aren't drawn.
     */
    Placed place(Entity entity, float age, float bodyRot, ItemIcons icons, ItemModelResolver resolver) {
        float forwardX = -Mth.sin(bodyRot * Mth.DEG_TO_RAD) * FORWARD;
        float forwardZ = Mth.cos(bodyRot * Mth.DEG_TO_RAD) * FORWARD;
        RegistryAccess registries = entity.level().registryAccess();
        Placed out = placed;
        out.count = 0;
        for (int i = 0; i < inRing.size(); i++) {
            float angle = places[i] + age * SPIN;
            float bob = Mth.sin(age * 0.08F + angle * 3) * 0.06F;
            add(out, entity, icons.icon(registries, inRing.get(i)), resolver, forwardX + Mth.cos(angle) * RADIUS,
                    HEIGHT + bob, forwardZ + Mth.sin(angle) * RADIUS, -angle * Mth.RAD_TO_DEG, SCALE);
        }
        for (Falling item : falling) {
            float progress = Math.min(1, (age - item.startAge()) / FALL_TICKS);
            float radius = RADIUS + progress * 0.5F;
            add(out, entity, icons.icon(registries, item.item()), resolver, forwardX + Mth.cos(item.angle()) * radius,
                    HEIGHT - progress * progress * HEIGHT, forwardZ + Mth.sin(item.angle()) * radius,
                    -item.angle() * Mth.RAD_TO_DEG + progress * 540, SCALE * (1 - progress * 0.6F));
        }
        return out;
    }

    private static void add(Placed out, Entity entity, Optional<ItemStack> icon, ItemModelResolver resolver,
            float x, float y, float z, float yRot, float scale) {
        if (icon.isEmpty() || out.count == Placed.CAPACITY) {
            return;
        }
        int i = out.count++;
        resolver.updateForNonLiving(out.stacks[i], icon.get(), ItemDisplayContext.GROUND, entity);
        out.x[i] = x;
        out.y[i] = y;
        out.z[i] = z;
        out.yRot[i] = yRot;
        out.scale[i] = scale;
    }
}
