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

/** Hormiga soldado: cabeza con mandíbulas, tórax, abdomen y seis patas. */
public class InsectModel<T extends Entity> extends HierarchicalModel<T> {
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart abdomen;
    private final ModelPart[] legs = new ModelPart[6];
    private static final float[] SPREAD = {0.45F, 0.0F, -0.45F};

    public InsectModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.abdomen = root.getChild("abdomen");
        for (int i = 0; i < 6; i++) {
            this.legs[i] = root.getChild("leg" + i);
        }
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition p = mesh.getRoot();
        p.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.0F, -3.0F, -5.0F, 6.0F, 5.0F, 5.0F)
                .texOffs(24, 0).addBox(-3.0F, 0.5F, -8.0F, 2.0F, 1.0F, 3.0F)
                .texOffs(24, 5).addBox(1.0F, 0.5F, -8.0F, 2.0F, 1.0F, 3.0F)
                .texOffs(36, 0).addBox(-2.5F, -7.0F, -4.0F, 1.0F, 4.0F, 1.0F)
                .texOffs(40, 0).addBox(1.5F, -7.0F, -4.0F, 1.0F, 4.0F, 1.0F), PartPose.offset(0.0F, 17.0F, -5.0F));
        p.addOrReplaceChild("thorax", CubeListBuilder.create()
                .texOffs(0, 12).addBox(-3.5F, -3.0F, -3.0F, 7.0F, 5.0F, 7.0F), PartPose.offset(0.0F, 17.0F, -2.0F));
        p.addOrReplaceChild("abdomen", CubeListBuilder.create()
                .texOffs(0, 26).addBox(-5.0F, -4.0F, 0.0F, 10.0F, 8.0F, 12.0F), PartPose.offsetAndRotation(0.0F, 16.0F, 2.0F, -0.2F, 0.0F, 0.0F));
        for (int i = 0; i < 3; i++) {
            float z = -4.0F + i * 2.5F;
            p.addOrReplaceChild("leg" + (i * 2), CubeListBuilder.create().texOffs(36, 10).addBox(-12.0F, -1.0F, -1.0F, 12.0F, 2.0F, 2.0F),
                    PartPose.offset(-3.0F, 17.0F, z));
            p.addOrReplaceChild("leg" + (i * 2 + 1), CubeListBuilder.create().texOffs(36, 10).mirror().addBox(0.0F, -1.0F, -1.0F, 12.0F, 2.0F, 2.0F),
                    PartPose.offset(3.0F, 17.0F, z));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;
        this.abdomen.xRot = -0.2F + Mth.sin(ageInTicks * 0.1F) * 0.04F;
        for (int i = 0; i < 3; i++) {
            float phase = i * (float) Math.PI * 2.0F / 3.0F;
            float swing = Mth.cos(limbSwing * 1.3F + phase) * 0.45F * limbSwingAmount;
            float lift = Math.abs(Mth.sin(limbSwing * 0.65F + phase)) * 0.35F * limbSwingAmount;
            ModelPart left = this.legs[i * 2];
            ModelPart right = this.legs[i * 2 + 1];
            left.yRot = -SPREAD[i] + swing;
            right.yRot = SPREAD[i] - swing;
            left.zRot = -0.6F + lift;
            right.zRot = 0.6F - lift;
        }
    }
}
