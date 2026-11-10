package io.github.willdalh.oboecraft.fabric.client.tsukumogami;

import io.github.willdalh.oboecraft.fabric.tsukumogami.Tsukumogami;
import java.util.Set;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The tsukumogami's model: a menreiki, a noh mask that came alive. It is tall and stooped, in a kimono that drags on
 * the ground, with long hair around a mask whose jaw opens, long thin hands like old tool handles and broken things
 * hanging from its sash. Its texture is 128 by 128 pixels.
 *
 * <p>Every animation is added to the model's own pose, each as strongly as the frame says, so one blends into the
 * next: the living animations give way to each other and all of them to fading out and to dying.
 */
public final class TsukumogamiModel extends EntityModel<TsukumogamiRenderState> {
    private static final int TEXTURE_SIZE = 128;
    private static final float MILLIS_PER_SECOND = 1000;
    /** How far its head turns to either side to look at its owner, in degrees. */
    private static final float MAX_HEAD_TURN = 50;
    /** How much of the way its head tips up or down to look at its owner. The stoop does the rest. */
    private static final float HEAD_PITCH_SHARE = 0.5F;
    /** How fast Minecraft's walk position rises for each block walked. */
    private static final float WALK_POSITION_PER_BLOCK = 4;
    /** Runs the walk once for every two steps walked. */
    private static final float WALK_SPEED_FACTOR = TsukumogamiAnimations.WALK.lengthInSeconds() * MILLIS_PER_SECOND
            / (2 * Tsukumogami.STEP_BLOCKS * WALK_POSITION_PER_BLOCK * 50);
    /** Shows the whole walk at its walking speed, where Minecraft's walk speed is about a third. */
    private static final float WALK_SCALE_FACTOR = 3;
    /** How much of the idle sway is left under the attack, and while it watches its owner. */
    private static final float IDLE_UNDER_ATTACK = 0.2F;
    private static final float IDLE_WHILE_WATCHING = 0.45F;

    private final ModelPart head;
    private final KeyframeAnimation idleAnimation;
    private final KeyframeAnimation walkAnimation;
    private final KeyframeAnimation attackAnimation;
    private final KeyframeAnimation watchAnimation;
    private final KeyframeAnimation flinchAnimation;
    private final KeyframeAnimation fadeAnimation;
    private final KeyframeAnimation deathAnimation;

    public TsukumogamiModel(ModelPart roots) {
        super(roots);
        ModelPart root = roots.getChild("root");
        head = root.getChild("torso").getChild("chest").getChild("head");
        idleAnimation = TsukumogamiAnimations.IDLE.bake(root);
        walkAnimation = TsukumogamiAnimations.WALK.bake(root);
        attackAnimation = TsukumogamiAnimations.ATTACK.bake(root);
        watchAnimation = TsukumogamiAnimations.WATCH.bake(root);
        flinchAnimation = TsukumogamiAnimations.FLINCH.bake(root);
        fadeAnimation = TsukumogamiAnimations.FADE.bake(root);
        deathAnimation = TsukumogamiAnimations.DEATH.bake(root);
    }

