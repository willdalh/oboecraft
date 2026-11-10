package io.github.willdalh.oboecraft.fabric.client.tsukumogami;

import io.github.willdalh.oboecraft.fabric.tsukumogami.Tsukumogami;
import io.github.willdalh.oboecraft.fabric.tsukumogami.TsukumogamiMotion;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.util.Mth;

/**
 * The tsukumogami's keyframe animations, in Minecraft's own format, on the parts of {@link TsukumogamiModel}. Rotations
 * are in degrees and positions in model pixels, added to the model's pose. A positive x rotation tips an upright part
 * forward and swings a hanging part back, and a positive z rotation swings a hanging part to its right.
 *
 * <p>Every animation that doesn't loop starts from the model's own pose, and the attack and the flinch end in it, so
 * {@link TsukumogamiModel} can add them to whatever else shows. The heavy parts lead and the loose ones follow: the
 * back moves first, then the arms, and the sleeves, the hair and the things on the sash swing after them.
 */
final class TsukumogamiAnimations {
    private static final float IDLE_SECONDS = 8.0F;
    private static final float WALK_SECONDS = 1.6F;
    private static final float WATCH_SECONDS = 12.0F;
    private static final float ATTACK_SECONDS = seconds(Tsukumogami.ATTACK_ANIMATION_TICKS);
    /** When the hands come down on its owner, as the server's wind-up ends. They are down just before, and stay. */
    private static final float HIT = seconds(Tsukumogami.WIND_UP_TICKS);
    private static final float FLINCH_SECONDS = seconds(TsukumogamiMotion.FLINCH_TICKS);
    private static final float FADE_SECONDS = seconds(Tsukumogami.FADE_TICKS);
    /** How long Minecraft keeps a mob that was killed. */
    private static final float DEATH_SECONDS = 1.0F;
    /** How many keyframes make one swing to and fro, enough for a smooth curve. */
    private static final int KEYFRAMES_PER_SWING = 8;

    private static final AnimationChannel ATTACK_FOREARM = rotation(
            smooth(0.0F, 0, 0, 0), smooth(0.4F, -34, 0, 0), smooth(0.8F, -42, 0, 0), linear(HIT - 0.13F, -44, 0, 0),
            linear(HIT - 0.04F, 12, 0, 0), smooth(HIT + 0.12F, 15, 0, 0), smooth(HIT + 0.55F, 4, 0, 0),
            smooth(ATTACK_SECONDS, 0, 0, 0));
    private static final AnimationChannel ATTACK_HAND = rotation(
            smooth(0.0F, 0, 0, 0), smooth(0.8F, -24, 0, 0), linear(HIT - 0.13F, -26, 0, 0),
            linear(HIT - 0.03F, 14, 0, 0), smooth(HIT + 0.5F, 5, 0, 0), smooth(ATTACK_SECONDS, 0, 0, 0));
    /** The fingers spread as the hands go up and close into claws as they come down. */
    private static final AnimationChannel ATTACK_FINGERS = rotation(
            smooth(0.0F, 0, 0, 0), smooth(0.5F, 12, 0, 0), smooth(0.8F, 16, 0, 0), linear(HIT - 0.12F, 16, 0, 0),
            linear(HIT - 0.02F, -42, 0, 0), smooth(HIT + 0.4F, -30, 0, 0), smooth(ATTACK_SECONDS, 0, 0, 0));

