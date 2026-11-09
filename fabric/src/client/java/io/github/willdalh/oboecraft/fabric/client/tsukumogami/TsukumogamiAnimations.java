package io.github.willdalh.oboecraft.fabric.client.tsukumogami;

import io.github.willdalh.oboecraft.fabric.tsukumogami.Tsukumogami;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * The tsukumogami's keyframe animations, in Minecraft's own format, on the parts of {@link TsukumogamiModel}. Rotations
 * are in degrees and positions in model pixels, added to the model's pose.
 */
final class TsukumogamiAnimations {
    /** A slow sway while it stands, under every other animation. */
    static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(4.0F)
            .looping()
            .addAnimation("upper_body", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(1.0F, 2.5F, 0, 1.5F), smooth(2.0F, 0, 0, 0),
                    smooth(3.0F, 2.5F, 0, -1.5F), smooth(4.0F, 0, 0, 0)))
            .addAnimation("head", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(1.4F, -2, 3, 4), smooth(2.6F, 1, -2, -3), smooth(4.0F, 0, 0, 0)))
            .addAnimation("jaw", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(2.0F, 4, 0, 0), smooth(4.0F, 0, 0, 0)))
            .addAnimation("right_arm", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(2.0F, -3, 0, -2), smooth(4.0F, 0, 0, 0)))
            .addAnimation("left_arm", rotation(
                    smooth(0.0F, -3, 0, 2), smooth(2.0F, 0, 0, 0), smooth(4.0F, -3, 0, 2)))
            .addAnimation("right_hand", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(2.0F, -5, 0, 0), smooth(4.0F, 0, 0, 0)))
            .addAnimation("left_hand", rotation(
                    smooth(0.0F, -5, 0, 0), smooth(2.0F, 0, 0, 0), smooth(4.0F, -5, 0, 0)))
            .addAnimation("comb", rotation(
                    smooth(0.0F, 0, 0, 8), smooth(2.0F, 0, 0, -8), smooth(4.0F, 0, 0, 8)))
            .addAnimation("bell", rotation(
                    smooth(0.0F, 6, 0, -6), smooth(2.0F, -6, 0, 6), smooth(4.0F, 6, 0, -6)))
            .addAnimation("cup", rotation(
                    smooth(0.0F, 0, 0, -5), smooth(2.0F, 5, 0, 5), smooth(4.0F, 0, 0, -5)))
            .addAnimation("train", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(2.0F, 0, 3, 0), smooth(4.0F, 0, 0, 0)))
            .build();

    /**
     * A slow, heavy walk: the skirt swings from the hips, the body rolls and sinks with each step, and the sleeves and
     * the things on the sash lag behind.
     */
    static final AnimationDefinition WALK = AnimationDefinition.Builder.withLength(2.0F)
            .looping()
            .addAnimation("right_robe", rotation(
                    smooth(0.0F, -16, 0, 0), smooth(1.0F, 16, 0, 0), smooth(2.0F, -16, 0, 0)))
            .addAnimation("left_robe", rotation(
                    smooth(0.0F, 16, 0, 0), smooth(1.0F, -16, 0, 0), smooth(2.0F, 16, 0, 0)))
            .addAnimation("root", position(
                    smoothPosition(0.0F, 0, 0, 0), smoothPosition(0.5F, 0, -0.8F, 0), smoothPosition(1.0F, 0, 0, 0),
                    smoothPosition(1.5F, 0, -0.8F, 0), smoothPosition(2.0F, 0, 0, 0)))
            .addAnimation("upper_body", rotation(
                    smooth(0.0F, 4, -6, -4), smooth(0.5F, 8, 0, 0), smooth(1.0F, 4, 6, 4), smooth(1.5F, 8, 0, 0),
                    smooth(2.0F, 4, -6, -4)))
            .addAnimation("head", rotation(
                    smooth(0.0F, -3, 4, 3), smooth(1.0F, -3, -4, -3), smooth(2.0F, -3, 4, 3)))
            .addAnimation("right_arm", rotation(
                    smooth(0.0F, 12, 0, -3), smooth(1.0F, -12, 0, -6), smooth(2.0F, 12, 0, -3)))
            .addAnimation("left_arm", rotation(
                    smooth(0.0F, -12, 0, 6), smooth(1.0F, 12, 0, 3), smooth(2.0F, -12, 0, 6)))
            .addAnimation("right_hand", rotation(
                    smooth(0.0F, -4, 0, 0), smooth(0.5F, 10, 0, 0), smooth(1.5F, -10, 0, 0), smooth(2.0F, -4, 0, 0)))
            .addAnimation("left_hand", rotation(
                    smooth(0.0F, 4, 0, 0), smooth(0.5F, -10, 0, 0), smooth(1.5F, 10, 0, 0), smooth(2.0F, 4, 0, 0)))
            .addAnimation("comb", rotation(
                    smooth(0.0F, 15, 0, 10), smooth(1.0F, -15, 0, -10), smooth(2.0F, 15, 0, 10)))
            .addAnimation("bell", rotation(
                    smooth(0.0F, -12, 0, -12), smooth(1.0F, 12, 0, 12), smooth(2.0F, -12, 0, -12)))
            .addAnimation("cup", rotation(
                    smooth(0.0F, 10, 0, 0), smooth(1.0F, -10, 0, 0), smooth(2.0F, 10, 0, 0)))
            .addAnimation("train", rotation(
                    smooth(0.0F, 0, 6, 0), smooth(1.0F, 0, -6, 0), smooth(2.0F, 0, 6, 0)))
            .build();

    /**
     * The attack: it rears back with both hands raised and its jaw opening over the wind-up, then brings them down at
     * the moment the hit lands, and settles again.
     */
    static final AnimationDefinition WIND_UP = AnimationDefinition.Builder
            .withLength(seconds(Tsukumogami.ATTACK_ANIMATION_TICKS))
            .addAnimation("upper_body", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.8F, -22, 0, 0), linear(0.95F, -24, 0, 0),
                    linear(1.1F, 30, 0, 0), smooth(1.6F, 0, 0, 0)))
            .addAnimation("head", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.8F, -12, 0, 0), linear(1.1F, 10, 0, 0), smooth(1.6F, 0, 0, 0)))
            .addAnimation("jaw", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.6F, 38, 0, 0), linear(1.0F, 42, 0, 0), smooth(1.6F, 0, 0, 0)))
            .addAnimation("right_arm", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.85F, -150, 0, -12), linear(0.95F, -155, 0, -12),
                    linear(1.1F, -20, 0, 0), smooth(1.6F, 0, 0, 0)))
            .addAnimation("left_arm", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.85F, -150, 0, 12), linear(0.95F, -155, 0, 12),
                    linear(1.1F, -20, 0, 0), smooth(1.6F, 0, 0, 0)))
            .addAnimation("right_hand", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.85F, -30, 0, 0), linear(1.1F, 15, 0, 0), smooth(1.6F, 0, 0, 0)))
            .addAnimation("left_hand", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.85F, -30, 0, 0), linear(1.1F, 15, 0, 0), smooth(1.6F, 0, 0, 0)))
            .addAnimation("bell", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.9F, -25, 0, 0), linear(1.1F, 35, 0, 0), smooth(1.6F, 0, 0, 0)))
            .build();

    /** Standing still during its owner's review: it leans in and tilts its head, watching. */
    static final AnimationDefinition WATCH = AnimationDefinition.Builder.withLength(6.0F)
            .looping()
            .addAnimation("upper_body", rotation(
                    smooth(0.0F, 10, 0, 0), smooth(3.0F, 12, 0, 0), smooth(6.0F, 10, 0, 0)))
            .addAnimation("head", rotation(
                    smooth(0.0F, -6, 0, 16), smooth(2.5F, -8, 0, 20), linear(2.7F, -8, 0, 20), smooth(3.2F, -6, 0, -10),
                    smooth(5.6F, -8, 0, -14), smooth(6.0F, -6, 0, 16)))
            .addAnimation("right_arm", rotation(
                    smooth(0.0F, -8, 0, 6), smooth(6.0F, -8, 0, 6)))
            .addAnimation("left_arm", rotation(
                    smooth(0.0F, -8, 0, -6), smooth(6.0F, -8, 0, -6)))
            .addAnimation("right_hand", rotation(
                    smooth(0.0F, -20, 0, 8), smooth(3.0F, -24, 0, 8), smooth(6.0F, -20, 0, 8)))
            .addAnimation("left_hand", rotation(
                    smooth(0.0F, -20, 0, -8), smooth(3.0F, -24, 0, -8), smooth(6.0F, -20, 0, -8)))
            .build();

    /** Fading away: it sinks and droops, as if the life went out of the old things it is made of. */
    static final AnimationDefinition FADE = AnimationDefinition.Builder.withLength(seconds(Tsukumogami.FADE_TICKS))
            .addAnimation("root", position(
                    smoothPosition(0.0F, 0, 0, 0), smoothPosition(seconds(Tsukumogami.FADE_TICKS), 0, -10, 0)))
            .addAnimation("upper_body", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(seconds(Tsukumogami.FADE_TICKS), 40, 0, 6)))
            .addAnimation("head", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(seconds(Tsukumogami.FADE_TICKS), 30, 0, -12)))
            .addAnimation("jaw", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(seconds(Tsukumogami.FADE_TICKS), 25, 0, 0)))
            .addAnimation("right_arm", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(seconds(Tsukumogami.FADE_TICKS), 10, 0, 10)))
            .addAnimation("left_arm", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(seconds(Tsukumogami.FADE_TICKS), 10, 0, -10)))
            .build();

    private TsukumogamiAnimations() {
    }

    private static float seconds(int ticks) {
        return ticks / 20.0F;
    }

    private static AnimationChannel rotation(Keyframe... keyframes) {
        return new AnimationChannel(AnimationChannel.Targets.ROTATION, keyframes);
    }

    private static AnimationChannel position(Keyframe... keyframes) {
        return new AnimationChannel(AnimationChannel.Targets.POSITION, keyframes);
    }

    /** A rotation keyframe that eases in and out of its neighbours. */
    private static Keyframe smooth(float time, float x, float y, float z) {
        return new Keyframe(time, KeyframeAnimations.degreeVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
    }

    /** A rotation keyframe reached in a straight line, for the sudden moves of the hit. */
    private static Keyframe linear(float time, float x, float y, float z) {
        return new Keyframe(time, KeyframeAnimations.degreeVec(x, y, z), AnimationChannel.Interpolations.LINEAR);
    }

    private static Keyframe smoothPosition(float time, float x, float y, float z) {
        return new Keyframe(time, KeyframeAnimations.posVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
    }
}