    private static MeshDefinition createMesh() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot().addOrReplaceChild(
                "root", CubeListBuilder.create(), PartPose.offset(0, 24, 0));
        // The kimono below the sash, with its two fronts, the left over the right, and the train it drags.
        PartDefinition robe = root.addOrReplaceChild("robe", CubeListBuilder.create()
                        .texOffs(0, 20).addBox(-5.5F, 0, -3.5F, 11, 10, 7)
                        .texOffs(30, 0).addBox(-6, 10, -4, 12, 10, 8),
                PartPose.offset(0, -20, 0));
        robe.addOrReplaceChild("right_robe_front", CubeListBuilder.create()
                        .texOffs(18, 0).addBox(-2.5F, 0, -1, 5, 19, 1),
                PartPose.offset(-3.5F, 1, -4));
        robe.addOrReplaceChild("left_robe_front", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4, 0, -1, 8, 19, 1),
                PartPose.offset(2, 1, -4.4F));
        // A geta and the toes of a tabi under each front.
        root.addOrReplaceChild("right_foot", CubeListBuilder.create()
                        .texOffs(0, 65).addBox(-2, -3, -4.5F, 4, 1, 6)
                        .texOffs(76, 72).addBox(-2, -2, -3.5F, 4, 2, 1)
                        .texOffs(76, 72).addBox(-2, -2, -0.5F, 4, 2, 1)
                        .texOffs(90, 65).addBox(-1.5F, -5, -4, 3, 2, 4),
                PartPose.offset(-3, 0, -3));
        root.addOrReplaceChild("left_foot", CubeListBuilder.create()
                        .texOffs(20, 65).addBox(-2, -3, -4.5F, 4, 1, 6)
                        .texOffs(76, 72).addBox(-2, -2, -3.5F, 4, 2, 1)
                        .texOffs(76, 72).addBox(-2, -2, -0.5F, 4, 2, 1)
                        .texOffs(104, 65).addBox(-1.5F, -5, -4, 3, 2, 4),
                PartPose.offset(3, 0, -3));
        PartDefinition train = robe.addOrReplaceChild("train", CubeListBuilder.create()
                        .texOffs(56, 65).addBox(-6, 0, 0, 12, 1, 5),
                tilted(0, 16, 3.5F, -50, 0, 0));
        train.addOrReplaceChild("train_tip", CubeListBuilder.create()
                        .texOffs(80, 52).addBox(-5, 0, 0, 10, 1, 6),
                tilted(0, 0.5F, 5, 46, 0, 0));
        // The stooped back in two parts, with the sash and its knot.
        PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create()
                        .texOffs(32, 37).addBox(-5, -7, -3.5F, 10, 7, 7)
                        .texOffs(66, 37).addBox(-5.5F, -5, -4, 11, 5, 8, new CubeDeformation(0.2F))
                        .texOffs(52, 52).addBox(-4, -6.5F, 4, 8, 6, 2)
                        .texOffs(0, 72).addBox(-2.5F, -0.5F, 4.5F, 5, 5, 1),
                tilted(0, -20, 0, 14, 0, 0));
        // Broken things that hang from the sash: a snapped comb, a cracked bell and a chipped cup.
        torso.addOrReplaceChild("comb", CubeListBuilder.create()
                        .texOffs(40, 65).addBox(-1.5F, 0, -0.5F, 3, 6, 1),
                tilted(2.5F, -0.5F, -4.5F, -14, 0, 0));
        torso.addOrReplaceChild("bell", CubeListBuilder.create()
                        .texOffs(86, 72).addBox(-0.5F, 0, -0.5F, 1, 2, 1)
                        .texOffs(12, 72).addBox(-1.5F, 2, -1.5F, 3, 3, 3),
                tilted(-3.5F, -0.5F, -4.7F, -14, 0, 0));
        torso.addOrReplaceChild("cup", CubeListBuilder.create()
                        .texOffs(72, 72).addBox(-0.5F, 0, -0.5F, 1, 3, 1)
                        .texOffs(46, 72).addBox(-1.5F, 3, -1.5F, 3, 2, 3),
                tilted(6, -0.5F, 0.5F, -14, 0, 0));
        PartDefinition chest = torso.addOrReplaceChild("chest", CubeListBuilder.create()
                        .texOffs(56, 20).addBox(-5.5F, -8, -3.5F, 11, 8, 7),
                tilted(0, -7, 0, 14, 0, 0));
        // Long hair, and the mask over it, with its nose and a jaw that hangs from it.
        PartDefinition head = chest.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(70, 0).addBox(-4.5F, -10, -4, 9, 10, 8)
                        .texOffs(18, 52).addBox(-4, -10, -5, 8, 8, 1)
                        .texOffs(66, 72).addBox(-1, -6, -6, 2, 3, 1),
                tilted(0, -8, -1, -18, 0, 0));
        head.addOrReplaceChild("jaw", CubeListBuilder.create()
                        .texOffs(32, 72).addBox(-3, 0, -3.5F, 6, 4, 1),
                PartPose.offset(0, -2, -1.5F));
        head.addOrReplaceChild("hair_back", CubeListBuilder.create()
                        .texOffs(36, 20).addBox(-4.5F, 0, 0, 9, 16, 1),
                tilted(0, -9, 4, 15, 0, 0));
        head.addOrReplaceChild("right_hair", CubeListBuilder.create()
                        .texOffs(104, 0).addBox(-0.5F, 0, -1.5F, 1, 15, 3),
                tilted(-4.6F, -8, -3, -10, 0, 0));
        head.addOrReplaceChild("left_hair", CubeListBuilder.create()
                        .texOffs(24, 37).addBox(-0.5F, 0, -1.5F, 1, 12, 3),
                tilted(4.6F, -8, -3, -10, 0, 0));
        // Each arm: a sleeve in two parts, the cloth that hangs from it, and a long thin hand with its fingers.
        PartDefinition rightArm = chest.addOrReplaceChild("right_arm", CubeListBuilder.create()
                        .texOffs(92, 20).addBox(-4, -1.5F, -2.5F, 4, 10, 5),
                tilted(-5.5F, -6.5F, 0, -22, 0, 3));
        PartDefinition rightForearm = rightArm.addOrReplaceChild("right_forearm", CubeListBuilder.create()
                        .texOffs(104, 37).addBox(-2, 0, -2.5F, 4, 8, 5, new CubeDeformation(0.2F)),
                tilted(-2, 8.5F, 0, -16, 0, 0));
        rightForearm.addOrReplaceChild("right_sleeve", CubeListBuilder.create()
                        .texOffs(0, 37).addBox(-2, 0, -1, 4, 13, 2),
                tilted(0, 1, 2.5F, 12, 0, 0));
        PartDefinition rightHand = rightForearm.addOrReplaceChild("right_hand", CubeListBuilder.create()
                        .texOffs(36, 52).addBox(-1, 0, -1, 2, 7, 2),
                tilted(0, 8, 0, -8, 0, 0));
        rightHand.addOrReplaceChild("right_fingers", CubeListBuilder.create()
                        .texOffs(48, 65).addBox(-1.5F, 0, -1, 1, 6, 1)
                        .texOffs(72, 52).addBox(-0.5F, 0, -1, 1, 7, 1)
                        .texOffs(24, 72).addBox(0.5F, 0, -1, 1, 5, 1)
                        .texOffs(58, 72).addBox(0.5F, -2, 0, 1, 4, 1),
                tilted(0, 7, 0, -12, 0, 0));
        PartDefinition leftArm = chest.addOrReplaceChild("left_arm", CubeListBuilder.create()
                        .texOffs(110, 20).addBox(0, -1.5F, -2.5F, 4, 10, 5),
                tilted(5.5F, -6.5F, 0, -22, 0, -3));
        PartDefinition leftForearm = leftArm.addOrReplaceChild("left_forearm", CubeListBuilder.create()
                        .texOffs(0, 52).addBox(-2, 0, -2.5F, 4, 8, 5, new CubeDeformation(0.2F)),
                tilted(2, 8.5F, 0, -16, 0, 0));
        leftForearm.addOrReplaceChild("left_sleeve", CubeListBuilder.create()
                        .texOffs(12, 37).addBox(-2, 0, -1, 4, 13, 2),
                tilted(0, 1, 2.5F, 12, 0, 0));
        PartDefinition leftHand = leftForearm.addOrReplaceChild("left_hand", CubeListBuilder.create()
                        .texOffs(44, 52).addBox(-1, 0, -1, 2, 7, 2),
                tilted(0, 8, 0, -8, 0, 0));
        leftHand.addOrReplaceChild("left_fingers", CubeListBuilder.create()
                        .texOffs(52, 65).addBox(0.5F, 0, -1, 1, 6, 1)
                        .texOffs(76, 52).addBox(-0.5F, 0, -1, 1, 7, 1)
                        .texOffs(28, 72).addBox(-1.5F, 0, -1, 1, 5, 1)
                        .texOffs(62, 72).addBox(-1.5F, -2, 0, 1, 4, 1),
                tilted(0, 7, 0, -12, 0, 0));
        return mesh;
    }

    /** A part at a place, turned by angles in degrees. */
    private static PartPose tilted(float x, float y, float z, float xDegrees, float yDegrees, float zDegrees) {
        return PartPose.offsetAndRotation(
                x, y, z, xDegrees * Mth.DEG_TO_RAD, yDegrees * Mth.DEG_TO_RAD, zDegrees * Mth.DEG_TO_RAD);
    }

    public static LayerDefinition createBodyLayer() {
        return LayerDefinition.create(createMesh(), TEXTURE_SIZE, TEXTURE_SIZE);
    }

    /** Only the head, for the dim glow in the mask's eye holes. */
    public static LayerDefinition createEyesLayer() {
        MeshDefinition mesh = createMesh();
        mesh.getRoot().retainExactParts(Set.of("head"));
        return LayerDefinition.create(mesh, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    @Override
    public void setupAnim(TsukumogamiRenderState state) {
        super.setupAnim(state);
        // What is left of the living animations while it fades out or dies.
        float alive = (1 - state.fadeWeight) * (1 - state.deathWeight);
        float calm = alive * (1 - state.attackWeight);
        head.yRot += Mth.clamp(state.yRot, -MAX_HEAD_TURN, MAX_HEAD_TURN) * Mth.DEG_TO_RAD * alive;
        head.xRot += state.xRot * Mth.DEG_TO_RAD * HEAD_PITCH_SHARE * alive;
        float idle = alive * Mth.lerp(state.attackWeight, 1, IDLE_UNDER_ATTACK)
                * Mth.lerp(state.watchWeight, 1, IDLE_WHILE_WATCHING);
        apply(idleAnimation, state.ageInTicks / 20, idle);
        walkAnimation.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, WALK_SPEED_FACTOR,
                WALK_SCALE_FACTOR * calm * (1 - state.watchWeight));
        apply(watchAnimation, state.watchSeconds, state.watchWeight * calm);
        apply(attackAnimation, state.attackSeconds, state.attackWeight * alive);
        apply(flinchAnimation, state.flinchSeconds, state.flinchSeconds > 0 ? alive : 0);
        apply(fadeAnimation, state.fadeSeconds, state.fadeSeconds > 0 ? 1 : 0);
        apply(deathAnimation, state.deathTime / 20, state.deathTime > 0 ? 1 : 0);
    }

    /** Adds an animation to the pose, {@code weight} of the way, at {@code seconds} after its start. */
    private static void apply(KeyframeAnimation animation, float seconds, float weight) {
        if (weight > 0) {
            animation.apply((long) (seconds * MILLIS_PER_SECOND), weight);
        }
    }
}
