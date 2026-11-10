package io.github.willdalh.oboecraft.fabric.client.tsukumogami;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.jspecify.annotations.Nullable;

/** What a frame draws of a tsukumogami. Times are in seconds since an animation started, and weights from 0 to 1. */
public final class TsukumogamiRenderState extends LivingEntityRenderState {
    public float attackSeconds;
    /** How strongly the attack shows. It falls to 0 when a review or fading out interrupts the attack. */
    public float attackWeight;
    public float watchSeconds;
    /** How strongly watching its owner shows. */
    public float watchWeight;
    /** How long it has flinched from being hurt. 0 while it doesn't. */
    public float flinchSeconds;
    /** How long it has faded out. 0 while it doesn't. */
    public float fadeSeconds;
    /** How far it has let go of what it was doing to fade out, which it does before much of it is gone. */
    public float fadeWeight;
    /** How far the fade-out is, from 0 when it starts to 1 when it is gone. */
    public float fadeProgress;
    /** How far it has let go of what it was doing to collapse, when it is killed. */
    public float deathWeight;
    /** The orbiting items and the ones falling away, placed for this frame, which its ring keeps. */
    public OrbitingItems.@Nullable Placed orbit;
}
