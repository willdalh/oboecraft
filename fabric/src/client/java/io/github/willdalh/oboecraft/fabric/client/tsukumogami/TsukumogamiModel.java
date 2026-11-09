package io.github.willdalh.oboecraft.fabric.client.tsukumogami;

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
 * the ground, with a mask whose jaw opens, long thin hands like old tool handles and broken things hanging from its
 * sash. Its texture is 128 by 128 pixels.
 */
public final class TsukumogamiModel extends EntityModel<TsukumogamiRenderState> {
    private static final int TEXTURE_SIZE = 128;
    /** How far its upper body bends forward when nothing else moves it, in radians. */
    private static final float STOOP = 26 * Mth.DEG_TO_RAD;

    private final ModelPart head;
    private final KeyframeAnimation idleAnimation;
    private final KeyframeAnimation walkAnimation;
    private final KeyframeAnimation windUpAnimation;
    private final KeyframeAnimation watchAnimation;
    private final KeyframeAnimation fadeAnimation;

    public TsukumogamiModel(ModelPart roots) {
        super(roots);
        ModelPart root = roots.getChild("root");
        head = root.getChild("upper_body").getChild("head");
        idleAnimation = TsukumogamiAnimations.IDLE.bake(root);
        walkAnimation = TsukumogamiAnimations.WALK.bake(root);
        windUpAnimation = TsukumogamiAnimations.WIND_UP.bake(root);
        watchAnimation = TsukumogamiAnimations.WATCH.bake(root);
        fadeAnimation = TsukumogamiAnimations.FADE.bake(root);
    }

    private static MeshDefinition createMesh() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot().addOrReplaceChild(
                "root", CubeListBuilder.create(), PartPose.offset(0, 24, 0));

        // The kimono's skirt in two halves that swing from the hips, with a geta under each, and its frayed train.
        root.addOrReplaceChild("right_robe", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3, 0, -4, 6, 19, 8)
                        .texOffs(56, 0).addBox(-2, 19, -3, 4, 1, 6),
                PartPose.offset(-3, -20, 0));
        root.addOrReplaceChild("left_robe", CubeListBuilder.create()
                        .texOffs(28, 0).addBox(-3, 0, -4, 6, 19, 8)
                        .texOffs(56, 0).addBox(-2, 19, -3, 4, 1, 6),
                PartPose.offset(3, -20, 0));
        root.addOrReplaceChild("train", CubeListBuilder.create()
                        .texOffs(56, 8).addBox(-6, 0, 0, 12, 1, 5),
                PartPose.offset(0, -1, 3.5F));

        PartDefinition upperBody = root.addOrReplaceChild("upper_body", CubeListBuilder.create()
                        .texOffs(0, 28).addBox(-5, -16, -3.5F, 10, 16, 7)
                        .texOffs(34, 28).addBox(-5.5F, -4, -4, 11, 4, 8, new CubeDeformation(0.1F)),
                PartPose.offsetAndRotation(0, -20, 0.5F, STOOP, 0, 0));
        // Broken things that hang from the sash: a snapped comb, a cracked bell and a chipped cup.
        upperBody.addOrReplaceChild("comb", CubeListBuilder.create()
                        .texOffs(76, 0).addBox(-1, 0, -0.5F, 2, 4, 1),
                PartPose.offset(3, -0.5F, -4.3F));
        upperBody.addOrReplaceChild("bell", CubeListBuilder.create()
                        .texOffs(84, 0).addBox(-1.5F, 0, -1.5F, 3, 3, 3),
                PartPose.offset(-3.5F, -0.5F, -4.5F));
        upperBody.addOrReplaceChild("cup", CubeListBuilder.create()
                        .texOffs(96, 0).addBox(-1.5F, 0, -1.5F, 3, 2, 3),
                PartPose.offset(5.5F, -0.5F, 1));

        PartDefinition head = upperBody.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 52).addBox(-4.5F, -9.5F, -2.5F, 9, 11, 7)
                        .texOffs(34, 42).addBox(-4, -9, -3.5F, 8, 7, 1),
                PartPose.offsetAndRotation(0, -16, -1.5F, -STOOP, 0, 0));
        head.addOrReplaceChild("jaw", CubeListBuilder.create()
                        .texOffs(54, 42).addBox(-3, 0, -0.5F, 6, 3, 1),
                PartPose.offset(0, -2, -3));

        addArm(upperBody, "right", -1, 72, 112);
        addArm(upperBody, "left", 1, 92, 120);
        return mesh;
    }

    /**
     * A wide sleeve that hangs from the shoulder, with a long thin hand like an old tool handle and three fingers.
     *
     * @param side    -1 for the right arm, 1 for the left
     * @param sleeveU where the sleeve's texture starts
     * @param handU   where the hand's texture starts
     */
    private static void addArm(PartDefinition upperBody, String name, int side, int sleeveU, int handU) {
        PartDefinition arm = upperBody.addOrReplaceChild(name + "_arm", CubeListBuilder.create()
                        .texOffs(sleeveU, 16).addBox(side < 0 ? -4 : 0, -1, -3, 4, 11, 6),
                PartPose.offsetAndRotation(side * 5, -14.5F, 0, -0.2F, 0, side * 0.08F));
        arm.addOrReplaceChild(name + "_hand", CubeListBuilder.create()
                        .texOffs(handU, 0).addBox(-1, 0, -1, 2, 14, 2)
                        .texOffs(112, 16).addBox(-1.5F, 14, -1.5F, 1, 4, 1)
                        .texOffs(116, 16).addBox(-0.5F, 14, -1.5F, 1, 5, 1)
                        .texOffs(120, 16).addBox(0.5F, 14, -1.5F, 1, 4, 1),
                PartPose.offsetAndRotation(side * 2, 9, 0, -0.25F, 0, 0));
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
        head.yRot += Mth.clamp(state.yRot, -60, 60) * Mth.DEG_TO_RAD;
        head.xRot += state.xRot * Mth.DEG_TO_RAD * 0.5F;
        idleAnimation.apply(state.idleAnimationState, state.ageInTicks);
        if (!state.standsStill && !state.fading) {
            walkAnimation.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 1.0F, 1.0F);
        }
        windUpAnimation.apply(state.windUpAnimationState, state.ageInTicks);
        watchAnimation.apply(state.watchAnimationState, state.ageInTicks);
        fadeAnimation.apply(state.fadeAnimationState, state.ageInTicks);
    }
}
