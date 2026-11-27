package com.elsistema.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Humanoide con proporciones propias (goblin, orco, caballero y guardián). HumanoidModel recoloca cada fotograma
 * la cabeza, el cuerpo, los brazos y las piernas a las posiciones de un jugador; aquí se devuelven a las del
 * modelo (cabeza grande del goblin, hombros anchos del orco...) sin perder las animaciones de andar y atacar.
 */
public class ProportionedHumanoidModel<T extends LivingEntity> extends HumanoidModel<T> {
    private final PartPose headPose;
    private final PartPose bodyPose;
    private final PartPose rightArmPose;
    private final PartPose leftArmPose;
    private final PartPose rightLegPose;
    private final PartPose leftLegPose;
    private final ModelPart earRight;
    private final ModelPart earLeft;
    private final PartPose earRightPose;
    private final PartPose earLeftPose;

    public ProportionedHumanoidModel(ModelPart root) {
        super(root);
        this.headPose = this.head.getInitialPose();
        this.bodyPose = this.body.getInitialPose();
        this.rightArmPose = this.rightArm.getInitialPose();
        this.leftArmPose = this.leftArm.getInitialPose();
        this.rightLegPose = this.rightLeg.getInitialPose();
        this.leftLegPose = this.leftLeg.getInitialPose();
        this.earRight = child(this.head, "ear_r");
        this.earLeft = child(this.head, "ear_l");
        this.earRightPose = this.earRight == null ? PartPose.ZERO : this.earRight.getInitialPose();
        this.earLeftPose = this.earLeft == null ? PartPose.ZERO : this.earLeft.getInitialPose();
    }

    private static ModelPart child(ModelPart part, String name) {
        try {
            return part.getChild(name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        float turn = this.body.yRot;
        float armX = Math.abs(this.rightArmPose.x);
        this.head.y = this.headPose.y;
        this.head.z = this.headPose.z;
        this.body.y = this.bodyPose.y;
        this.rightArm.y = this.rightArmPose.y;
        this.leftArm.y = this.leftArmPose.y;
        this.rightArm.x = -armX * Mth.cos(turn);
        this.rightArm.z = armX * Mth.sin(turn) + this.rightArmPose.z;
        this.leftArm.x = armX * Mth.cos(turn);
        this.leftArm.z = -armX * Mth.sin(turn) + this.leftArmPose.z;
        this.rightLeg.y = this.rightLegPose.y;
        this.leftLeg.y = this.leftLegPose.y;
        this.rightLeg.x = this.rightLegPose.x;
        this.leftLeg.x = this.leftLegPose.x;
        this.hat.copyFrom(this.head);
        // Las orejas del goblin se mueven solas
        if (this.earRight != null && this.earLeft != null) {
            float twitch = Mth.sin(ageInTicks * 0.09F) * 0.07F + Mth.sin(ageInTicks * 0.53F) * 0.02F;
            this.earRight.zRot = this.earRightPose.zRot + twitch;
            this.earLeft.zRot = this.earLeftPose.zRot - twitch;
        }
    }
}
