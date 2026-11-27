package com.elsistema.client.render;

import com.elsistema.DungeonMonster;
import com.elsistema.ElSistema;
import com.elsistema.ModEntities;
import com.elsistema.client.ClientShadows;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Registro de modelos y dibujo de los monstruos de mazmorra y de las sombras. */
public final class ModRenderers {
    private ModRenderers() {}

    public static final ModelLayerLocation GOBLIN_LAYER = layer("goblin");
    public static final ModelLayerLocation ORCO_LAYER = layer("orco");
    public static final ModelLayerLocation CABALLERO_LAYER = layer("caballero");
    public static final ModelLayerLocation GUARDIAN_LAYER = layer("guardian_juicio");
    public static final ModelLayerLocation COLOSO_LAYER = layer("coloso");
    public static final ModelLayerLocation LOBO_LAYER = layer("lobo_acero");
    public static final ModelLayerLocation OSO_LAYER = layer("oso_hielo");
    public static final ModelLayerLocation HORMIGA_LAYER = layer("hormiga");
    public static final ModelLayerLocation REINA_LAYER = layer("reina_carmesi");
    public static final ResourceLocation SHADOW_TEX = tex("sombra");

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, name), "main");
    }

    public static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, "textures/entity/" + name + ".png");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register() {
        EntityModelLayerRegistry.registerModelLayer(GOBLIN_LAYER, MonsterLayers::goblin);
        EntityModelLayerRegistry.registerModelLayer(ORCO_LAYER, MonsterLayers::orco);
        EntityModelLayerRegistry.registerModelLayer(CABALLERO_LAYER, MonsterLayers::caballero);
        EntityModelLayerRegistry.registerModelLayer(GUARDIAN_LAYER, MonsterLayers::guardian);
        EntityModelLayerRegistry.registerModelLayer(COLOSO_LAYER, MonsterLayers::coloso);
        EntityModelLayerRegistry.registerModelLayer(LOBO_LAYER, MonsterLayers::lobo);
        EntityModelLayerRegistry.registerModelLayer(OSO_LAYER, MonsterLayers::oso);
        EntityModelLayerRegistry.registerModelLayer(HORMIGA_LAYER, MonsterLayers::hormiga);
        EntityModelLayerRegistry.registerModelLayer(REINA_LAYER, MonsterLayers::reina);

        // Tamaños pensados para que cada monstruo llene su caja de colisión
        EntityRendererRegistry.register(ModEntities.GOBLIN, ctx -> new Humanoid(ctx, GOBLIN_LAYER, "goblin", 0.9F, false));
        EntityRendererRegistry.register(ModEntities.ORCO, ctx -> new Humanoid(ctx, ORCO_LAYER, "orco", 1.15F, true));
        EntityRendererRegistry.register(ModEntities.CABALLERO, ctx -> new Humanoid(ctx, CABALLERO_LAYER, "caballero", 1.1F, true));
        EntityRendererRegistry.register(ModEntities.COLOSO,
                ctx -> new Beast(ctx, new ColossusModel<>(ctx.bakeLayer(COLOSO_LAYER)), "coloso", 1.4F, 1.85F, true));
        EntityRendererRegistry.register(ModEntities.LOBO,
                ctx -> new Beast(ctx, new BeastModel<>(ctx.bakeLayer(LOBO_LAYER)), "lobo_acero", 0.6F, 0.9F, true));
        EntityRendererRegistry.register(ModEntities.OSO,
                ctx -> new Beast(ctx, new BeastModel<>(ctx.bakeLayer(OSO_LAYER)), "oso_hielo", 1.0F, 1.0F, true));
        EntityRendererRegistry.register(ModEntities.HORMIGA,
                ctx -> new Beast(ctx, new InsectModel<>(ctx.bakeLayer(HORMIGA_LAYER)), "hormiga", 0.8F, 1.0F, false));
        // Jefes secretos
        EntityRendererRegistry.register(ModEntities.GUARDIAN, ctx -> new Humanoid(ctx, GUARDIAN_LAYER, "guardian_juicio", 1.25F, true));
        EntityRendererRegistry.register(ModEntities.PRIMORDIAL,
                ctx -> new Beast(ctx, new ColossusModel<>(ctx.bakeLayer(COLOSO_LAYER)), "coloso_primordial", 1.7F, 2.35F, true));
        EntityRendererRegistry.register(ModEntities.ECLIPSE,
                ctx -> new Beast(ctx, new BeastModel<>(ctx.bakeLayer(LOBO_LAYER)), "bestia_eclipse", 1.1F, 1.6F, true));
        EntityRendererRegistry.register(ModEntities.QUEEN,
                ctx -> new Beast(ctx, new InsectModel<>(ctx.bakeLayer(REINA_LAYER)), "reina_carmesi", 1.4F, 1.7F, true));

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) ->
                helper.register(new ShadowLayer((RenderLayerParent) entityRenderer)));
    }

    /** Monstruos con forma humana (goblin, orco, caballero y guardián) con su modelo, textura y tamaño. */
    public static class Humanoid extends HumanoidMobRenderer<DungeonMonster, HumanoidModel<DungeonMonster>> {
        private final ResourceLocation texture;
        private final float size;

        public Humanoid(EntityRendererProvider.Context ctx, ModelLayerLocation layer, String name, float size, boolean glow) {
            super(ctx, new ProportionedHumanoidModel<>(ctx.bakeLayer(layer)), 0.5F * size);
            this.texture = tex(name);
            this.size = size;
            if (glow) this.addLayer(new GlowLayer<>(this, tex(name + "_brillo")));
        }

        @Override
        public ResourceLocation getTextureLocation(DungeonMonster entity) {
            return this.texture;
        }

        @Override
        protected void scale(DungeonMonster entity, PoseStack poseStack, float partialTick) {
            poseStack.scale(this.size, this.size, this.size);
        }
    }

    /** Monstruos con modelo propio (coloso, lobo, oso y hormiga) y los jefes secretos con esas formas. */
    public static class Beast extends MobRenderer<DungeonMonster, EntityModel<DungeonMonster>> {
        private final ResourceLocation texture;
        private final float size;

        public Beast(EntityRendererProvider.Context ctx, EntityModel<DungeonMonster> model, String name, float shadow, float size,
                     boolean glow) {
            super(ctx, model, shadow);
            this.texture = tex(name);
            this.size = size;
            if (glow) this.addLayer(new GlowLayer<>(this, tex(name + "_brillo")));
        }

        @Override
        public ResourceLocation getTextureLocation(DungeonMonster entity) {
            return this.texture;
        }

        @Override
        protected void scale(DungeonMonster entity, PoseStack poseStack, float partialTick) {
            if (this.size != 1.0F) poseStack.scale(this.size, this.size, this.size);
        }
    }

    /** Ojos, runas y grietas de magma que brillan en la oscuridad (como los ojos de las arañas). */
    public static class GlowLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
        private final RenderType type;

        public GlowLayer(RenderLayerParent<T, M> parent, ResourceLocation texture) {
            super(parent);
            this.type = RenderType.eyes(texture);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (ClientShadows.isShadow(entity) || entity.isInvisible()) return;
            this.getParentModel().renderToBuffer(poseStack, buffer.getBuffer(this.type), 0xF00000, OverlayTexture.NO_OVERLAY);
        }
    }

    /** Capa que oscurece a las sombras: las pinta de negro y morado casi opaco por encima de su textura. */
    public static class ShadowLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
        public ShadowLayer(RenderLayerParent<T, M> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!ClientShadows.isShadow(entity)) return;
            VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(SHADOW_TEX));
            float pulse = 0.5F + 0.5F * (float) Math.sin(ageInTicks * 0.08F);
            int alpha = 0xD8 + Math.round(pulse * 0x18);
            this.getParentModel().renderToBuffer(poseStack, vc, packedLight, LivingEntityRenderer.getOverlayCoords(entity, 0.0F),
                    (alpha << 24) | 0xFFFFFF);
        }
    }
}
