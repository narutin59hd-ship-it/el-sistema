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
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Registro de modelos y dibujo de los monstruos de mazmorra y de las sombras. */
public final class ModRenderers {
    private ModRenderers() {}

    public static final ModelLayerLocation LOBO_LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, "lobo_acero"), "main");
    public static final ModelLayerLocation OSO_LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, "oso_hielo"), "main");
    public static final ModelLayerLocation HORMIGA_LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, "hormiga"), "main");
    public static final ResourceLocation SHADOW_TEX = tex("sombra");

    public static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, "textures/entity/" + name + ".png");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register() {
        EntityModelLayerRegistry.registerModelLayer(LOBO_LAYER, BeastModel::createWolfLayer);
        EntityModelLayerRegistry.registerModelLayer(OSO_LAYER, BeastModel::createBearLayer);
        EntityModelLayerRegistry.registerModelLayer(HORMIGA_LAYER, InsectModel::createLayer);

        EntityRendererRegistry.register(ModEntities.GOBLIN, ctx -> new Humanoid(ctx, tex("goblin"), 0.72F));
        EntityRendererRegistry.register(ModEntities.ORCO, ctx -> new Humanoid(ctx, tex("orco"), 1.2F));
        EntityRendererRegistry.register(ModEntities.CABALLERO, ctx -> new Humanoid(ctx, tex("caballero"), 1.12F));
        EntityRendererRegistry.register(ModEntities.COLOSO, ctx -> new Humanoid(ctx, tex("coloso"), 1.75F));
        EntityRendererRegistry.register(ModEntities.LOBO, ctx -> new Beast(ctx, new BeastModel<>(ctx.bakeLayer(LOBO_LAYER)), tex("lobo_acero"), 0.6F));
        EntityRendererRegistry.register(ModEntities.OSO, ctx -> new Beast(ctx, new BeastModel<>(ctx.bakeLayer(OSO_LAYER)), tex("oso_hielo"), 1.0F));
        EntityRendererRegistry.register(ModEntities.HORMIGA, ctx -> new Beast(ctx, new InsectModel<>(ctx.bakeLayer(HORMIGA_LAYER)), tex("hormiga"), 0.8F));
        // Jefes secretos
        EntityRendererRegistry.register(ModEntities.GUARDIAN, ctx -> new Humanoid(ctx, tex("guardian_juicio"), 1.3F));
        EntityRendererRegistry.register(ModEntities.PRIMORDIAL, ctx -> new Humanoid(ctx, tex("coloso_primordial"), 2.2F));
        EntityRendererRegistry.register(ModEntities.ECLIPSE,
                ctx -> new Beast(ctx, new BeastModel<>(ctx.bakeLayer(LOBO_LAYER)), tex("bestia_eclipse"), 1.1F, 1.9F));
        EntityRendererRegistry.register(ModEntities.QUEEN,
                ctx -> new Beast(ctx, new InsectModel<>(ctx.bakeLayer(HORMIGA_LAYER)), tex("reina_carmesi"), 1.4F, 1.8F));

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) ->
                helper.register(new ShadowLayer((RenderLayerParent) entityRenderer)));
    }

    /** Monstruos con forma humana (goblin, orco, caballero y coloso) con su propia textura y tamaño. */
    public static class Humanoid extends HumanoidMobRenderer<DungeonMonster, HumanoidModel<DungeonMonster>> {
        private final ResourceLocation texture;
        private final float size;

        public Humanoid(EntityRendererProvider.Context ctx, ResourceLocation texture, float size) {
            super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5F * size);
            this.texture = texture;
            this.size = size;
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

    /** Monstruos con modelo propio (lobo, oso y hormiga) y los jefes secretos con forma de bestia. */
    public static class Beast extends MobRenderer<DungeonMonster, EntityModel<DungeonMonster>> {
        private final ResourceLocation texture;
        private final float size;

        public Beast(EntityRendererProvider.Context ctx, EntityModel<DungeonMonster> model, ResourceLocation texture, float shadow) {
            this(ctx, model, texture, shadow, 1.0F);
        }

        public Beast(EntityRendererProvider.Context ctx, EntityModel<DungeonMonster> model, ResourceLocation texture, float shadow,
                     float size) {
            super(ctx, model, shadow);
            this.texture = texture;
            this.size = size;
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
