package com.elsistema;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Habilidades activas de cada clase (tecla R). Solo dañan a monstruos, nunca a aldeanos, mascotas ni jugadores. */
public final class Skills {
    private Skills() {}

    private static boolean isTarget(LivingEntity e, ServerPlayer p) {
        return e != p && e.isAlive() && e instanceof Enemy;
    }

    private static void sound(ServerLevel lvl, double x, double y, double z, SoundEvent s, float volume, float pitch) {
        lvl.playSound((Player) null, x, y, z, s, SoundSource.PLAYERS, volume, pitch);
    }

    /** Espadachín: Tajo giratorio. */
    public static boolean slash(ServerPlayer p, Data d) {
        ServerLevel lvl = (ServerLevel) p.level();
        double r = 4.5 + d.fx(Trees.FX_SKILL_RADIUS);
        float dmg = (float) (p.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.5 * SystemLogic.skillMult(d));
        List<LivingEntity> list = lvl.getEntitiesOfClass(LivingEntity.class,
                p.getBoundingBox().inflate(r, 1.5, r), e -> isTarget(e, p));
        for (LivingEntity e : list) {
            if (e.distanceToSqr(p) > r * r + 2.0) continue;
            e.hurt(p.damageSources().playerAttack(p), dmg);
            e.knockback(0.7, p.getX() - e.getX(), p.getZ() - e.getZ());
        }
        double y = p.getY() + 1.0;
        for (int i = 0; i < 16; i++) {
            double ang = i * Math.PI * 2.0 / 16.0;
            lvl.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX() + Math.cos(ang) * 2.2, y, p.getZ() + Math.sin(ang) * 2.2,
                    1, 0.0, 0.0, 0.0, 0.0);
            lvl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + Math.cos(ang) * 3.8, y, p.getZ() + Math.sin(ang) * 3.8,
                    2, 0.1, 0.1, 0.1, 0.02);
        }
        lvl.sendParticles(ParticleTypes.ENCHANTED_HIT, p.getX(), y, p.getZ(), 40, 2.0, 0.4, 2.0, 0.2);
        sound(lvl, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.7F);
        sound(lvl, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.6F);
        return true;
    }

    /** Arquero: Disparo múltiple (7 flechas en abanico que no se pueden recoger). */
    public static boolean volley(ServerPlayer p, Data d) {
        ServerLevel lvl = (ServerLevel) p.level();
        double mult = SystemLogic.arrowBonus(d) * SystemLogic.skillMult(d);
        int count = 7 + (int) Math.round(d.fx(Trees.FX_EXTRA_ARROWS));
        boolean fire = d.fx(Trees.FX_FIRE_ARROWS) > 0;
        float spread = count > 7 ? 36.0F / (count - 1) : 6.0F;
        for (int k = 0; k < count; k++) {
            float i = k - (count - 1) / 2.0F;
            Arrow a = EntityType.ARROW.create(lvl);
            if (a == null) continue;
            a.setOwner(p);
            a.setPos(p.getX(), p.getEyeY() - 0.1, p.getZ());
            a.shootFromRotation(p, p.getXRot(), p.getYRot() + i * spread, 0.0F, 3.0F, 1.0F);
            a.setBaseDamage(a.getBaseDamage() * mult);
            a.setCritArrow(true);
            a.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            a.addTag(SystemLogic.ARROW_TAG);
            if (fire) a.setRemainingFireTicks(2000);
            lvl.addFreshEntity(a);
        }
        lvl.sendParticles(ParticleTypes.CRIT, p.getX(), p.getEyeY(), p.getZ(), 25, 0.4, 0.2, 0.4, 0.3);
        sound(lvl, p.getX(), p.getY(), p.getZ(), SoundEvents.ARROW_SHOOT, 1.2F, 0.8F);
        sound(lvl, p.getX(), p.getY(), p.getZ(), SoundEvents.ARROW_SHOOT, 1.0F, 1.3F);
        return true;
    }

    /** Mago: Lanza arcana (rayo que atraviesa a todos los monstruos en línea). */
    public static boolean lance(ServerPlayer p, Data d) {
        ServerLevel lvl = (ServerLevel) p.level();
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(24.0));
        BlockHitResult bh = lvl.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (bh.getType() != HitResult.Type.MISS) {
            end = bh.getLocation();
        }
        final Vec3 from = eye;
        final Vec3 to = end;
        float dmg = (float) ((6.0 + 0.35 * d.bonus(3)) * SystemLogic.skillMult(d));
        List<LivingEntity> hits = lvl.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.0),
                e -> isTarget(e, p) && e.getBoundingBox().inflate(0.4).clip(from, to).isPresent());
        for (LivingEntity e : hits) {
            e.hurt(p.damageSources().indirectMagic(p, p), dmg);
            lvl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, e.getX(), e.getY() + e.getBbHeight() / 2.0, e.getZ(), 15, 0.3, 0.3, 0.3, 0.05);
        }
        double len = from.distanceTo(to);
        Vec3 dir = to.subtract(from).normalize();
        int step = 0;
        for (double t = 0.8; t < len; t += 0.4) {
            Vec3 q = from.add(dir.scale(t));
            lvl.sendParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0.0);
            if (step++ % 3 == 0) {
                lvl.sendParticles(ParticleTypes.ELECTRIC_SPARK, q.x, q.y, q.z, 2, 0.1, 0.1, 0.1, 0.05);
            }
        }
        lvl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, to.x, to.y, to.z, 25, 0.3, 0.3, 0.3, 0.05);
        sound(lvl, p.getX(), p.getY(), p.getZ(), SoundEvents.EVOKER_CAST_SPELL, 1.0F, 1.6F);
        sound(lvl, to.x, to.y, to.z, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 0.6F);
        return true;
    }

    /** Asesino: Sigilo. */
    public static boolean stealth(ServerPlayer p, Data d) {
        ServerLevel lvl = (ServerLevel) p.level();
        int time = 120 + (int) Math.round(d.fx(Trees.FX_SKILL_TIME) * 20);
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, time, 0, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, time, 1, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, time, 0, false, false, true));
        List<Mob> hunters = lvl.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24.0), m -> m.getTarget() == p);
        for (Mob m : hunters) {
            m.setTarget(null);
        }
        lvl.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.4, 0.6, 0.4, 0.02);
        lvl.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.4, 0.6, 0.4, 0.05);
        sound(lvl, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, 0.8F, 1.5F);
        return true;
    }

    /** Tanque: Fortaleza. */
    public static boolean fortress(ServerPlayer p, Data d) {
        ServerLevel lvl = (ServerLevel) p.level();
        int time = 160 + (int) Math.round(d.fx(Trees.FX_SKILL_TIME) * 20);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, time, 1, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, time, 1, false, false, true));
        List<LivingEntity> list = lvl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(6.0), e -> isTarget(e, p));
        for (LivingEntity e : list) {
            e.knockback(1.2, p.getX() - e.getX(), p.getZ() - e.getZ());
            if (e instanceof Mob m) {
                m.setTarget(p);
            }
        }
        for (int i = 0; i < 32; i++) {
            double ang = i * Math.PI * 2.0 / 32.0;
            lvl.sendParticles(ParticleTypes.ENCHANTED_HIT, p.getX() + Math.cos(ang) * 3.0, p.getY() + 0.3, p.getZ() + Math.sin(ang) * 3.0,
                    2, 0.05, 0.3, 0.05, 0.0);
        }
        lvl.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 30, 1.5, 0.1, 1.5, 0.05);
        sound(lvl, p.getX(), p.getY(), p.getZ(), SoundEvents.RAVAGER_ROAR, 0.7F, 1.3F);
        sound(lvl, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, 0.5F, 0.6F);
        return true;
    }
}
