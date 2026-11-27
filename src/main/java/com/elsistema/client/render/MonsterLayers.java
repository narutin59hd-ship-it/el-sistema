package com.elsistema.client.render;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Modelos de los monstruos (desde la 1.5.0). ARCHIVO GENERADO por tools/gen_modelos.py a partir de
 * tools/modelos.py: no lo edites a mano (texturas.py comprueba que coincide con las texturas).
 */
@SuppressWarnings("unused")
public final class MonsterLayers {
    private MonsterLayers() {}

    /** goblin (textura 64x32, dibujada a 128x64). */
    public static LayerDefinition goblin() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p1 = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.5F, -8.0F, -4.0F, 9.0F, 8.0F, 8.0F)
                .texOffs(0, 26).addBox(-1.0F, -4.5F, -5.5F, 2.0F, 3.0F, 2.0F)
                .texOffs(30, 26).addBox(-4.0F, -6.5F, -4.6F, 8.0F, 1.0F, 1.0F),
                PartPose.offset(0.0F, 7.0F, -0.5F));
        PartDefinition p2 = p1.addOrReplaceChild("ear_r", CubeListBuilder.create()
                .texOffs(8, 26).addBox(-5.0F, -1.5F, -0.5F, 5.0F, 3.0F, 1.0F),
                PartPose.offsetAndRotation(-4.5F, -5.0F, 0.0F, 0.0F, 0.45F, 0.25F));
        PartDefinition p3 = p1.addOrReplaceChild("ear_l", CubeListBuilder.create()
                .texOffs(8, 26).mirror().addBox(0.0F, -1.5F, -0.5F, 5.0F, 3.0F, 1.0F).mirror(false),
                PartPose.offsetAndRotation(4.5F, -5.0F, 0.0F, 0.0F, -0.45F, -0.25F));
        PartDefinition p4 = root.addOrReplaceChild("hat", CubeListBuilder.create(),
                PartPose.offset(0.0F, 7.0F, -0.5F));
        PartDefinition p5 = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(34, 0).addBox(-3.5F, 0.0F, -2.0F, 7.0F, 10.0F, 4.0F)
                .texOffs(12, 16).addBox(-4.0F, 8.0F, -2.5F, 8.0F, 2.0F, 5.0F, new CubeDeformation(0.05F))
                .texOffs(38, 16).addBox(-2.5F, 10.0F, -2.8F, 5.0F, 4.0F, 1.0F),
                PartPose.offset(0.0F, 7.0F, 0.0F));
        PartDefinition p6 = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(56, 0).addBox(-1.5F, -1.0F, -1.0F, 2.0F, 11.0F, 2.0F)
                .texOffs(50, 16).addBox(-1.5F, 5.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.3F)),
                PartPose.offset(-4.5F, 9.0F, 0.0F));
        PartDefinition p7 = root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(56, 0).mirror().addBox(-0.5F, -1.0F, -1.0F, 2.0F, 11.0F, 2.0F).mirror(false)
                .texOffs(50, 16).mirror().addBox(-0.5F, 5.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.3F)).mirror(false),
                PartPose.offset(4.5F, 9.0F, 0.0F));
        PartDefinition p8 = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 16).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 7.0F, 3.0F)
                .texOffs(20, 26).addBox(-1.5F, 5.0F, -3.0F, 3.0F, 2.0F, 2.0F),
                PartPose.offset(-1.8F, 17.0F, 0.0F));
        PartDefinition p9 = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(0, 16).mirror().addBox(-1.5F, 0.0F, -1.5F, 3.0F, 7.0F, 3.0F).mirror(false)
                .texOffs(20, 26).mirror().addBox(-1.5F, 5.0F, -3.0F, 3.0F, 2.0F, 2.0F).mirror(false),
                PartPose.offset(1.8F, 17.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** orco (textura 128x48, dibujada a 256x96). */
    public static LayerDefinition orco() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p1 = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(52, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F)
                .texOffs(18, 28).addBox(-4.5F, -7.2F, -4.6F, 9.0F, 1.0F, 1.0F)
                .texOffs(110, 18).addBox(-3.5F, -3.0F, -5.2F, 7.0F, 3.0F, 2.0F)
                .texOffs(14, 28).addBox(-3.0F, -4.6F, -5.7F, 1.0F, 2.0F, 1.0F)
                .texOffs(14, 28).mirror().addBox(2.0F, -4.6F, -5.7F, 1.0F, 2.0F, 1.0F).mirror(false)
                .texOffs(100, 18).addBox(-1.0F, -10.5F, 0.5F, 2.0F, 3.0F, 3.0F)
                .texOffs(0, 28).addBox(-5.0F, -6.0F, -1.0F, 1.0F, 3.0F, 2.0F)
                .texOffs(0, 28).mirror().addBox(4.0F, -6.0F, -1.0F, 1.0F, 3.0F, 2.0F).mirror(false),
                PartPose.offset(0.0F, -0.5F, -1.5F));
        PartDefinition p2 = root.addOrReplaceChild("hat", CubeListBuilder.create(),
                PartPose.offset(0.0F, -0.5F, -1.5F));
        PartDefinition p3 = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5.0F, 0.0F, -3.0F, 10.0F, 12.0F, 6.0F)
                .texOffs(24, 18).addBox(-5.5F, 9.0F, -3.5F, 11.0F, 2.0F, 7.0F, new CubeDeformation(0.05F))
                .texOffs(104, 0).addBox(-5.2F, -0.2F, -3.3F, 10.0F, 10.0F, 1.0F, new CubeDeformation(0.05F)),
                PartPose.offset(0.0F, 1.0F, 0.0F));
        PartDefinition p4 = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(32, 0).addBox(-3.0F, -2.0F, -2.5F, 5.0F, 13.0F, 5.0F)
                .texOffs(80, 18).addBox(-3.0F, 7.0F, -2.5F, 5.0F, 3.0F, 5.0F, new CubeDeformation(0.25F))
                .texOffs(0, 18).addBox(-4.5F, -2.5F, -3.0F, 6.0F, 4.0F, 6.0F, new CubeDeformation(0.1F))
                .texOffs(6, 28).addBox(-3.0F, -5.0F, -0.5F, 1.0F, 3.0F, 1.0F)
                .texOffs(10, 28).addBox(-1.0F, -4.5F, -2.0F, 1.0F, 2.0F, 1.0F),
                PartPose.offset(-6.5F, 3.0F, 0.0F));
        PartDefinition p5 = root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(32, 0).mirror().addBox(-2.0F, -2.0F, -2.5F, 5.0F, 13.0F, 5.0F).mirror(false)
                .texOffs(80, 18).mirror().addBox(-2.0F, 7.0F, -2.5F, 5.0F, 3.0F, 5.0F, new CubeDeformation(0.25F)).mirror(false)
                .texOffs(0, 18).mirror().addBox(-1.5F, -2.5F, -3.0F, 6.0F, 4.0F, 6.0F, new CubeDeformation(0.1F)).mirror(false)
                .texOffs(6, 28).mirror().addBox(2.0F, -5.0F, -0.5F, 1.0F, 3.0F, 1.0F).mirror(false)
                .texOffs(10, 28).mirror().addBox(0.0F, -4.5F, -2.0F, 1.0F, 2.0F, 1.0F).mirror(false),
                PartPose.offset(6.5F, 3.0F, 0.0F));
        PartDefinition p6 = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(84, 0).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 11.0F, 5.0F)
                .texOffs(60, 18).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 4.0F, 5.0F, new CubeDeformation(0.35F)),
                PartPose.offset(-2.6F, 13.0F, 0.0F));
        PartDefinition p7 = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(84, 0).mirror().addBox(-2.5F, 0.0F, -2.5F, 5.0F, 11.0F, 5.0F).mirror(false)
                .texOffs(60, 18).mirror().addBox(-2.5F, 0.0F, -2.5F, 5.0F, 4.0F, 5.0F, new CubeDeformation(0.35F)).mirror(false),
                PartPose.offset(2.6F, 13.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 48);
    }

    /** caballero (textura 128x32, dibujada a 256x64). */
    public static LayerDefinition caballero() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p1 = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.25F))
                .texOffs(0, 26).addBox(-3.5F, -5.5F, -4.8F, 7.0F, 3.0F, 1.0F)
                .texOffs(0, 16).addBox(-0.5F, -9.2F, -4.0F, 1.0F, 2.0F, 8.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition p2 = p1.addOrReplaceChild("horn_r", CubeListBuilder.create()
                .texOffs(118, 16).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F),
                PartPose.offsetAndRotation(-4.0F, -6.5F, -0.5F, -0.35F, 0.0F, -0.6F));
        PartDefinition p3 = p2.addOrReplaceChild("horn_r2", CubeListBuilder.create()
                .texOffs(28, 26).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F, -0.45F, 0.0F, 0.35F));
        PartDefinition p4 = p1.addOrReplaceChild("horn_l", CubeListBuilder.create()
                .texOffs(118, 16).mirror().addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F).mirror(false),
                PartPose.offsetAndRotation(4.0F, -6.5F, -0.5F, -0.35F, 0.0F, 0.6F));
        PartDefinition p5 = p4.addOrReplaceChild("horn_l2", CubeListBuilder.create()
                .texOffs(28, 26).mirror().addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F).mirror(false),
                PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F, -0.45F, 0.0F, -0.35F));
        PartDefinition p6 = root.addOrReplaceChild("hat", CubeListBuilder.create(),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition p7 = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(32, 0).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.3F))
                .texOffs(90, 16).addBox(-3.5F, 1.0F, -2.9F, 7.0F, 6.0F, 1.0F)
                .texOffs(18, 16).addBox(-4.5F, 10.0F, -2.5F, 9.0F, 4.0F, 5.0F, new CubeDeformation(0.1F))
                .texOffs(46, 16).addBox(-2.0F, 10.5F, -3.2F, 4.0F, 8.0F, 1.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition p8 = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(56, 0).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.2F))
                .texOffs(88, 0).addBox(-4.5F, -3.0F, -3.0F, 6.0F, 4.0F, 6.0F, new CubeDeformation(0.15F))
                .texOffs(106, 16).addBox(-3.0F, -4.2F, -2.5F, 1.0F, 2.0F, 5.0F)
                .texOffs(74, 16).addBox(-3.0F, 6.5F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.35F)),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        PartDefinition p9 = root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(56, 0).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.2F)).mirror(false)
                .texOffs(88, 0).mirror().addBox(-1.5F, -3.0F, -3.0F, 6.0F, 4.0F, 6.0F, new CubeDeformation(0.15F)).mirror(false)
                .texOffs(106, 16).mirror().addBox(2.0F, -4.2F, -2.5F, 1.0F, 2.0F, 5.0F).mirror(false)
                .texOffs(74, 16).mirror().addBox(-1.0F, 6.5F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.35F)).mirror(false),
                PartPose.offset(5.0F, 2.0F, 0.0F));
        PartDefinition p10 = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(72, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.2F))
                .texOffs(16, 26).addBox(-2.5F, 3.5F, -2.8F, 5.0F, 3.0F, 1.0F)
                .texOffs(56, 16).addBox(-2.0F, 9.0F, -2.75F, 4.0F, 3.0F, 5.0F, new CubeDeformation(0.25F)),
                PartPose.offset(-1.9F, 12.0F, 0.0F));
        PartDefinition p11 = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(72, 0).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.2F)).mirror(false)
                .texOffs(16, 26).mirror().addBox(-2.5F, 3.5F, -2.8F, 5.0F, 3.0F, 1.0F).mirror(false)
                .texOffs(56, 16).mirror().addBox(-2.0F, 9.0F, -2.75F, 4.0F, 3.0F, 5.0F, new CubeDeformation(0.25F)).mirror(false),
                PartPose.offset(1.9F, 12.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 32);
    }

    /** guardian_juicio (textura 128x48, dibujada a 256x96). */
    public static LayerDefinition guardian() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p1 = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.25F))
                .texOffs(12, 26).addBox(-3.5F, -5.5F, -4.8F, 7.0F, 3.0F, 1.0F)
                .texOffs(0, 16).addBox(-0.5F, -9.2F, -4.0F, 1.0F, 2.0F, 8.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition p2 = p1.addOrReplaceChild("halo", CubeListBuilder.create()
                .texOffs(40, 26).addBox(-5.0F, 0.0F, -5.0F, 10.0F, 1.0F, 1.0F)
                .texOffs(40, 26).addBox(-5.0F, 0.0F, 4.0F, 10.0F, 1.0F, 1.0F)
                .texOffs(46, 16).addBox(-5.0F, 0.0F, -4.0F, 1.0F, 1.0F, 8.0F)
                .texOffs(46, 16).mirror().addBox(4.0F, 0.0F, -4.0F, 1.0F, 1.0F, 8.0F).mirror(false),
                PartPose.offsetAndRotation(0.0F, -12.5F, 0.0F, 0.12F, 0.0F, 0.0F));
        PartDefinition p3 = root.addOrReplaceChild("hat", CubeListBuilder.create(),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition p4 = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(32, 0).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.3F))
                .texOffs(108, 16).addBox(-3.5F, 1.0F, -2.9F, 7.0F, 6.0F, 1.0F)
                .texOffs(18, 16).addBox(-4.5F, 10.0F, -2.5F, 9.0F, 4.0F, 5.0F, new CubeDeformation(0.1F))
                .texOffs(64, 16).addBox(-2.0F, 10.5F, -3.2F, 4.0F, 8.0F, 1.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition p5 = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(56, 0).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.2F))
                .texOffs(88, 0).addBox(-4.5F, -3.0F, -3.0F, 6.0F, 4.0F, 6.0F, new CubeDeformation(0.15F))
                .texOffs(0, 26).addBox(-3.0F, -4.2F, -2.5F, 1.0F, 2.0F, 5.0F)
                .texOffs(92, 16).addBox(-3.0F, 6.5F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.35F)),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        PartDefinition p6 = root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(56, 0).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.2F)).mirror(false)
                .texOffs(88, 0).mirror().addBox(-1.5F, -3.0F, -3.0F, 6.0F, 4.0F, 6.0F, new CubeDeformation(0.15F)).mirror(false)
                .texOffs(0, 26).mirror().addBox(2.0F, -4.2F, -2.5F, 1.0F, 2.0F, 5.0F).mirror(false)
                .texOffs(92, 16).mirror().addBox(-1.0F, 6.5F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.35F)).mirror(false),
                PartPose.offset(5.0F, 2.0F, 0.0F));
        PartDefinition p7 = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(72, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.2F))
                .texOffs(28, 26).addBox(-2.5F, 3.5F, -2.8F, 5.0F, 3.0F, 1.0F)
                .texOffs(74, 16).addBox(-2.0F, 9.0F, -2.75F, 4.0F, 3.0F, 5.0F, new CubeDeformation(0.25F)),
                PartPose.offset(-1.9F, 12.0F, 0.0F));
        PartDefinition p8 = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(72, 0).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.2F)).mirror(false)
                .texOffs(28, 26).mirror().addBox(-2.5F, 3.5F, -2.8F, 5.0F, 3.0F, 1.0F).mirror(false)
                .texOffs(74, 16).mirror().addBox(-2.0F, 9.0F, -2.75F, 4.0F, 3.0F, 5.0F, new CubeDeformation(0.25F)).mirror(false),
                PartPose.offset(1.9F, 12.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 48);
    }

    /** coloso (textura 128x48, dibujada a 256x96). */
    public static LayerDefinition coloso() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p1 = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6.0F, -13.0F, -4.5F, 12.0F, 13.0F, 9.0F)
                .texOffs(28, 22).addBox(-4.5F, -3.0F, -4.0F, 9.0F, 4.0F, 7.0F)
                .texOffs(112, 22).addBox(-5.0F, -15.0F, 0.0F, 4.0F, 3.0F, 4.0F)
                .texOffs(16, 34).addBox(1.5F, -14.5F, 1.5F, 3.0F, 2.0F, 3.0F),
                PartPose.offset(0.0F, 13.0F, 1.0F));
        PartDefinition p2 = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(86, 0).addBox(-3.5F, -6.0F, -4.0F, 7.0F, 7.0F, 7.0F)
                .texOffs(28, 34).addBox(-4.0F, -6.2F, -4.6F, 8.0F, 1.0F, 1.0F)
                .texOffs(0, 34).addBox(-2.5F, 0.0F, -4.2F, 5.0F, 2.0F, 3.0F),
                PartPose.offset(0.0F, 1.0F, -3.5F));
        PartDefinition p3 = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(42, 0).addBox(-2.5F, -2.0F, -2.5F, 5.0F, 16.0F, 5.0F)
                .texOffs(60, 22).addBox(-3.0F, 14.0F, -3.0F, 6.0F, 5.0F, 6.0F)
                .texOffs(0, 22).addBox(-3.5F, -4.0F, -3.5F, 7.0F, 5.0F, 7.0F),
                PartPose.offset(-8.5F, 2.0F, 0.5F));
        PartDefinition p4 = root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(42, 0).mirror().addBox(-2.5F, -2.0F, -2.5F, 5.0F, 16.0F, 5.0F).mirror(false)
                .texOffs(60, 22).mirror().addBox(-3.0F, 14.0F, -3.0F, 6.0F, 5.0F, 6.0F).mirror(false)
                .texOffs(0, 22).mirror().addBox(-3.5F, -4.0F, -3.5F, 7.0F, 5.0F, 7.0F).mirror(false),
                PartPose.offset(8.5F, 2.0F, 0.5F));
        PartDefinition p5 = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(62, 0).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 11.0F, 6.0F)
                .texOffs(84, 22).addBox(-3.5F, 9.0F, -4.0F, 7.0F, 2.0F, 7.0F),
                PartPose.offset(-3.5F, 13.0F, 1.0F));
        PartDefinition p6 = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(62, 0).mirror().addBox(-3.0F, 0.0F, -3.0F, 6.0F, 11.0F, 6.0F).mirror(false)
                .texOffs(84, 22).mirror().addBox(-3.5F, 9.0F, -4.0F, 7.0F, 2.0F, 7.0F).mirror(false),
                PartPose.offset(3.5F, 13.0F, 1.0F));
        return LayerDefinition.create(mesh, 128, 48);
    }

    /** lobo (textura 128x32, dibujada a 256x64). */
    public static LayerDefinition lobo() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p1 = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.5F, -3.0F, -7.0F, 7.0F, 6.0F, 14.0F)
                .texOffs(42, 0).addBox(-4.5F, -4.5F, -9.0F, 9.0F, 9.0F, 6.0F)
                .texOffs(0, 20).addBox(-4.0F, -4.2F, -3.0F, 8.0F, 2.0F, 7.0F, new CubeDeformation(0.2F)),
                PartPose.offset(0.0F, 12.5F, 1.0F));
        PartDefinition p2 = p1.addOrReplaceChild("spine1", CubeListBuilder.create()
                .texOffs(88, 20).addBox(-0.5F, -3.0F, -1.0F, 1.0F, 3.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, -4.5F, -1.5F, -0.5F, 0.0F, 0.0F));
        PartDefinition p3 = p1.addOrReplaceChild("spine2", CubeListBuilder.create()
                .texOffs(88, 20).addBox(-0.5F, -2.5F, -1.0F, 1.0F, 3.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, -3.5F, 2.0F, -0.55F, 0.0F, 0.0F));
        PartDefinition p4 = p1.addOrReplaceChild("spine3", CubeListBuilder.create()
                .texOffs(100, 20).addBox(-0.5F, -2.0F, -1.0F, 1.0F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, -3.2F, 5.0F, -0.6F, 0.0F, 0.0F));
        PartDefinition p5 = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(72, 0).addBox(-3.5F, -3.5F, -4.0F, 7.0F, 6.0F, 5.0F)
                .texOffs(30, 20).addBox(-2.0F, -0.5F, -8.0F, 4.0F, 3.0F, 4.0F)
                .texOffs(74, 20).addBox(-1.5F, 2.5F, -7.5F, 3.0F, 1.0F, 4.0F)
                .texOffs(94, 20).addBox(-3.5F, -6.0F, -1.5F, 2.0F, 3.0F, 1.0F)
                .texOffs(94, 20).mirror().addBox(1.5F, -6.0F, -1.5F, 2.0F, 3.0F, 1.0F).mirror(false)
                .texOffs(106, 20).addBox(-1.5F, 2.5F, -8.0F, 1.0F, 2.0F, 1.0F)
                .texOffs(106, 20).mirror().addBox(0.5F, 2.5F, -8.0F, 1.0F, 2.0F, 1.0F).mirror(false),
                PartPose.offset(0.0F, 11.0F, -8.0F));
        PartDefinition p6 = root.addOrReplaceChild("leg0", CubeListBuilder.create()
                .texOffs(96, 0).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 8.0F, 3.0F)
                .texOffs(46, 20).addBox(-2.0F, 7.0F, -2.5F, 4.0F, 2.0F, 4.0F),
                PartPose.offset(-2.5F, 15.0F, 6.0F));
        PartDefinition p7 = root.addOrReplaceChild("leg1", CubeListBuilder.create()
                .texOffs(96, 0).mirror().addBox(-1.5F, 0.0F, -1.5F, 3.0F, 8.0F, 3.0F).mirror(false)
                .texOffs(46, 20).mirror().addBox(-2.0F, 7.0F, -2.5F, 4.0F, 2.0F, 4.0F).mirror(false),
                PartPose.offset(2.5F, 15.0F, 6.0F));
        PartDefinition p8 = root.addOrReplaceChild("leg2", CubeListBuilder.create()
                .texOffs(96, 0).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 8.0F, 3.0F)
                .texOffs(46, 20).addBox(-2.0F, 7.0F, -2.5F, 4.0F, 2.0F, 4.0F),
                PartPose.offset(-2.5F, 15.0F, -5.0F));
        PartDefinition p9 = root.addOrReplaceChild("leg3", CubeListBuilder.create()
                .texOffs(96, 0).mirror().addBox(-1.5F, 0.0F, -1.5F, 3.0F, 8.0F, 3.0F).mirror(false)
                .texOffs(46, 20).mirror().addBox(-2.0F, 7.0F, -2.5F, 4.0F, 2.0F, 4.0F).mirror(false),
                PartPose.offset(2.5F, 15.0F, -5.0F));
        PartDefinition p10 = root.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(108, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F)
                .texOffs(62, 20).addBox(-1.5F, 6.5F, -1.5F, 3.0F, 3.0F, 3.0F),
                PartPose.offsetAndRotation(0.0F, 10.5F, 7.5F, 0.95F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 32);
    }

    /** oso (textura 128x64, dibujada a 256x128). */
    public static LayerDefinition oso() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p1 = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-7.0F, -6.5F, -12.0F, 14.0F, 13.0F, 24.0F)
                .texOffs(24, 37).addBox(-5.5F, -9.0F, -10.0F, 11.0F, 4.0F, 10.0F),
                PartPose.offset(0.0F, 10.0F, 0.0F));
        PartDefinition p2 = p1.addOrReplaceChild("ice1", CubeListBuilder.create()
                .texOffs(86, 37).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F),
                PartPose.offsetAndRotation(-2.5F, -6.5F, -6.0F, 0.15F, 0.0F, -0.35F));
        PartDefinition p3 = p1.addOrReplaceChild("ice2", CubeListBuilder.create()
                .texOffs(94, 37).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F),
                PartPose.offsetAndRotation(2.0F, -6.5F, -3.0F, -0.1F, 0.0F, 0.4F));
        PartDefinition p4 = p1.addOrReplaceChild("ice3", CubeListBuilder.create()
                .texOffs(86, 37).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F),
                PartPose.offsetAndRotation(-1.5F, -6.5F, 1.0F, -0.25F, 0.0F, -0.2F));
        PartDefinition p5 = p1.addOrReplaceChild("ice4", CubeListBuilder.create()
                .texOffs(114, 37).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F),
                PartPose.offsetAndRotation(2.5F, -6.5F, 4.5F, -0.3F, 0.0F, 0.3F));
        PartDefinition p6 = p1.addOrReplaceChild("ice5", CubeListBuilder.create()
                .texOffs(18, 52).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F),
                PartPose.offsetAndRotation(-2.0F, -6.5F, 8.0F, -0.4F, 0.0F, -0.25F));
        PartDefinition p7 = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(76, 0).addBox(-5.0F, -5.0F, -6.0F, 10.0F, 9.0F, 7.0F)
                .texOffs(66, 37).addBox(-3.0F, 0.0F, -10.0F, 6.0F, 4.0F, 4.0F)
                .texOffs(0, 52).addBox(-5.5F, -7.0F, -3.0F, 3.0F, 3.0F, 2.0F)
                .texOffs(0, 52).mirror().addBox(2.5F, -7.0F, -3.0F, 3.0F, 3.0F, 2.0F).mirror(false),
                PartPose.offset(0.0F, 7.5F, -12.0F));
        PartDefinition p8 = p7.addOrReplaceChild("crest", CubeListBuilder.create()
                .texOffs(10, 52).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, -5.0F, -2.0F, -0.3F, 0.0F, 0.0F));
        PartDefinition p9 = root.addOrReplaceChild("leg0", CubeListBuilder.create()
                .texOffs(0, 37).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 9.0F, 6.0F)
                .texOffs(26, 52).addBox(-3.0F, 7.0F, -4.0F, 6.0F, 2.0F, 1.0F),
                PartPose.offset(-4.5F, 15.0F, 8.0F));
        PartDefinition p10 = root.addOrReplaceChild("leg1", CubeListBuilder.create()
                .texOffs(0, 37).mirror().addBox(-3.0F, 0.0F, -3.0F, 6.0F, 9.0F, 6.0F).mirror(false)
                .texOffs(26, 52).mirror().addBox(-3.0F, 7.0F, -4.0F, 6.0F, 2.0F, 1.0F).mirror(false),
                PartPose.offset(4.5F, 15.0F, 8.0F));
        PartDefinition p11 = root.addOrReplaceChild("leg2", CubeListBuilder.create()
                .texOffs(0, 37).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 9.0F, 6.0F)
                .texOffs(26, 52).addBox(-3.0F, 7.0F, -4.0F, 6.0F, 2.0F, 1.0F),
                PartPose.offset(-4.5F, 15.0F, -8.0F));
        PartDefinition p12 = root.addOrReplaceChild("leg3", CubeListBuilder.create()
                .texOffs(0, 37).mirror().addBox(-3.0F, 0.0F, -3.0F, 6.0F, 9.0F, 6.0F).mirror(false)
                .texOffs(26, 52).mirror().addBox(-3.0F, 7.0F, -4.0F, 6.0F, 2.0F, 1.0F).mirror(false),
                PartPose.offset(4.5F, 15.0F, -8.0F));
        PartDefinition p13 = root.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(102, 37).addBox(-1.5F, -1.0F, -1.0F, 3.0F, 3.0F, 3.0F),
                PartPose.offsetAndRotation(0.0F, 6.0F, 12.0F, 0.4F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    /** hormiga (textura 128x32, dibujada a 256x64). */
    public static LayerDefinition hormiga() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p1 = root.addOrReplaceChild("thorax", CubeListBuilder.create()
                .texOffs(40, 0).addBox(-3.0F, -2.5F, -4.0F, 6.0F, 5.0F, 8.0F)
                .texOffs(118, 0).addBox(-1.5F, -1.0F, 4.0F, 3.0F, 3.0F, 2.0F),
                PartPose.offset(0.0F, 16.0F, 0.0F));
        PartDefinition p2 = p1.addOrReplaceChild("gaster", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.5F, -4.0F, 0.0F, 9.0F, 8.0F, 11.0F),
                PartPose.offsetAndRotation(0.0F, -0.5F, 5.5F, -0.28F, 0.0F, 0.0F));
        PartDefinition p3 = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(76, 0).addBox(-3.5F, -3.5F, -6.0F, 7.0F, 6.0F, 6.0F),
                PartPose.offset(0.0F, 15.0F, -4.0F));
        PartDefinition p4 = p3.addOrReplaceChild("mandible_r", CubeListBuilder.create()
                .texOffs(102, 0).addBox(-1.0F, -1.0F, -4.0F, 2.0F, 2.0F, 4.0F),
                PartPose.offsetAndRotation(-2.0F, 1.5F, -5.5F, 0.0F, -0.4F, 0.0F));
        PartDefinition p5 = p3.addOrReplaceChild("mandible_l", CubeListBuilder.create()
                .texOffs(102, 0).mirror().addBox(-1.0F, -1.0F, -4.0F, 2.0F, 2.0F, 4.0F).mirror(false),
                PartPose.offsetAndRotation(2.0F, 1.5F, -5.5F, 0.0F, 0.4F, 0.0F));
        PartDefinition p6 = p3.addOrReplaceChild("antenna_r", CubeListBuilder.create()
                .texOffs(114, 0).addBox(-0.5F, -5.0F, -0.5F, 1.0F, 5.0F, 1.0F),
                PartPose.offsetAndRotation(-1.5F, -3.5F, -5.0F, 0.5F, 0.0F, -0.35F));
        PartDefinition p7 = p6.addOrReplaceChild("antenna_r2", CubeListBuilder.create()
                .texOffs(0, 19).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, -5.0F, 0.0F, 0.9F, 0.0F, 0.0F));
        PartDefinition p8 = p3.addOrReplaceChild("antenna_l", CubeListBuilder.create()
                .texOffs(114, 0).mirror().addBox(-0.5F, -5.0F, -0.5F, 1.0F, 5.0F, 1.0F).mirror(false),
                PartPose.offsetAndRotation(1.5F, -3.5F, -5.0F, 0.5F, 0.0F, 0.35F));
        PartDefinition p9 = p8.addOrReplaceChild("antenna_l2", CubeListBuilder.create()
                .texOffs(0, 19).mirror().addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F).mirror(false),
                PartPose.offsetAndRotation(0.0F, -5.0F, 0.0F, 0.9F, 0.0F, 0.0F));
        PartDefinition p10 = root.addOrReplaceChild("leg0r", CubeListBuilder.create()
                .texOffs(4, 19).addBox(-7.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(-3.0F, 16.0F, -3.0F, 0.0F, -0.55F, 0.45F));
        PartDefinition p11 = p10.addOrReplaceChild("leg0r_lo", CubeListBuilder.create()
                .texOffs(68, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F),
                PartPose.offsetAndRotation(-7.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.2F));
        PartDefinition p12 = root.addOrReplaceChild("leg0l", CubeListBuilder.create()
                .texOffs(4, 19).mirror().addBox(0.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F).mirror(false),
                PartPose.offsetAndRotation(3.0F, 16.0F, -3.0F, 0.0F, 0.55F, -0.45F));
        PartDefinition p13 = p12.addOrReplaceChild("leg0l_lo", CubeListBuilder.create()
                .texOffs(68, 0).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F).mirror(false),
                PartPose.offsetAndRotation(7.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.2F));
        PartDefinition p14 = root.addOrReplaceChild("leg1r", CubeListBuilder.create()
                .texOffs(4, 19).addBox(-7.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(-3.0F, 16.0F, 0.0F, 0.0F, 0.0F, 0.45F));
        PartDefinition p15 = p14.addOrReplaceChild("leg1r_lo", CubeListBuilder.create()
                .texOffs(68, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F),
                PartPose.offsetAndRotation(-7.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.2F));
        PartDefinition p16 = root.addOrReplaceChild("leg1l", CubeListBuilder.create()
                .texOffs(4, 19).mirror().addBox(0.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F).mirror(false),
                PartPose.offsetAndRotation(3.0F, 16.0F, 0.0F, 0.0F, 0.0F, -0.45F));
        PartDefinition p17 = p16.addOrReplaceChild("leg1l_lo", CubeListBuilder.create()
                .texOffs(68, 0).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F).mirror(false),
                PartPose.offsetAndRotation(7.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.2F));
        PartDefinition p18 = root.addOrReplaceChild("leg2r", CubeListBuilder.create()
                .texOffs(4, 19).addBox(-7.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(-3.0F, 16.0F, 3.0F, 0.0F, 0.55F, 0.45F));
        PartDefinition p19 = p18.addOrReplaceChild("leg2r_lo", CubeListBuilder.create()
                .texOffs(68, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F),
                PartPose.offsetAndRotation(-7.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.2F));
        PartDefinition p20 = root.addOrReplaceChild("leg2l", CubeListBuilder.create()
                .texOffs(4, 19).mirror().addBox(0.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F).mirror(false),
                PartPose.offsetAndRotation(3.0F, 16.0F, 3.0F, 0.0F, -0.55F, -0.45F));
        PartDefinition p21 = p20.addOrReplaceChild("leg2l_lo", CubeListBuilder.create()
                .texOffs(68, 0).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F).mirror(false),
                PartPose.offsetAndRotation(7.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.2F));
        return LayerDefinition.create(mesh, 128, 32);
    }

    /** reina_carmesi (textura 128x32, dibujada a 256x64). */
    public static LayerDefinition reina() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p1 = root.addOrReplaceChild("thorax", CubeListBuilder.create()
                .texOffs(54, 0).addBox(-3.0F, -2.5F, -4.0F, 6.0F, 5.0F, 8.0F)
                .texOffs(22, 25).addBox(-1.5F, -1.0F, 4.0F, 3.0F, 3.0F, 2.0F),
                PartPose.offset(0.0F, 16.0F, 0.0F));
        PartDefinition p2 = p1.addOrReplaceChild("gaster", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6.0F, -5.0F, 0.0F, 12.0F, 10.0F, 15.0F),
                PartPose.offsetAndRotation(0.0F, -0.5F, 5.5F, -0.28F, 0.0F, 0.0F));
        PartDefinition p3 = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(90, 0).addBox(-3.5F, -3.5F, -6.0F, 7.0F, 6.0F, 6.0F)
                .texOffs(4, 25).addBox(-3.0F, -5.5F, -4.0F, 6.0F, 2.0F, 3.0F),
                PartPose.offset(0.0F, 15.0F, -4.0F));
        PartDefinition p4 = p3.addOrReplaceChild("mandible_r", CubeListBuilder.create()
                .texOffs(116, 0).addBox(-1.0F, -1.0F, -4.0F, 2.0F, 2.0F, 4.0F),
                PartPose.offsetAndRotation(-2.0F, 1.5F, -5.5F, 0.0F, -0.4F, 0.0F));
        PartDefinition p5 = p3.addOrReplaceChild("mandible_l", CubeListBuilder.create()
                .texOffs(116, 0).mirror().addBox(-1.0F, -1.0F, -4.0F, 2.0F, 2.0F, 4.0F).mirror(false),
                PartPose.offsetAndRotation(2.0F, 1.5F, -5.5F, 0.0F, 0.4F, 0.0F));
        PartDefinition p6 = p3.addOrReplaceChild("antenna_r", CubeListBuilder.create()
                .texOffs(0, 25).addBox(-0.5F, -5.0F, -0.5F, 1.0F, 5.0F, 1.0F),
                PartPose.offsetAndRotation(-1.5F, -3.5F, -5.0F, 0.5F, 0.0F, -0.35F));
        PartDefinition p7 = p6.addOrReplaceChild("antenna_r2", CubeListBuilder.create()
                .texOffs(32, 25).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, -5.0F, 0.0F, 0.9F, 0.0F, 0.0F));
        PartDefinition p8 = p3.addOrReplaceChild("antenna_l", CubeListBuilder.create()
                .texOffs(0, 25).mirror().addBox(-0.5F, -5.0F, -0.5F, 1.0F, 5.0F, 1.0F).mirror(false),
                PartPose.offsetAndRotation(1.5F, -3.5F, -5.0F, 0.5F, 0.0F, 0.35F));
        PartDefinition p9 = p8.addOrReplaceChild("antenna_l2", CubeListBuilder.create()
                .texOffs(32, 25).mirror().addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F).mirror(false),
                PartPose.offsetAndRotation(0.0F, -5.0F, 0.0F, 0.9F, 0.0F, 0.0F));
        PartDefinition p10 = root.addOrReplaceChild("leg0r", CubeListBuilder.create()
                .texOffs(36, 25).addBox(-7.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(-3.0F, 16.0F, -3.0F, 0.0F, -0.55F, 0.45F));
        PartDefinition p11 = p10.addOrReplaceChild("leg0r_lo", CubeListBuilder.create()
                .texOffs(82, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F),
                PartPose.offsetAndRotation(-7.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.2F));
        PartDefinition p12 = root.addOrReplaceChild("leg0l", CubeListBuilder.create()
                .texOffs(36, 25).mirror().addBox(0.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F).mirror(false),
                PartPose.offsetAndRotation(3.0F, 16.0F, -3.0F, 0.0F, 0.55F, -0.45F));
        PartDefinition p13 = p12.addOrReplaceChild("leg0l_lo", CubeListBuilder.create()
                .texOffs(82, 0).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F).mirror(false),
                PartPose.offsetAndRotation(7.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.2F));
        PartDefinition p14 = root.addOrReplaceChild("leg1r", CubeListBuilder.create()
                .texOffs(36, 25).addBox(-7.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(-3.0F, 16.0F, 0.0F, 0.0F, 0.0F, 0.45F));
        PartDefinition p15 = p14.addOrReplaceChild("leg1r_lo", CubeListBuilder.create()
                .texOffs(82, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F),
                PartPose.offsetAndRotation(-7.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.2F));
        PartDefinition p16 = root.addOrReplaceChild("leg1l", CubeListBuilder.create()
                .texOffs(36, 25).mirror().addBox(0.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F).mirror(false),
                PartPose.offsetAndRotation(3.0F, 16.0F, 0.0F, 0.0F, 0.0F, -0.45F));
        PartDefinition p17 = p16.addOrReplaceChild("leg1l_lo", CubeListBuilder.create()
                .texOffs(82, 0).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F).mirror(false),
                PartPose.offsetAndRotation(7.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.2F));
        PartDefinition p18 = root.addOrReplaceChild("leg2r", CubeListBuilder.create()
                .texOffs(36, 25).addBox(-7.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(-3.0F, 16.0F, 3.0F, 0.0F, 0.55F, 0.45F));
        PartDefinition p19 = p18.addOrReplaceChild("leg2r_lo", CubeListBuilder.create()
                .texOffs(82, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F),
                PartPose.offsetAndRotation(-7.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.2F));
        PartDefinition p20 = root.addOrReplaceChild("leg2l", CubeListBuilder.create()
                .texOffs(36, 25).mirror().addBox(0.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F).mirror(false),
                PartPose.offsetAndRotation(3.0F, 16.0F, 3.0F, 0.0F, -0.55F, -0.45F));
        PartDefinition p21 = p20.addOrReplaceChild("leg2l_lo", CubeListBuilder.create()
                .texOffs(82, 0).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F).mirror(false),
                PartPose.offsetAndRotation(7.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.2F));
        return LayerDefinition.create(mesh, 128, 32);
    }
}