    /**
     * Standing: it breathes and sways so slowly that it nearly stands still, the mask steadier than the body, and its
     * hands, sleeves, hair and the things on its sash each settle in their own time.
     */
    static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(IDLE_SECONDS)
            .looping()
            .addAnimation("torso", swing(IDLE_SECONDS, 1, 0.0F, 0, 0, 1.2F))
            .addAnimation("torso", swing(IDLE_SECONDS, 2, 0.1F, 0.9F, 0, 0))
            .addAnimation("chest", swing(IDLE_SECONDS, 2, 0.22F, 1.3F, 0, 0))
            .addAnimation("head", swing(IDLE_SECONDS, 1, 0.08F, 0, 0, -0.9F))
            .addAnimation("head", swing(IDLE_SECONDS, 2, 0.3F, -1.0F, 0, 0))
            .addAnimation("jaw", swing(IDLE_SECONDS, 2, 0.35F, 1.2F, 0, 0))
            .addAnimation("right_arm", swing(IDLE_SECONDS, 2, 0.34F, 1.6F, 0, 0))
            .addAnimation("left_arm", swing(IDLE_SECONDS, 2, 0.42F, 1.4F, 0, 0))
            .addAnimation("right_forearm", swing(IDLE_SECONDS, 1, 0.5F, 2.2F, 0, 0))
            .addAnimation("left_forearm", swing(IDLE_SECONDS, 1, 0.15F, 2.2F, 0, 0))
            .addAnimation("right_fingers", swing(IDLE_SECONDS, 1, 0.62F, 7, 0, 0))
            .addAnimation("left_fingers", swing(IDLE_SECONDS, 1, 0.27F, 6, 0, 0))
            .addAnimation("right_sleeve", swing(IDLE_SECONDS, 2, 0.48F, 2.2F, 0, 0))
            .addAnimation("left_sleeve", swing(IDLE_SECONDS, 2, 0.58F, 2.0F, 0, 0))
            .addAnimation("right_hair", swing(IDLE_SECONDS, 2, 0.45F, 1.6F, 0, 0))
            .addAnimation("left_hair", swing(IDLE_SECONDS, 2, 0.55F, 1.8F, 0, 0))
            .addAnimation("hair_back", swing(IDLE_SECONDS, 2, 0.5F, 0.8F, 0, 0))
            .addAnimation("comb", swing(IDLE_SECONDS, 2, 0.4F, 0, 0, 2.5F))
            .addAnimation("bell", swing(IDLE_SECONDS, 3, 0.2F, 2.0F, 0, 2.0F))
            .addAnimation("cup", swing(IDLE_SECONDS, 2, 0.65F, 3.0F, 0, 0))
            .addAnimation("train_tip", swing(IDLE_SECONDS, 1, 0.3F, 0, 1.5F, 0))
            .build();

