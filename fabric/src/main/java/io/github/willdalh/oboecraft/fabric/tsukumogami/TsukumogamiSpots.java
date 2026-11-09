package io.github.willdalh.oboecraft.fabric.tsukumogami;

import java.util.Optional;
import java.util.function.DoubleSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Where a tsukumogami arrives near its owner: on solid ground, out of the owner's sight if possible, 16 to 24 blocks
 * away. Only used on the server thread.
 */
final class TsukumogamiSpots {
    private static final double MIN_DISTANCE = 16;
    private static final double MAX_DISTANCE = 24;
    /** How many places around the owner are tried. */
    private static final int ATTEMPTS = 16;
    /** How far above the owner a place is looked for. */
    private static final int ABOVE = 8;
    /**
     * How far below the owner a place is looked for, so one is found under an owner who flies or pillars up.
     */
    private static final int BELOW = 48;
    /** How far to either side of straight behind the owner a place behind them may be, in radians. */
    private static final double BEHIND_SPREAD = Math.PI / 3;

    private TsukumogamiSpots() {
    }

    /** A place to arrive at, in any direction from the owner. */
    static Optional<Vec3> around(ServerLevel level, ServerPlayer owner, EntityType<?> type) {
        RandomSource random = owner.getRandom();
        return find(level, owner, type, () -> random.nextDouble() * Math.PI * 2);
    }

    /** A place behind the owner, the way they came from, to keep up with them. */
    static Optional<Vec3> behind(ServerLevel level, ServerPlayer owner, Tsukumogami tsukumogami) {
        RandomSource random = owner.getRandom();
        // The direction the owner faces is (-sin(yRot), cos(yRot)), as an angle atan2(cos, -sin).
        double facing = Math.atan2(Mth.cos(owner.getYRot() * Mth.DEG_TO_RAD), -Mth.sin(owner.getYRot() * Mth.DEG_TO_RAD));
        double behind = facing + Math.PI;
        return find(level, owner, tsukumogami.getType(),
                () -> behind + (random.nextDouble() * 2 - 1) * BEHIND_SPREAD);
    }

    /**
     * The first place out of the owner's sight among {@link #ATTEMPTS} places in directions from {@code angles}, or the
     * first place in sight if every place is.
     */
    private static Optional<Vec3> find(ServerLevel level, ServerPlayer owner, EntityType<?> type, DoubleSupplier angles) {
        RandomSource random = owner.getRandom();
        Optional<Vec3> inSight = Optional.empty();
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            double angle = angles.getAsDouble();
            double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
            Optional<Vec3> ground = groundAt(level, owner,
                    owner.getX() + Math.cos(angle) * distance, owner.getZ() + Math.sin(angle) * distance, type);
            if (ground.isEmpty()) {
                continue;
            }
            if (!inSightOf(level, owner, ground.get().add(0, type.getDimensions().eyeHeight(), 0))) {
                return ground;
            }
            if (inSight.isEmpty()) {
                inSight = ground;
            }
        }
        return inSight;
    }

    /**
     * The highest place in the column at x and z, from a little above the owner down to well below them, where it
     * stands on a sturdy block, out of any liquid, with room for its whole body.
     */
    private static Optional<Vec3> groundAt(ServerLevel level, ServerPlayer owner, double x, double z,
            EntityType<?> type) {
        int top = Math.min(owner.getBlockY() + ABOVE, level.getMaxY());
        int bottom = Math.max(owner.getBlockY() - BELOW, level.getMinY() + 1);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), top, Mth.floor(z));
        if (!level.isLoaded(pos)) {
            return Optional.empty();
        }
        for (int y = top; y >= bottom; y--) {
            pos.setY(y - 1);
            if (!level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)) {
                continue;
            }
            pos.setY(y);
            Vec3 feet = new Vec3(pos.getX() + 0.5, y, pos.getZ() + 0.5);
            if (level.getFluidState(pos).isEmpty() && level.noCollision(type.getSpawnAABB(feet.x, feet.y, feet.z))) {
                return Optional.of(feet);
            }
        }
        return Optional.empty();
    }

    /** Whether the owner can see the point: it is in front of them, with no block between their eyes and it. */
    private static boolean inSightOf(ServerLevel level, ServerPlayer owner, Vec3 point) {
        Vec3 eyes = owner.getEyePosition();
        if (owner.getViewVector(1.0F).dot(point.subtract(eyes)) <= 0) {
            return false;
        }
        return level.clip(new ClipContext(eyes, point, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, owner))
                .getType() == HitResult.Type.MISS;
    }
}
