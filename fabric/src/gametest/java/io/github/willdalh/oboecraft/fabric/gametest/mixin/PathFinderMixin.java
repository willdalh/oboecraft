package io.github.willdalh.oboecraft.fabric.gametest.mixin;

import io.github.willdalh.oboecraft.fabric.gametest.PathSearches;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes each path search without changing it. */
@Mixin(PathFinder.class)
abstract class PathFinderMixin {
    @Inject(method = "findPath(Lnet/minecraft/world/level/PathNavigationRegion;Lnet/minecraft/world/entity/Mob;"
            + "Ljava/util/Set;FIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"))
    private void recordPathSearch(PathNavigationRegion level, Mob mob, Set<BlockPos> targets, float maxPathLength,
            int reachRange, float maxVisitedNodesMultiplier, CallbackInfoReturnable<Path> callback) {
        PathSearches.record(mob);
    }
}
