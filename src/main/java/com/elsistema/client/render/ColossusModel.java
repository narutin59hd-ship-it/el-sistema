package com.elsistema.client.render;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** Coloso de piedra (y el Coloso Primordial): pasos pesados, brazos que llegan al suelo y golpe a dos manos. */
public class ColossusModel<T extends Entity> extends HierarchicalModel<T> {
    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public ColossusModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = root.getChild("head");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float walk = limbSwing * 0.45F;
        float amount = Math.min(1.0F, limbSwingAmount);
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.6F;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD * 0.5F;
        this.rightLeg.xRot = Mth.cos(walk) * 0.9F * amount;
        this.leftLeg.xRot = -Mth.cos(walk) * 0.9F * amount;
        this.body.zRot = Mth.cos(walk) * 0.05F * amount;
        this.body.xRot = 0.04F + Mth.sin(ageInTicks * 0.05F) * 0.015F;
        float swingR = -Mth.cos(walk) * 0.7F * amount;
        float swingL = Mth.cos(walk) * 0.7F * amount;
        float breathe = Mth.sin(ageInTicks * 0.05F) * 0.03F;
        if (this.attackTime > 0.0F) {
            // Golpe a dos manos: sube los brazos y los deja caer
            float a = Mth.sin(this.attackTime * (float) Math.PI);
            swingR = -2.2F * a;
            swingL = -2.2F * a;
        }
        this.rightArm.xRot = swingR;
        this.leftArm.xRot = swingL;
        this.rightArm.zRot = 0.06F + breathe;
        this.leftArm.zRot = -0.06F - breathe;
    }
}
