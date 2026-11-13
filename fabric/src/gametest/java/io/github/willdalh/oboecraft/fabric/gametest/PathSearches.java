package io.github.willdalh.oboecraft.fabric.gametest;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Mob;

/** How many paths each mob has looked for, recorded by {@code PathFinderMixin} on the server thread. */
public final class PathSearches {
    private static final Map<Mob, Integer> COUNTS = new WeakHashMap<>();

    private PathSearches() {
    }

    public static void record(Mob mob) {
        COUNTS.merge(mob, 1, Integer::sum);
    }

    /** How many paths the mob has looked for since it was made. */
    public static int of(Mob mob) {
        return COUNTS.getOrDefault(mob, 0);
    }
}
