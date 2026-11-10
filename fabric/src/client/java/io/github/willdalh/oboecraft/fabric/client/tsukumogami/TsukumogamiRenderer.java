package io.github.willdalh.oboecraft.fabric.client.tsukumogami;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.willdalh.oboecraft.core.Oboecraft;
import io.github.willdalh.oboecraft.fabric.client.OboecraftClient;
import io.github.willdalh.oboecraft.fabric.tsukumogami.Tsukumogami;
import io.github.willdalh.oboecraft.fabric.tsukumogami.TsukumogamiMotion;
import io.github.willdalh.oboecraft.fabric.tsukumogami.TsukumogamiType;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Draws the tsukumogami: its model and animations, a dim glow in the mask's eye holes, the ring of its owner's overdue
 * items, and the fade-out, in which its items fall away and it dissolves from the hem of its kimono up to its mask.
 */
public final class TsukumogamiRenderer extends MobRenderer<Tsukumogami, TsukumogamiRenderState, TsukumogamiModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(id("tsukumogami"), "main");
    public static final ModelLayerLocation EYES_LAYER = new ModelLayerLocation(id("tsukumogami"), "eyes");
    private static final Identifier TEXTURE = id("textures/entity/tsukumogami/tsukumogami.png");
    private static final Identifier EYES_TEXTURE = id("textures/entity/tsukumogami/tsukumogami_eyes.png");
    /**
     * When each pixel of {@link #TEXTURE} dissolves as it fades out: the more see-through a pixel is here, the longer
     * it stays.
     */
    private static final Identifier DISSOLVE_TEXTURE = id("textures/entity/tsukumogami/tsukumogami_dissolve.png");
    private static final RenderType DISSOLVING = RenderTypes.entityCutoutDissolve(TEXTURE, DISSOLVE_TEXTURE);
    private static final float SHADOW_RADIUS = 0.7F;
    /** How long it takes to let go of what it was doing when it starts to fade out or is killed, in seconds. */
    private static final float LET_GO_SECONDS = 0.35F;
    /** How far into the wind-up the eyes glow at their brightest, in seconds. */
    private static final float EYES_FLARE_SECONDS = 0.8F;
    /** When the attack hits, in seconds, and how long the eyes take to dim again after it. */
    private static final float HIT_SECONDS = Tsukumogami.WIND_UP_TICKS / 20.0F;
    private static final float EYES_DIM_SECONDS = 0.6F;
    /** How far into the fade-out the eyes start to go out. They are the last of it to go. */
    private static final float EYES_FADE_START = 0.75F;

    /** The ring of each tsukumogami that is drawn, forgotten with the entity. */
    private final Map<Tsukumogami, OrbitingItems> rings = new WeakHashMap<>();

    public TsukumogamiRenderer(EntityRendererProvider.Context context) {
        super(context, new TsukumogamiModel(context.bakeLayer(LAYER)), SHADOW_RADIUS);
        addLayer(new LivingEntityEmissiveLayer<>(this, state -> EYES_TEXTURE, TsukumogamiRenderer::eyeGlow,
                new TsukumogamiModel(context.bakeLayer(EYES_LAYER)), RenderTypes::eyes, false));
    }

    /** Registers the renderer and its model layers, when the client starts. */
    public static void register() {
        ModelLayerRegistry.registerModelLayer(LAYER, TsukumogamiModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(EYES_LAYER, TsukumogamiModel::createEyesLayer);
        EntityRenderers.register(TsukumogamiType.TYPE, TsukumogamiRenderer::new);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Oboecraft.MOD_ID, path);
    }

    /**
     * A dim glow that flickers slowly. It flares up over the attack's wind-up and dims again after the hit, and goes
     * out last of all as it fades out and at once when it is killed.
     */
    private static float eyeGlow(TsukumogamiRenderState state, float ageInTicks) {
        float flicker = 0.32F + 0.07F * Mth.sin(ageInTicks * 0.13F) + 0.04F * Mth.sin(ageInTicks * 0.71F);
        float flare = state.attackWeight * Math.min(1, state.attackSeconds / EYES_FLARE_SECONDS)
                * (1 - Mth.clamp((state.attackSeconds - HIT_SECONDS) / EYES_DIM_SECONDS, 0, 1));
        float goingOut = Mth.clamp((state.fadeProgress - EYES_FADE_START) / (1 - EYES_FADE_START), 0, 1);
        return Mth.lerp(flare, flicker, 1) * (1 - goingOut) * (1 - state.deathWeight);
    }

    /** How far something that takes {@link #LET_GO_SECONDS} is after {@code seconds}, eased, from 0 to 1. */
    private static float letGo(float seconds) {
        float linear = Math.min(1, seconds / LET_GO_SECONDS);
        return linear * linear * (3 - 2 * linear);
    }

    @Override
    public Identifier getTextureLocation(TsukumogamiRenderState state) {
        return TEXTURE;
    }

    @Override
    public TsukumogamiRenderState createRenderState() {
        return new TsukumogamiRenderState();
    }

    @Override
    public void extractRenderState(Tsukumogami entity, TsukumogamiRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        TsukumogamiMotion motion = entity.motion();
        state.attackSeconds = motion.attackSeconds(partialTicks);
        state.attackWeight = motion.attackWeight(partialTicks);
        state.watchSeconds = motion.watchSeconds(partialTicks);
        state.watchWeight = motion.watchWeight(partialTicks);
        state.flinchSeconds = motion.flinchSeconds(partialTicks);
        state.fadeSeconds = motion.fadeSeconds(partialTicks);
        state.fadeWeight = letGo(state.fadeSeconds);
        state.fadeProgress = Math.min(1, state.fadeSeconds * 20 / Tsukumogami.FADE_TICKS);
        state.deathWeight = letGo(state.deathTime / 20);
        OrbitingItems ring = rings.computeIfAbsent(entity, tsukumogami -> new OrbitingItems());
        ring.update(entity.orbitingItems(), entity.isFading(), state.ageInTicks);
        state.orbit = ring.place(entity, state.ageInTicks, state.bodyRot, OboecraftClient.itemIcons(),
                itemModelResolver);
    }

    @Override
    public void submit(TsukumogamiRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera) {
        super.submit(state, poseStack, submitNodeCollector, camera);
        OrbitingItems.Placed orbit = state.orbit;
        if (orbit == null) {
            return;
        }
        for (int i = 0; i < orbit.count; i++) {
            poseStack.pushPose();
            poseStack.translate(orbit.x[i], orbit.y[i], orbit.z[i]);
            poseStack.rotateDegrees(Axis.YP, orbit.yRot[i]);
            poseStack.scale(orbit.scale[i], orbit.scale[i], orbit.scale[i]);
            orbit.stacks[i].submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY,
                    state.outlineColor);
            poseStack.popPose();
        }
    }

    /** While it fades out, its pixels go one by one, as {@link #DISSOLVE_TEXTURE} says. */
    @Override
    protected @Nullable RenderType getRenderType(TsukumogamiRenderState state, boolean isBodyVisible,
            boolean forceTransparent, boolean appearGlowing) {
        if (state.fadeSeconds > 0 && isBodyVisible) {
            return DISSOLVING;
        }
        return super.getRenderType(state, isBodyVisible, forceTransparent, appearGlowing);
    }

    /** The dissolve reads how far the fade-out is from the tint's alpha: the pixels left are those above it. */
    @Override
    protected int getModelTint(TsukumogamiRenderState state) {
        return state.fadeSeconds > 0 ? ARGB.white(1 - state.fadeProgress) : super.getModelTint(state);
    }

    /** Its shadow goes with it as it fades out. */
    @Override
    protected float getShadowStrength(TsukumogamiRenderState state) {
        return super.getShadowStrength(state) * (1 - state.fadeProgress);
    }

    /**
     * It collapses where it stands when it is killed, see {@link TsukumogamiAnimations#DEATH}, and doesn't fall over.
     */
    @Override
    protected float getFlipDegrees() {
        return 0;
    }
}
