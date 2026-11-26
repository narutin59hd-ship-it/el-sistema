package com.elsistema;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/** Monstruos propios de las mazmorras (goblins, lobos, osos de hielo, orcos, hormigas, caballeros y colosos). */
public class DungeonMonster extends Monster {
    public enum Kind { GOBLIN, LOBO, OSO, ORCO, HORMIGA, CABALLERO, COLOSO }

    public DungeonMonster(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public Kind kind() {
        return ModEntities.kindOf(this.getType());
    }

    /** Atributos base (antes del rango y el nivel). */
    public static AttributeSupplier.Builder attributes(Kind k) {
        double hp;
        double dmg;
        double speed;
        double armor;
        double kb;
        switch (k) {
            case GOBLIN -> { hp = 14; dmg = 3; speed = 0.30; armor = 2; kb = 0.0; }
            case LOBO -> { hp = 22; dmg = 5; speed = 0.36; armor = 3; kb = 0.0; }
            case OSO -> { hp = 50; dmg = 9; speed = 0.27; armor = 6; kb = 0.4; }
            case ORCO -> { hp = 40; dmg = 8; speed = 0.26; armor = 6; kb = 0.3; }
            case HORMIGA -> { hp = 34; dmg = 7; speed = 0.33; armor = 9; kb = 0.2; }
            case CABALLERO -> { hp = 60; dmg = 10; speed = 0.27; armor = 14; kb = 0.5; }
            default -> { hp = 150; dmg = 16; speed = 0.21; armor = 18; kb = 1.0; }
        }
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, hp)
                .add(Attributes.ATTACK_DAMAGE, dmg)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.ARMOR, armor)
                .add(Attributes.KNOCKBACK_RESISTANCE, kb)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        Kind k = ModEntities.kindOf(this.getType());
        this.goalSelector.addGoal(0, new FloatGoal(this));
        if (k == Kind.LOBO || k == Kind.HORMIGA || k == Kind.GOBLIN) {
            this.goalSelector.addGoal(1, new LeapAtTargetGoal(this, k == Kind.GOBLIN ? 0.3F : 0.4F));
        }
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, k == Kind.COLOSO ? 1.0 : 1.2, false));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        SpawnGroupData spawnGroupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        ItemStack weapon = switch (this.kind()) {
            case GOBLIN -> new ItemStack(ModItems.DAGA);
            case ORCO -> new ItemStack(ModItems.HACHA);
            case CABALLERO -> new ItemStack(ModItems.ESPADA);
            default -> ItemStack.EMPTY;
        };
        if (!weapon.isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        }
        for (EquipmentSlot s : EquipmentSlot.values()) {
            this.setDropChance(s, 0.0F);
        }
        return data;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity le) {
            switch (this.kind()) {
                case OSO -> le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                case HORMIGA -> le.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
                case LOBO -> le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
                case CABALLERO -> le.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0));
                case COLOSO -> le.knockback(1.6, this.getX() - le.getX(), this.getZ() - le.getZ());
                default -> {
                }
            }
        }
        return hit;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return switch (this.kind()) {
            case GOBLIN -> SoundEvents.PIGLIN_AMBIENT;
            case LOBO -> SoundEvents.WOLF_GROWL;
            case OSO -> SoundEvents.POLAR_BEAR_AMBIENT;
            case ORCO -> SoundEvents.RAVAGER_AMBIENT;
            case HORMIGA -> SoundEvents.SPIDER_AMBIENT;
            case CABALLERO -> SoundEvents.WITHER_SKELETON_AMBIENT;
            default -> SoundEvents.IRON_GOLEM_STEP;
        };
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return switch (this.kind()) {
            case GOBLIN -> SoundEvents.PIGLIN_HURT;
            case LOBO -> SoundEvents.WOLF_HURT;
            case OSO -> SoundEvents.POLAR_BEAR_HURT;
            case ORCO -> SoundEvents.VINDICATOR_HURT;
            case HORMIGA -> SoundEvents.SPIDER_HURT;
            case CABALLERO -> SoundEvents.WITHER_SKELETON_HURT;
            default -> SoundEvents.IRON_GOLEM_HURT;
        };
    }

    @Override
    protected SoundEvent getDeathSound() {
        return switch (this.kind()) {
            case GOBLIN -> SoundEvents.PIGLIN_DEATH;
            case LOBO -> SoundEvents.WOLF_DEATH;
            case OSO -> SoundEvents.POLAR_BEAR_DEATH;
            case ORCO -> SoundEvents.VINDICATOR_DEATH;
            case HORMIGA -> SoundEvents.SPIDER_DEATH;
            case CABALLERO -> SoundEvents.WITHER_SKELETON_DEATH;
            default -> SoundEvents.IRON_GOLEM_DEATH;
        };
    }

    @Override
    protected float getSoundVolume() {
        return this.kind() == Kind.COLOSO ? 1.4F : 1.0F;
    }

    @Override
    public float getVoicePitch() {
        return switch (this.kind()) {
            case GOBLIN -> 1.5F;
            case ORCO, OSO -> 0.75F;
            case COLOSO -> 0.5F;
            default -> 1.0F;
        };
    }
}
