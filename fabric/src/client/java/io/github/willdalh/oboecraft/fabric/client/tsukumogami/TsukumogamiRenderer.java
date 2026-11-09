package io.github.willdalh.oboecraft.fabric.client.tsukumogami;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.willdalh.oboecraft.core.Oboecraft;
import io.github.willdalh.oboecraft.fabric.client.OboecraftClient;
import io.github.willdalh.oboecraft.fabric.tsukumogami.Tsukumogami;
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
 * items, and the fade-out, in which it turns see-through and its items fall away.
 */
public final class TsukumogamiRenderer extends MobRenderer<Tsukumogami, TsukumogamiRenderState, TsukumogamiModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(id("tsukumogami"), "main");
    public static final ModelLayerLocation EYES_LAYER = new ModelLayerLocation(id("tsukumogami"), "eyes");
    private static final Identifier TEXTURE = id("textures/entity/tsukumogami/tsukumogami.png");
    private static final Identifier EYES_TEXTURE = id("textures/entity/tsukumogami/tsukumogami_eyes.png");
    private static final float SHADOW_RADIUS = 0.7F;

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

    /** A dim glow that flickers slowly, brighter while it winds up, and gone as it fades out. */
    private static float eyeGlow(TsukumogamiRenderState state, float ageInTicks) {
        float flicker = 0.55F + 0.15F * Mth.sin(ageInTicks * 0.13F) + 0.05F * Mth.sin(ageInTicks * 0.71F);
        float glow = state.windUpAnimationState.isStarted() ? Math.min(1, flicker + 0.35F) : flicker;
        return glow * (1 - state.fadeProgress);
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
        state.idleAnimationState.copyFrom(entity.idleAnimationState);
        state.windUpAnimationState.copyFrom(entity.windUpAnimationState);
        state.watchAnimationState.copyFrom(entity.watchAnimationState);
        state.fadeAnimationState.copyFrom(entity.fadeAnimationState);
        state.standsStill = entity.standsStill();
        state.fading = entity.isFading();
        state.fadeProgress = state.fading && entity.fadeAnimationState.isStarted()
                ? Math.clamp(entity.fadeAnimationState.getTimeInMillis(state.ageInTicks)
                        / (Tsukumogami.FADE_TICKS * 50.0F), 0, 1)
                : 0;
        OrbitingItems ring = rings.computeIfAbsent(entity, tsukumogami -> new OrbitingItems());
        ring.update(entity.orbitingItems(), state.fading, state.ageInTicks);
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

    /** See-through while it fades out, so it dissolves instead of vanishing. */
    @Override
    protected @Nullable RenderType getRenderType(TsukumogamiRenderState state, boolean isBodyVisible,
            boolean forceTransparent, boolean appearGlowing) {
        if (state.fading && isBodyVisible) {
            return RenderTypes.entityTranslucent(getTextureLocation(state));
        }
        return super.getRenderType(state, isBodyVisible, forceTransparent, appearGlowing);
    }

    @Override
    protected int getModelTint(TsukumogamiRenderState state) {
        return state.fading ? ARGB.white(1 - state.fadeProgress) : super.getModelTint(state);
    }
}
