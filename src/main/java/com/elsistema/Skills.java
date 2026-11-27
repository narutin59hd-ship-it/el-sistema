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
        return e != p && e.isAlive() && e instanceof Enemy && !Shadows.allied(e, p);
    }

    private static void sound(ServerLevel lvl, double x, double y, double z, SoundEvent s, float volume, float pitch) {
        lvl.playSound((Player) null, x, y, z, s, SoundSource.PLAYERS, volume, pitch);
    }

    /** Espadachín: Tajo giratorio (Señor de la Guerra: Tormenta de acero). */
    public static boolean slash(ServerPlayer p, Data d) {
        ServerLevel lvl = (ServerLevel) p.level();
        boolean mut = d.isMutated();
        double r = (mut ? 6.5 : 4.5) + d.fx(Trees.FX_SKILL_RADIUS);
        float dmg = (float) (p.getAttributeValue(Attributes.ATTACK_DAMAGE) * (mut ? 2.2 : 1.5) * SystemLogic.skillMult(d));
        List<LivingEntity> list = lvl.getEntitiesOfClass(LivingEntity.class,
                p.getBoundingBox().inflate(r, 1.5, r), e -> isTarget(e, p));
        for (LivingEntity e : list) {
            if (e.distanceToSqr(p) > r * r + 2.0) continue;
            e.hurt(p.damageSources().playerAttack(p), dmg);
            e.knockback(mut ? 1.0 : 0.7, p.getX() - e.getX(), p.getZ() - e.getZ());
            if (mut) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), p);
        }
        double y = p.getY() + 1.0;
        if (mut) {
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 0, false, false, true));
            for (int i = 0; i < 28; i++) {
                double ang = i * Math.PI * 2.0 / 28.0;
                Fx.send(lvl, ParticleTypes.SWEEP_ATTACK, p.getX() + Math.cos(ang) * 5.2, y + 0.2, p.getZ() + Math.sin(ang) * 5.2,
                        1, 0.0, 0.0, 0.0, 0.0);
                Fx.send(lvl, ParticleTypes.SOUL_FIRE_FLAME, p.getX() + Math.cos(ang) * 6.3, y, p.getZ() + Math.sin(ang) * 6.3,
                        2, 0.1, 0.2, 0.1, 0.01);
            }
            Fx.send(lvl, ParticleTypes.CRIT, p.getX(), y, p.getZ(), 60, 3.0, 0.5, 3.0, 0.4);
            sound(lvl, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, 0.5F, 1.4F);
        }
        for (int i = 0; i < 16; i++) {
            double ang = i * Math.PI * 2.0 / 16.0;
            Fx.send(lvl, ParticleTypes.SWEEP_ATTACK, p.getX() + Math.cos(ang) * 2.2, y, p.getZ() + Math.sin(ang) * 2.2,
                    1, 0.0, 0.0, 0.0, 0.0);
            Fx.send(lvl, ParticleTypes.ELECTRIC_SPARK, p.getX() + Math.cos(ang) * 3.8, y, p.getZ() + Math.sin(ang) * 3.8,
                    2, 0.1, 0.1, 0.1, 0.02);
        }
        Fx.send(lvl, ParticleTypes.ENCHANTED_HIT, p.getX(), y, p.getZ(), 40, 2.0, 0.4, 2.0, 0.2);
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
        Fx.send(lvl, ParticleTypes.CRIT, p.getX(), p.getEyeY(), p.getZ(), 25, 0.4, 0.2, 0.4, 0.3);
        sound(lvl, p.getX(), p.getY(), p.getZ(), SoundEvents.ARROW_SHOOT, 1.2F, 0.8F);
        sound(lvl, p.getX(), p.getY(), p.getZ(), SoundEvents.ARROW_SHOOT, 1.0F, 1.3F);
        if (d.isMutated()) rain(p, lvl, mult, fire);
        return true;
    }

    /** Cazador Celestial: una lluvia de flechas cae del cielo donde miras. */
    private static void rain(ServerPlayer p, ServerLevel lvl, double mult, boolean fire) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(32.0));
        BlockHitResult bh = lvl.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 at = bh.getType() != HitResult.Type.MISS ? bh.getLocation() : eye.add(p.getLookAngle().scale(20.0));
        double top = Math.min(at.y + 12.0, lvl.getMaxBuildHeight() - 2.0);
        for (int k = 0; k < 18; k++) {
            Arrow a = EntityType.ARROW.create(lvl);
            if (a == null) continue;
            double ox = (p.getRandom().nextDouble() - 0.5) * 7.0;
            double oz = (p.getRandom().nextDouble() - 0.5) * 7.0;
            a.setOwner(p);
            a.setPos(at.x + ox, top + p.getRandom().nextDouble() * 3.0, at.z + oz);
            a.shoot(0.0, -1.0, 0.0, 1.9F, 4.0F);
            a.setBaseDamage(a.getBaseDamage() * mult);
            a.setCritArrow(true);
            a.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            a.addTag(SystemLogic.ARROW_TAG);
            if (fire) a.setRemainingFireTicks(2000);
            lvl.addFreshEntity(a);
        }
        Fx.send(lvl, ParticleTypes.END_ROD, at.x, top, at.z, 50, 3.0, 0.5, 3.0, 0.02);
        Fx.send(lvl, ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.3, at.z, 30, 3.0, 0.2, 3.0, 0.05);
        sound(lvl, at.x, at.y, at.z, SoundEvents.ARROW_SHOOT, 1.6F, 0.5F);
        sound(lvl, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 1.6F);
    }

    /** Mago: Lanza arcana (Hechicero del Abismo: Lanza del abismo, más larga y estalla al final). */
    public static boolean lance(ServerPlayer p, Data d) {
        ServerLevel lvl = (ServerLevel) p.level();
        boolean mut = d.isMutated();
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(mut ? 32.0 : 24.0));
        BlockHitResult bh = lvl.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (bh.getType() != HitResult.Type.MISS) {
            end = bh.getLocation();
        }
        final Vec3 from = eye;
        final Vec3 to = end;
        float dmg = (float) ((6.0 + 0.35 * d.bonus(3)) * SystemLogic.skillMult(d) * (mut ? 1.6 : 1.0));
        List<LivingEntity> hits = lvl.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.0),
                e -> isTarget(e, p) && e.getBoundingBox().inflate(0.4).clip(from, to).isPresent());
        for (LivingEntity e : hits) {
            e.hurt(p.damageSources().indirectMagic(p, p), dmg);
            Fx.send(lvl, ParticleTypes.SOUL_FIRE_FLAME, e.getX(), e.getY() + e.getBbHeight() / 2.0, e.getZ(), 15, 0.3, 0.3, 0.3, 0.05);
        }
        double len = from.distanceTo(to);
        Vec3 dir = to.subtract(from).normalize();
        int step = 0;
        for (double t = 0.8; t < len; t += 0.4) {
            Vec3 q = from.add(dir.scale(t));
            Fx.send(lvl, mut ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.END_ROD, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0.0);
            if (step++ % 3 == 0) {
                Fx.send(lvl, mut ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.ELECTRIC_SPARK, q.x, q.y, q.z, 2, 0.1, 0.1, 0.1, 0.05);
            }
        }
        Fx.send(lvl, ParticleTypes.SOUL_FIRE_FLAME, to.x, to.y, to.z, 25, 0.3, 0.3, 0.3, 0.05);
        sound(lvl, p.getX(), p.getY(), p.getZ(), SoundEvents.EVOKER_CAST_SPELL, 1.0F, 1.6F);
        sound(lvl, to.x, to.y, to.z, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 0.6F);
        if (mut) {
            // Estallido del abismo al final del rayo
            List<LivingEntity> blast = lvl.getEntitiesOfClass(LivingEntity.class, new AABB(to, to).inflate(4.0),
                    e -> isTarget(e, p) && e.distanceToSqr(to) < 16.0);
            for (LivingEntity e : blast) {
                e.hurt(p.damageSources().indirectMagic(p, p), dmg * 0.6F);
                e.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0), p);
            }
            Fx.send(lvl, ParticleTypes.EXPLOSION, to.x, to.y, to.z, 4, 1.2, 0.8, 1.2, 0.0);
            Fx.send(lvl, ParticleTypes.REVERSE_PORTAL, to.x, to.y, to.z, 80, 1.5, 1.2, 1.5, 0.2);
            Fx.send(lvl, ParticleTypes.SOUL_FIRE_FLAME, to.x, to.y, to.z, 50, 2.0, 1.0, 2.0, 0.05);
            sound(lvl, to.x, to.y, to.z, SoundEvents.WITHER_SHOOT, 1.0F, 0.6F);
        }
        return true;
    }

    /** Báculo arcano: rayo corto que atraviesa monstruos (más daño con INTELIGENCIA). */
    public static void bolt(ServerPlayer p, Data d) {
        bolt(p, d, 1.0F, false);
    }

    /** Rayo arcano. El Báculo del Monarca lo lanza más lejos, con llamas de alma y el triple de daño. */
    public static void bolt(ServerPlayer p, Data d, float mult, boolean sovereign) {
        ServerLevel lvl = (ServerLevel) p.level();
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(sovereign ? 28.0 : 18.0));
        BlockHitResult bh = lvl.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (bh.getType() != HitResult.Type.MISS) {
            end = bh.getLocation();
        }
        final Vec3 from = eye;
        final Vec3 to = end;
        float dmg = (float) ((5.0 + 0.25 * d.bonus(3) + 0.05 * d.level) * mult);
        List<LivingEntity> hits = lvl.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.0),
                e -> isTarget(e, p) && e.getBoundingBox().inflate(0.3).clip(from, to).isPresent());
        for (LivingEntity e : hits) {
            e.hurt(p.damageSources().indirectMagic(p, p), dmg);
            Fx.send(lvl, sovereign ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.WITCH,
                    e.getX(), e.getY() + e.getBbHeight() / 2.0, e.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
            if (sovereign) e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1), p);
        }
        double len = from.distanceTo(to);
        Vec3 dir = to.subtract(from).normalize();
        for (double t = 0.8; t < len; t += 0.5) {
            Vec3 q = from.add(dir.scale(t));
            Fx.send(lvl, sovereign ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.WITCH, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
        sound(lvl, p.getX(), p.getY(), p.getZ(), SoundEvents.EVOKER_CAST_SPELL, 0.7F, 1.9F);
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
        Fx.send(lvl, ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.4, 0.6, 0.4, 0.02);
        Fx.send(lvl, ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.4, 0.6, 0.4, 0.05);
        sound(lvl, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, 0.8F, 1.5F);
        if (d.isMutated()) {
            // Nigromante: Dominio de las sombras
            int out = Shadows.summonAll(p);
            if (out > 0) SystemLogic.notify(p, Net.N_SHADOWS_OUT, out, 0);
            Shadows.buffOut(p, time);
            Fx.send(lvl, ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 0.3, p.getZ(), 40, 2.0, 0.2, 2.0, 0.03);
            sound(lvl, p.getX(), p.getY(), p.getZ(), SoundEvents.WITHER_SPAWN, 0.3F, 1.8F);
        }
        return true;
    }

    /** Tanque: Fortaleza (Bastión Inmortal: Muralla eterna). */
    public static boolean fortress(ServerPlayer p, Data d) {
        ServerLevel lvl = (ServerLevel) p.level();
        boolean mut = d.isMutated();
        int time = 160 + (int) Math.round(d.fx(Trees.FX_SKILL_TIME) * 20);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, time, mut ? 2 : 1, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, time, mut ? 3 : 1, false, false, true));
        if (mut) {
            p.heal(p.getMaxHealth() * 0.3F);
            Fx.send(lvl, ParticleTypes.HEART, p.getX(), p.getY() + 1.6, p.getZ(), 8, 0.5, 0.3, 0.5, 0.0);
            Fx.send(lvl, ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.6, 1.0, 0.6, 0.3);
        }
        List<LivingEntity> list = lvl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(mut ? 10.0 : 6.0),
                e -> isTarget(e, p));
        for (LivingEntity e : list) {
            e.knockback(mut ? 1.6 : 1.2, p.getX() - e.getX(), p.getZ() - e.getZ());
            if (e instanceof Mob m) {
                m.setTarget(p);
            }
        }
        for (int i = 0; i < 32; i++) {
            double ang = i * Math.PI * 2.0 / 32.0;
            Fx.send(lvl, ParticleTypes.ENCHANTED_HIT, p.getX() + Math.cos(ang) * 3.0, p.getY() + 0.3, p.getZ() + Math.sin(ang) * 3.0,
                    2, 0.05, 0.3, 0.05, 0.0);
        }
        Fx.send(lvl, ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 30, 1.5, 0.1, 1.5, 0.05);
        sound(lvl, p.getX(), p.getY(), p.getZ(), SoundEvents.RAVAGER_ROAR, 0.7F, 1.3F);
        sound(lvl, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, 0.5F, 0.6F);
        return true;
    }
}