    /**
     * A slow, heavy walk of two sliding steps. It puts its weight over the foot that stands before the other one
     * slides out from under the kimono, sinks as each step lands, and its sleeves, hair and train come after it.
     * The right foot lands at the start and the left foot halfway.
     */
    static final AnimationDefinition WALK = AnimationDefinition.Builder.withLength(WALK_SECONDS)
            .looping()
            .addAnimation("right_foot", position(
                    linearPosition(0.0F, 0, 0, -4.5F), linearPosition(0.8F, 0, 0, 4), smoothPosition(1.05F, 0, 0.8F, 3),
                    smoothPosition(1.35F, 0, 1.0F, -3), linearPosition(1.6F, 0, 0, -4.5F)))
            .addAnimation("left_foot", position(
                    linearPosition(0.0F, 0, 0, 4), smoothPosition(0.25F, 0, 0.8F, 3),
                    smoothPosition(0.55F, 0, 1.0F, -3),
                    linearPosition(0.8F, 0, 0, -4.5F), linearPosition(1.6F, 0, 0, 4)))
            // The knee pushes each front of the kimono out as its foot slides forward.
            .addAnimation("right_robe_front", rotation(
                    smooth(0.0F, -7, 0, 0), smooth(0.4F, -1, 0, 0), smooth(0.8F, 3, 0, 0), smooth(1.2F, -6, 0, 0),
                    smooth(1.45F, -10, 0, 0), smooth(1.6F, -7, 0, 0)))
            .addAnimation("left_robe_front", rotation(
                    smooth(0.0F, 3, 0, 0), smooth(0.4F, -6, 0, 0), smooth(0.65F, -10, 0, 0), smooth(0.8F, -7, 0, 0),
                    smooth(1.2F, -1, 0, 0), smooth(1.6F, 3, 0, 0)))
            // It sinks onto each step and leans over the foot that stands.
            .addAnimation("root", position(
                    smoothPosition(0.0F, 0, -0.5F, 0), smoothPosition(0.12F, -0.4F, -0.9F, 0),
                    smoothPosition(0.5F, -0.9F, 0.1F, 0), smoothPosition(0.8F, 0, -0.5F, 0),
                    smoothPosition(0.92F, 0.4F, -0.9F, 0), smoothPosition(1.3F, 0.9F, 0.1F, 0),
                    smoothPosition(1.6F, 0, -0.5F, 0)))
            .addAnimation("robe", swing(WALK_SECONDS, 1, 0.06F, 0, 0, 1.6F))
            .addAnimation("torso", hold(4, 0, 0))
            .addAnimation("torso", swing(WALK_SECONDS, 1, 0.04F, 0, 0, 2.6F))
            .addAnimation("torso", swing(WALK_SECONDS, 1, 0.25F, 0, 3.0F, 0))
            .addAnimation("torso", swing(WALK_SECONDS, 2, 0.2F, 1.4F, 0, 0))
            .addAnimation("chest", swing(WALK_SECONDS, 1, 0.14F, 0, 0, 1.6F))
            .addAnimation("chest", swing(WALK_SECONDS, 2, 0.3F, 1.2F, 0, 0))
            // The mask stays level and keeps facing its owner while the body rolls under it.
            .addAnimation("head", hold(-3, 0, 0))
            .addAnimation("head", swing(WALK_SECONDS, 1, 0.08F, 0, 0, -3.4F))
            .addAnimation("head", swing(WALK_SECONDS, 1, 0.25F, 0, -2.4F, 0))
            .addAnimation("head", swing(WALK_SECONDS, 2, 0.34F, -1.5F, 0, 0))
            .addAnimation("jaw", swing(WALK_SECONDS, 2, 0.45F, 1.5F, 0, 0))
            .addAnimation("right_arm", swing(WALK_SECONDS, 1, 0.34F, 5, 0, 0))
            .addAnimation("left_arm", swing(WALK_SECONDS, 1, 0.84F, 5, 0, 0))
            .addAnimation("right_forearm", swing(WALK_SECONDS, 1, 0.42F, 6, 0, 0))
            .addAnimation("left_forearm", swing(WALK_SECONDS, 1, 0.92F, 6, 0, 0))
            .addAnimation("right_hand", swing(WALK_SECONDS, 1, 0.5F, 5, 0, 0))
            .addAnimation("left_hand", swing(WALK_SECONDS, 1, 0.0F, 5, 0, 0))
            .addAnimation("right_sleeve", swing(WALK_SECONDS, 1, 0.52F, 9, 0, 0))
            .addAnimation("left_sleeve", swing(WALK_SECONDS, 1, 0.02F, 9, 0, 0))
            .addAnimation("right_hair", swing(WALK_SECONDS, 2, 0.5F, 3.5F, 0, 0))
            .addAnimation("left_hair", swing(WALK_SECONDS, 2, 0.58F, 4.0F, 0, 0))
            .addAnimation("hair_back", swing(WALK_SECONDS, 2, 0.55F, 2.0F, 0, 0))
            .addAnimation("hair_back", swing(WALK_SECONDS, 1, 0.3F, 0, 0, 2.0F))
            .addAnimation("comb", swing(WALK_SECONDS, 2, 0.5F, 9, 0, 5))
            .addAnimation("bell", swing(WALK_SECONDS, 2, 0.62F, 11, 0, 0))
            .addAnimation("bell", swing(WALK_SECONDS, 1, 0.3F, 0, 0, 7))
            .addAnimation("cup", swing(WALK_SECONDS, 2, 0.56F, 8, 0, 0))
            .addAnimation("train", swing(WALK_SECONDS, 1, 0.3F, 0, 4, 0))
            .addAnimation("train_tip", swing(WALK_SECONDS, 1, 0.5F, 0, 7, 0))
            .build();

