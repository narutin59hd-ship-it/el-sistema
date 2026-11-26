package com.elsistema.client.render;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** Cuadrúpedo propio: lobo colmillo de acero y oso de hielo (mismas piezas, otras proporciones). */
public class BeastModel<T extends Entity> extends HierarchicalModel<T> {
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart leg0;
    private final ModelPart leg1;
    private final ModelPart leg2;
    private final ModelPart leg3;
    private final ModelPart tail;

    public BeastModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.leg0 = root.getChild("leg0");
        this.leg1 = root.getChild("leg1");
        this.leg2 = root.getChild("leg2");
        this.leg3 = root.getChild("leg3");
        this.tail = root.getChild("tail");
    }

    /** Lobo colmillo de acero (textura 64x64). */
    public static LayerDefinition createWolfLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition p = mesh.getRoot();
        p.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -3.5F, -8.0F, 8.0F, 7.0F, 16.0F)
                .texOffs(0, 24).addBox(-5.0F, -4.5F, -9.0F, 10.0F, 9.0F, 7.0F), PartPose.offset(0.0F, 13.0F, 0.0F));
        p.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 41).addBox(-3.5F, -3.5F, -4.0F, 7.0F, 7.0F, 6.0F)
                .texOffs(28, 41).addBox(-2.0F, -0.5F, -8.0F, 4.0F, 3.0F, 4.0F)
                .texOffs(44, 41).addBox(-3.5F, -6.5F, -1.0F, 2.0F, 3.0F, 1.0F)
                .texOffs(50, 41).addBox(1.5F, -6.5F, -1.0F, 2.0F, 3.0F, 1.0F), PartPose.offset(0.0F, 12.0F, -9.0F));
        CubeListBuilder leg = CubeListBuilder.create().texOffs(48, 0).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 9.0F, 3.0F);
        p.addOrReplaceChild("leg0", leg, PartPose.offset(-2.5F, 15.0F, 6.0F));
        p.addOrReplaceChild("leg1", leg, PartPose.offset(2.5F, 15.0F, 6.0F));
        p.addOrReplaceChild("leg2", leg, PartPose.offset(-2.5F, 15.0F, -6.0F));
        p.addOrReplaceChild("leg3", leg, PartPose.offset(2.5F, 15.0F, -6.0F));
        p.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(48, 13).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 9.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 11.0F, 8.0F, 0.9F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Oso de hielo (textura 128x64). */
    public static LayerDefinition createBearLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition p = mesh.getRoot();
        p.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-7.0F, -7.0F, -12.0F, 14.0F, 14.0F, 24.0F), PartPose.offset(0.0F, 10.0F, 0.0F));
        p.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 40).addBox(-5.0F, -5.0F, -6.0F, 10.0F, 9.0F, 8.0F)
                .texOffs(38, 40).addBox(-3.0F, 0.0F, -10.0F, 6.0F, 4.0F, 4.0F)
                .texOffs(60, 40).addBox(-5.0F, -7.0F, -2.0F, 3.0F, 3.0F, 2.0F)
                .texOffs(72, 40).addBox(2.0F, -7.0F, -2.0F, 3.0F, 3.0F, 2.0F), PartPose.offset(0.0F, 8.0F, -13.0F));
        CubeListBuilder leg = CubeListBuilder.create().texOffs(78, 0).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 9.0F, 6.0F);
        p.addOrReplaceChild("leg0", leg, PartPose.offset(-4.5F, 15.0F, 8.0F));
        p.addOrReplaceChild("leg1", leg, PartPose.offset(4.5F, 15.0F, 8.0F));
        p.addOrReplaceChild("leg2", leg, PartPose.offset(-4.5F, 15.0F, -8.0F));
        p.addOrReplaceChild("leg3", leg, PartPose.offset(4.5F, 15.0F, -8.0F));
        p.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(78, 17).addBox(-1.5F, 0.0F, -1.0F, 3.0F, 3.0F, 3.0F),
                PartPose.offset(0.0F, 5.0F, 12.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;
        float swing = Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
        this.leg0.xRot = swing;
        this.leg1.xRot = -swing;
        this.leg2.xRot = -swing;
        this.leg3.xRot = swing;
        this.tail.yRot = Mth.cos(ageInTicks * 0.15F) * 0.25F;
    }
}
