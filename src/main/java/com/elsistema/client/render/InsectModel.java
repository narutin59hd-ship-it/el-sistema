package com.elsistema.client.render;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** Hormiga soldado y Reina Carmesí: mandíbulas que se abren, antenas que tantean y seis patas de dos tramos. */
public class InsectModel<T extends Entity> extends HierarchicalModel<T> {
    private static final String[] LEGS = {"leg0r", "leg0l", "leg1r", "leg1l", "leg2r", "leg2l"};
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart gaster;
    private final ModelPart mandibleRight;
    private final ModelPart mandibleLeft;
    private final ModelPart antennaRight;
    private final ModelPart antennaLeft;
    private final ModelPart[] legs = new ModelPart[6];
    private final PartPose[] legPoses = new PartPose[6];
    private final PartPose gasterPose;
    private final PartPose mandibleRightPose;
    private final PartPose mandibleLeftPose;
    private final PartPose antennaRightPose;
    private final PartPose antennaLeftPose;

    public InsectModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.gaster = root.getChild("thorax").getChild("gaster");
        this.mandibleRight = this.head.getChild("mandible_r");
        this.mandibleLeft = this.head.getChild("mandible_l");
        this.antennaRight = this.head.getChild("antenna_r");
        this.antennaLeft = this.head.getChild("antenna_l");
        for (int i = 0; i < 6; i++) {
            this.legs[i] = root.getChild(LEGS[i]);
            this.legPoses[i] = this.legs[i].getInitialPose();
        }
        this.gasterPose = this.gaster.getInitialPose();
        this.mandibleRightPose = this.mandibleRight.getInitialPose();
        this.mandibleLeftPose = this.mandibleLeft.getInitialPose();
        this.antennaRightPose = this.antennaRight.getInitialPose();
        this.antennaLeftPose = this.antennaLeft.getInitialPose();
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;
        this.gaster.xRot = this.gasterPose.xRot + Mth.sin(ageInTicks * 0.1F) * 0.04F;
        float bite = (0.5F + 0.5F * Mth.sin(ageInTicks * 0.22F)) * 0.28F;
        if (this.attackTime > 0.0F) bite = 0.45F * Mth.sin(this.attackTime * (float) Math.PI);
        this.mandibleRight.yRot = this.mandibleRightPose.yRot + bite;
        this.mandibleLeft.yRot = this.mandibleLeftPose.yRot - bite;
        this.antennaRight.xRot = this.antennaRightPose.xRot + Mth.sin(ageInTicks * 0.13F) * 0.12F;
        this.antennaLeft.xRot = this.antennaLeftPose.xRot + Mth.sin(ageInTicks * 0.13F + 1.7F) * 0.12F;
        float amount = Math.min(1.0F, limbSwingAmount);
        for (int i = 0; i < 6; i++) {
            // Paso en trípode: patas 0r, 1l y 2r juntas, y las otras tres al contrario
            int pair = i / 2;
            boolean right = i % 2 == 0;
            float phase = ((pair + (right ? 0 : 1)) % 2 == 0) ? 0.0F : (float) Math.PI;
            float swing = Mth.cos(limbSwing * 1.1F + phase) * 0.45F * amount;
            float lift = Math.max(0.0F, Mth.sin(limbSwing * 1.1F + phase)) * 0.35F * amount;
            PartPose p = this.legPoses[i];
            this.legs[i].yRot = p.yRot + (right ? swing : -swing);
            this.legs[i].zRot = p.zRot + (right ? lift : -lift);
        }
    }
}
