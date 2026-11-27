package com.elsistema.client.render;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** Cuadrúpedo propio: lobo colmillo de acero, oso de hielo y Bestia del Eclipse (modelos en MonsterLayers). */
public class BeastModel<T extends Entity> extends HierarchicalModel<T> {
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart leg0;
    private final ModelPart leg1;
    private final ModelPart leg2;
    private final ModelPart leg3;
    private final ModelPart tail;
    private final PartPose tailPose;

    public BeastModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.leg0 = root.getChild("leg0");
        this.leg1 = root.getChild("leg1");
        this.leg2 = root.getChild("leg2");
        this.leg3 = root.getChild("leg3");
        this.tail = root.getChild("tail");
        this.tailPose = this.tail.getInitialPose();
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
        this.tail.xRot = this.tailPose.xRot + Mth.sin(ageInTicks * 0.07F) * 0.06F - limbSwingAmount * 0.2F;
    }
}