    /**
     * The attack: over the wind-up it rears up with both hands raised behind its head and its jaw opening, holds, and
     * then brings them down fast, so they are down when the hit lands. After that it straightens up slowly, while its
     * sleeves, hair and the things on its sash swing out and settle, and its jaw clacks shut.
     */
    static final AnimationDefinition ATTACK = AnimationDefinition.Builder.withLength(ATTACK_SECONDS)
            .addAnimation("torso", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.45F, -11, 0, 0), smooth(0.78F, -17, 0, 0),
                    linear(HIT - 0.13F, -18, 0, 0), linear(HIT - 0.04F, 6, 0, 0), smooth(HIT + 0.1F, 9, 0, 0),
                    smooth(HIT + 0.5F, 4, 0, 0), smooth(ATTACK_SECONDS, 0, 0, 0)))
            .addAnimation("torso", position(
                    smoothPosition(0.0F, 0, 0, 0), smoothPosition(0.78F, 0, 0.6F, 1),
                    linearPosition(HIT - 0.13F, 0, 0.6F, 1), linearPosition(HIT - 0.04F, 0, -1.2F, -1),
                    smoothPosition(HIT + 0.5F, 0, -0.5F, -0.5F), smoothPosition(ATTACK_SECONDS, 0, 0, 0)))
            .addAnimation("chest", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.5F, -8, 0, 0), smooth(0.8F, -13, 0, 0),
                    linear(HIT - 0.12F, -13, 0, 0), linear(HIT - 0.03F, 5, 0, 0), smooth(HIT + 0.12F, 7, 0, 0),
                    smooth(HIT + 0.55F, 3, 0, 0), smooth(ATTACK_SECONDS, 0, 0, 0)))
            // The mask keeps staring at its owner: chin down as it rears up, and up as it comes down over them.
            .addAnimation("head", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.5F, 7, 0, 3), smooth(0.8F, 13, 0, 6),
                    linear(HIT - 0.12F, 13, 0, 6), linear(HIT - 0.03F, -2, 0, 0), smooth(HIT + 0.12F, -4, 0, -2),
                    smooth(HIT + 0.6F, -2, 0, 0), smooth(ATTACK_SECONDS, 0, 0, 0)))
            .addAnimation("jaw", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.3F, 7, 0, 0), smooth(0.8F, 34, 0, 0), linear(HIT - 0.05F, 46, 0, 0),
                    linear(HIT + 0.08F, 46, 0, 0), smooth(HIT + 0.22F, 9, 0, 0), smooth(HIT + 0.34F, 17, 0, 0),
                    smooth(HIT + 0.6F, 3, 0, 0), smooth(ATTACK_SECONDS, 0, 0, 0)))
            .addAnimation("robe", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.8F, -2.5F, 0, 0), linear(HIT - 0.12F, -2.5F, 0, 0),
                    linear(HIT, 4, 0, 0), smooth(HIT + 0.2F, 5, 0, 0), smooth(ATTACK_SECONDS, 0, 0, 0)))
            .addAnimation("right_robe_front", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(HIT - 0.1F, 2, 0, 0), smooth(HIT + 0.08F, -9, 0, 0),
                    smooth(HIT + 0.35F, 4, 0, 0), smooth(HIT + 0.65F, -1, 0, 0), smooth(ATTACK_SECONDS, 0, 0, 0)))
            .addAnimation("left_robe_front", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(HIT - 0.1F, 2, 0, 0), smooth(HIT + 0.1F, -11, 0, 0),
                    smooth(HIT + 0.4F, 5, 0, 0), smooth(HIT + 0.7F, -1, 0, 0), smooth(ATTACK_SECONDS, 0, 0, 0)))
            .addAnimation("right_arm", attackArm(1))
            .addAnimation("left_arm", attackArm(-1))
            .addAnimation("right_forearm", ATTACK_FOREARM)
            .addAnimation("left_forearm", ATTACK_FOREARM)
            .addAnimation("right_hand", ATTACK_HAND)
            .addAnimation("left_hand", ATTACK_HAND)
            .addAnimation("right_fingers", ATTACK_FINGERS)
            .addAnimation("left_fingers", ATTACK_FINGERS)
            .addAnimation("right_sleeve", attackSleeve(0))
            .addAnimation("left_sleeve", attackSleeve(0.04F))
            .addAnimation("right_hair", attackHair(0, 1))
            .addAnimation("left_hair", attackHair(0.05F, 0.85F))
            .addAnimation("hair_back", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.8F, -9, 0, 0), smooth(HIT - 0.1F, -9, 0, 0),
                    smooth(HIT + 0.03F, 26, 0, 0), smooth(HIT + 0.3F, -5, 0, 0), smooth(HIT + 0.6F, 3, 0, 0),
                    smooth(ATTACK_SECONDS, 0, 0, 0)))
            .addAnimation("comb", attackHanging(0.03F, 0.8F))
            .addAnimation("bell", attackHanging(0, 1))
            .addAnimation("cup", attackHanging(0.06F, 0.6F))
            .addAnimation("train_tip", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(HIT, 0, 0, 0), smooth(HIT + 0.25F, -5, 3, 0),
                    smooth(ATTACK_SECONDS, 0, 0, 0)))
            .build();

    /**
     * Standing still during its owner's review. It leans in with its head tilted, its hands half raised, and holds
     * that. Now and then it tilts its head the other way, quickly, and holds again.
     */
    static final AnimationDefinition WATCH = AnimationDefinition.Builder.withLength(WATCH_SECONDS)
            .looping()
            .addAnimation("torso", hold(5, 0, 0))
            .addAnimation("chest", hold(6, 0, 0))
            .addAnimation("head", rotation(
                    linear(0.0F, -5, 0, 13), linear(5.2F, -5, 0, 13), smooth(5.5F, -3, 0, -11),
                    linear(10.6F, -3, 0, -11), smooth(10.95F, -5, 0, 13), linear(WATCH_SECONDS, -5, 0, 13)))
            .addAnimation("jaw", rotation(
                    linear(0.0F, 3, 0, 0), linear(5.2F, 3, 0, 0), smooth(5.5F, 9, 0, 0), smooth(6.3F, 3, 0, 0),
                    linear(WATCH_SECONDS, 3, 0, 0)))
            .addAnimation("right_arm", hold(-9, 0, 3))
            .addAnimation("left_arm", hold(-6, 0, -3))
            .addAnimation("right_forearm", hold(-16, 0, 0))
            .addAnimation("left_forearm", hold(-10, 0, 0))
            .addAnimation("right_hand", hold(-8, 0, 0))
            .addAnimation("left_hand", hold(-6, 0, 0))
            .addAnimation("right_fingers", rotation(
                    linear(0.0F, -14, 0, 0), linear(5.5F, -14, 0, 0), smooth(5.9F, -30, 0, 0),
                    smooth(6.8F, -14, 0, 0), linear(WATCH_SECONDS, -14, 0, 0)))
            .addAnimation("left_fingers", rotation(
                    linear(0.0F, -10, 0, 0), linear(5.6F, -10, 0, 0), smooth(6.05F, -26, 0, 0),
                    smooth(7.0F, -10, 0, 0), linear(WATCH_SECONDS, -10, 0, 0)))
            .addAnimation("right_sleeve", hold(8, 0, 0))
            .addAnimation("left_sleeve", hold(5, 0, 0))
            .addAnimation("right_hair", rotation(
                    linear(0.0F, -4, 0, -10), linear(5.25F, -4, 0, -10), smooth(5.75F, -4, 0, 12),
                    smooth(6.2F, -4, 0, 7), linear(10.65F, -4, 0, 8), smooth(11.15F, -4, 0, -12),
                    smooth(11.6F, -4, 0, -9), linear(WATCH_SECONDS, -4, 0, -10)))
            .addAnimation("left_hair", rotation(
                    linear(0.0F, -4, 0, -10), linear(5.25F, -4, 0, -10), smooth(5.8F, -4, 0, 12),
                    smooth(6.25F, -4, 0, 7), linear(10.65F, -4, 0, 8), smooth(11.2F, -4, 0, -12),
                    smooth(11.65F, -4, 0, -9), linear(WATCH_SECONDS, -4, 0, -10)))
            .build();

    /** Being hurt: a short, stiff jolt back from the blow, the mask knocked aside, and still again. */
    static final AnimationDefinition FLINCH = AnimationDefinition.Builder.withLength(FLINCH_SECONDS)
            .addAnimation("torso", rotation(
                    linear(0.0F, 0, 0, 0), smooth(0.08F, -5, 0, 1.5F), smooth(0.25F, 1.5F, 0, 0),
                    smooth(FLINCH_SECONDS, 0, 0, 0)))
            .addAnimation("chest", rotation(
                    linear(0.0F, 0, 0, 0), smooth(0.1F, -4, 0, 0), smooth(0.28F, 1, 0, 0),
                    smooth(FLINCH_SECONDS, 0, 0, 0)))
            .addAnimation("head", rotation(
                    linear(0.0F, 0, 0, 0), smooth(0.07F, -7, 4, 7), smooth(0.24F, 2, -1, -2),
                    smooth(FLINCH_SECONDS, 0, 0, 0)))
            .addAnimation("jaw", rotation(
                    linear(0.0F, 0, 0, 0), smooth(0.1F, 11, 0, 0), smooth(0.3F, 2, 0, 0),
                    smooth(FLINCH_SECONDS, 0, 0, 0)))
            .addAnimation("right_arm", rotation(
                    linear(0.0F, 0, 0, 0), smooth(0.12F, 6, 0, 5), smooth(0.3F, -2, 0, 0),
                    smooth(FLINCH_SECONDS, 0, 0, 0)))
            .addAnimation("left_arm", rotation(
                    linear(0.0F, 0, 0, 0), smooth(0.12F, 6, 0, -5), smooth(0.3F, -2, 0, 0),
                    smooth(FLINCH_SECONDS, 0, 0, 0)))
            .addAnimation("right_sleeve", rotation(
                    linear(0.0F, 0, 0, 0), smooth(0.18F, -9, 0, 0), smooth(0.34F, 3, 0, 0),
                    smooth(FLINCH_SECONDS, 0, 0, 0)))
            .addAnimation("left_sleeve", rotation(
                    linear(0.0F, 0, 0, 0), smooth(0.2F, -9, 0, 0), smooth(0.36F, 3, 0, 0),
                    smooth(FLINCH_SECONDS, 0, 0, 0)))
            .addAnimation("right_hair", rotation(
                    linear(0.0F, 0, 0, 0), smooth(0.16F, -10, 0, 0), smooth(0.33F, 4, 0, 0),
                    smooth(FLINCH_SECONDS, 0, 0, 0)))
            .addAnimation("left_hair", rotation(
                    linear(0.0F, 0, 0, 0), smooth(0.18F, -10, 0, 0), smooth(0.35F, 4, 0, 0),
                    smooth(FLINCH_SECONDS, 0, 0, 0)))
            .addAnimation("bell", rotation(
                    linear(0.0F, 0, 0, 0), smooth(0.15F, -14, 0, 0), smooth(0.32F, 8, 0, 0),
                    smooth(FLINCH_SECONDS, 0, 0, 0)))
            .build();

    /**
     * Fading out: what moved it lets go. Its hands and jaw drop, its back and its head sink forward, and it stays so
     * while it dissolves from the hem up.
     */
    static final AnimationDefinition FADE = AnimationDefinition.Builder.withLength(FADE_SECONDS)
            .addAnimation("root", position(
                    smoothPosition(0.0F, 0, 0, 0), smoothPosition(0.9F, 0, -1.2F, 0),
                    smoothPosition(FADE_SECONDS, 0, -2.5F, 0)))
            .addAnimation("torso", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.8F, 9, 0, 1), smooth(FADE_SECONDS, 15, 0, 2)))
            .addAnimation("chest", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.9F, 9, 0, 0), smooth(FADE_SECONDS, 14, 0, 0)))
            .addAnimation("head", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.35F, -4, 0, 0), smooth(1.1F, 16, 0, -5),
                    smooth(FADE_SECONDS, 24, 0, -8)))
            .addAnimation("jaw", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.5F, 14, 0, 0), smooth(FADE_SECONDS, 20, 0, 0)))
            .addAnimation("right_arm", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.7F, 12, 0, -2), smooth(FADE_SECONDS, 18, 0, -3)))
            .addAnimation("left_arm", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.8F, 12, 0, 2), smooth(FADE_SECONDS, 18, 0, 3)))
            .addAnimation("right_forearm", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.6F, 12, 0, 0), smooth(0.9F, 9, 0, 0),
                    smooth(FADE_SECONDS, 11, 0, 0)))
            .addAnimation("left_forearm", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.7F, 12, 0, 0), smooth(1.0F, 9, 0, 0),
                    smooth(FADE_SECONDS, 11, 0, 0)))
            .addAnimation("right_fingers", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.5F, 14, 0, 0), smooth(FADE_SECONDS, 10, 0, 0)))
            .addAnimation("left_fingers", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.6F, 14, 0, 0), smooth(FADE_SECONDS, 10, 0, 0)))
            .addAnimation("right_sleeve", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.9F, -16, 0, 0), smooth(1.3F, -11, 0, 0),
                    smooth(FADE_SECONDS, -13, 0, 0)))
            .addAnimation("left_sleeve", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(1.0F, -16, 0, 0), smooth(1.4F, -11, 0, 0),
                    smooth(FADE_SECONDS, -13, 0, 0)))
            .addAnimation("right_hair", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(1.2F, -22, 0, 0), smooth(FADE_SECONDS, -30, 0, 0)))
            .addAnimation("left_hair", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(1.25F, -22, 0, 0), smooth(FADE_SECONDS, -30, 0, 0)))
            .addAnimation("hair_back", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(1.2F, -6, 0, 0), smooth(FADE_SECONDS, -9, 0, 0)))
            .build();

    /**
     * Being killed: the blow knocks it back, and then nothing holds it up. The kimono falls in on itself, the back
     * folds over it, the arms drop to its sides and the mask tips up and falls last, its jaw hanging open.
     */
    static final AnimationDefinition DEATH = AnimationDefinition.Builder.withLength(DEATH_SECONDS)
            .addAnimation("robe", new AnimationChannel(AnimationChannel.Targets.SCALE,
                    scale(0.0F, 1, 1, 1), scale(0.15F, 1, 1, 1), scale(0.5F, 1.12F, 0.55F, 1.12F),
                    scale(0.75F, 1.25F, 0.24F, 1.3F), scale(DEATH_SECONDS, 1.28F, 0.2F, 1.34F)))
            .addAnimation("robe", position(
                    smoothPosition(0.0F, 0, 0, 0), smoothPosition(0.15F, 0, 0, 0), smoothPosition(0.5F, 0, -9, 0),
                    smoothPosition(0.75F, 0, -15.2F, 0), smoothPosition(DEATH_SECONDS, 0, -16, 0)))
            .addAnimation("torso", position(
                    smoothPosition(0.0F, 0, 0, 0), smoothPosition(0.15F, 0, 0.5F, 1), smoothPosition(0.5F, 0, -9, 1),
                    smoothPosition(0.75F, 0, -15.5F, 0), smoothPosition(DEATH_SECONDS, 0, -16.5F, 0)))
            .addAnimation("torso", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.15F, -12, 0, 3), smooth(0.5F, 14, 0, 5), smooth(0.78F, 52, 0, 7),
                    smooth(DEATH_SECONDS, 58, 0, 7)))
            .addAnimation("chest", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.15F, -8, 0, 0), smooth(0.55F, 8, 0, 0), smooth(0.85F, 20, 0, 0),
                    smooth(DEATH_SECONDS, 22, 0, 0)))
            .addAnimation("head", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.13F, -22, 6, 8), smooth(0.5F, -30, 4, 12),
                    smooth(0.85F, -52, 0, 18), smooth(DEATH_SECONDS, -46, 0, 20)))
            .addAnimation("jaw", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.15F, 30, 0, 0), smooth(0.6F, 48, 0, 0), smooth(0.85F, 60, 0, 0),
                    smooth(DEATH_SECONDS, 55, 0, 0)))
            .addAnimation("right_arm", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.15F, -25, 0, 18), smooth(0.55F, -10, 0, 30),
                    smooth(0.85F, -38, 0, 22), smooth(DEATH_SECONDS, -42, 0, 20)))
            .addAnimation("left_arm", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.15F, -25, 0, -18), smooth(0.55F, -10, 0, -30),
                    smooth(0.88F, -38, 0, -22), smooth(DEATH_SECONDS, -42, 0, -20)))
            .addAnimation("right_forearm", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.2F, -20, 0, 0), smooth(0.6F, 10, 0, 0), smooth(0.9F, -22, 0, 0),
                    smooth(DEATH_SECONDS, -18, 0, 0)))
            .addAnimation("left_forearm", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.2F, -20, 0, 0), smooth(0.6F, 10, 0, 0), smooth(0.93F, -22, 0, 0),
                    smooth(DEATH_SECONDS, -18, 0, 0)))
            .addAnimation("right_fingers", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.15F, 14, 0, 0), smooth(DEATH_SECONDS, 12, 0, 0)))
            .addAnimation("left_fingers", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.15F, 14, 0, 0), smooth(DEATH_SECONDS, 12, 0, 0)))
            .addAnimation("right_sleeve", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.25F, 25, 0, 0), smooth(0.65F, 40, 0, 0),
                    smooth(DEATH_SECONDS, 20, 0, 0)))
            .addAnimation("left_sleeve", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.25F, 25, 0, 0), smooth(0.65F, 40, 0, 0),
                    smooth(DEATH_SECONDS, 20, 0, 0)))
            .addAnimation("right_hair", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.2F, 20, 0, 0), smooth(0.6F, 34, 0, 0),
                    smooth(DEATH_SECONDS, 12, 0, 0)))
            .addAnimation("left_hair", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.2F, 20, 0, 0), smooth(0.6F, 34, 0, 0),
                    smooth(DEATH_SECONDS, 12, 0, 0)))
            .addAnimation("hair_back", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.2F, -10, 0, 0), smooth(0.6F, 30, 0, 0),
                    smooth(DEATH_SECONDS, 16, 0, 0)))
            .addAnimation("bell", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.2F, -30, 0, 0), smooth(0.6F, 25, 0, 10),
                    smooth(DEATH_SECONDS, -40, 0, 0)))
            .addAnimation("comb", rotation(
                    smooth(0.0F, 0, 0, 0), smooth(0.2F, -25, 0, 0), smooth(0.6F, 20, 0, -10),
                    smooth(DEATH_SECONDS, -45, 0, 0)))
            .build();

    private TsukumogamiAnimations() {
    }

    /**
     * An arm of the attack, raised behind the head and brought down. {@code out} is 1 for the right arm, -1 the left.
     */
    private static AnimationChannel attackArm(int out) {
        return rotation(
                smooth(0.0F, 0, 0, 0), smooth(0.4F, -72, 0, out * 9), smooth(0.8F, -128, 0, out * 14),
                linear(HIT - 0.13F, -133, 0, out * 14), linear(HIT - 0.04F, -84, 0, out * -5),
                smooth(HIT + 0.1F, -70, 0, out * -7), smooth(HIT + 0.5F, -20, 0, 0), smooth(ATTACK_SECONDS, 0, 0, 0));
    }

    /**
     * The cloth that hangs from a sleeve in the attack: it hangs down from the raised arm, is left behind as the arm
     * comes down, swings through after the hit and settles, {@code delay} seconds behind.
     */
    private static AnimationChannel attackSleeve(float delay) {
        return rotation(
                smooth(0.0F, 0, 0, 0), smooth(0.45F + delay, 52, 0, 0), smooth(0.85F + delay, 118, 0, 0),
                smooth(HIT - 0.06F + delay, 96, 0, 0), smooth(HIT + 0.2F + delay, -34, 0, 0),
                smooth(HIT + 0.45F + delay, 14, 0, 0), smooth(HIT + 0.7F + delay, -5, 0, 0),
                smooth(ATTACK_SECONDS, 0, 0, 0));
    }

    /** A lock of hair in the attack, left behind by the head and thrown forward after the hit, {@code amount} of it. */
    private static AnimationChannel attackHair(float delay, float amount) {
        return rotation(
                smooth(0.0F, 0, 0, 0), smooth(0.8F + delay, 16 * amount, 0, 0),
                smooth(HIT - 0.05F + delay, 22 * amount, 0, 0),
                smooth(HIT + 0.16F + delay, -30 * amount, 0, 0), smooth(HIT + 0.42F + delay, 9 * amount, 0, 0),
                smooth(HIT + 0.68F + delay, -3 * amount, 0, 0), smooth(ATTACK_SECONDS, 0, 0, 0));
    }

    /** A thing on the sash in the attack: it swings out as the body comes down and swings to rest after the hit. */
    private static AnimationChannel attackHanging(float delay, float amount) {
        return rotation(
                smooth(0.0F, 0, 0, 0), smooth(0.8F + delay, 14 * amount, 0, 0),
                smooth(HIT + 0.02F + delay, -38 * amount, 0, 0),
                smooth(HIT + 0.25F + delay, 26 * amount, 0, 6 * amount),
                smooth(HIT + 0.48F + delay, -14 * amount, 0, 0),
                smooth(HIT + 0.72F + delay, 5 * amount, 0, 0), smooth(ATTACK_SECONDS, 0, 0, 0));
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

    /** A rotation that stays the same for the whole animation. */
    private static AnimationChannel hold(float x, float y, float z) {
        return rotation(smooth(0, x, y, z));
    }

    /**
     * A rotation that swings evenly to and fro, between (x, y, z) and its opposite, {@code swings} times over a looping
     * animation of {@code length} seconds. {@code phase} is how far into a swing it starts, from 0 to 1, so the parts
     * don't all move together.
     */
    private static AnimationChannel swing(float length, int swings, float phase, float x, float y, float z) {
        Keyframe[] keyframes = new Keyframe[swings * KEYFRAMES_PER_SWING + 1];
        for (int i = 0; i < keyframes.length; i++) {
            float amount = Mth.sin(Mth.TWO_PI * ((float) i / KEYFRAMES_PER_SWING + phase));
            keyframes[i] = smooth(length * i / (keyframes.length - 1), x * amount, y * amount, z * amount);
        }
        return rotation(keyframes);
    }

    /** A rotation keyframe that eases in and out of its neighbours. */
    private static Keyframe smooth(float time, float x, float y, float z) {
        return new Keyframe(time, KeyframeAnimations.degreeVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
    }

    /** A rotation keyframe reached in a straight line, for holding still and for the sudden moves of the hit. */
    private static Keyframe linear(float time, float x, float y, float z) {
        return new Keyframe(time, KeyframeAnimations.degreeVec(x, y, z), AnimationChannel.Interpolations.LINEAR);
    }

    private static Keyframe smoothPosition(float time, float x, float y, float z) {
        return new Keyframe(time, KeyframeAnimations.posVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
    }

    private static Keyframe linearPosition(float time, float x, float y, float z) {
        return new Keyframe(time, KeyframeAnimations.posVec(x, y, z), AnimationChannel.Interpolations.LINEAR);
    }

    /** A scale keyframe: how many times its own size the part is. */
    private static Keyframe scale(float time, float x, float y, float z) {
        return new Keyframe(time, KeyframeAnimations.scaleVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
    }
}
