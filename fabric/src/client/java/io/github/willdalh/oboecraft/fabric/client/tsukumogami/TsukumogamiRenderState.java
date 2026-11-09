package io.github.willdalh.oboecraft.fabric.client.tsukumogami;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;
import org.jspecify.annotations.Nullable;

/** What a frame draws of a tsukumogami. */
public final class TsukumogamiRenderState extends LivingEntityRenderState {
    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState windUpAnimationState = new AnimationState();
    public final AnimationState watchAnimationState = new AnimationState();
    public final AnimationState fadeAnimationState = new AnimationState();
    public boolean standsStill;
    public boolean fading;
    /** How far the fade-out is, from 0 when it starts to 1 when it is gone. */
    public float fadeProgress;
    /** The orbiting items and the ones falling away, placed for this frame, which its ring keeps. */
    public OrbitingItems.@Nullable Placed orbit;
}
