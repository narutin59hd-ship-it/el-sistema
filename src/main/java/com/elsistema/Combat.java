package com.elsistema;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Efectos de combate de los árboles: esquivar, devolver daño, robo de vida, fuego, veneno y última resistencia. */
public final class Combat {
    private Combat() {}

    private static boolean busy = false;

    /** Devuelve false si el golpe se anula (esquivado). */
    public static boolean allowDamage(LivingEntity victim, DamageSource src, float amount) {
        if (busy) return true;
        Entity attacker = src.getEntity();
        // El Jugador y sus sombras nunca se hacen daño entre ellos
        if (attacker != null && attacker != victim && Shadows.allied(attacker, victim)) return false;

        if (victim instanceof ServerPlayer p) {
            Data d = Store.get(p);
            if (d.awakened == 1) {
                if (attacker != null && attacker != p) {
                    double dodge = d.fx(Trees.FX_DODGE);
                    if (dodge > 0 && p.getRandom().nextDouble() * 100.0 < dodge) {
                        SystemLogic.notify(p, Net.N_DODGE, 0, 0);
                        p.level().playSound((Player) null, p.getX(), p.getY(), p.getZ(),
                                SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 1.0F, 1.6F);
                        return false;
                    }
                }
                if (d.lastStand <= 0 && d.fx(Trees.FX_LAST_STAND) > 0 && p.getHealth() - amount < p.getMaxHealth() * 0.3F) {
                    d.lastStand = 1200;
                    p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 1, false, true, true));
                    p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 120, 0, false, true, true));
                    SystemLogic.notify(p, Net.N_LAST_STAND, 0, 0);
                }
                double thorns = d.fx(Trees.FX_THORNS);
                if (thorns > 0 && attacker instanceof LivingEntity le && le != p && src.getDirectEntity() == attacker) {
                    busy = true;
                    try {
                        le.hurt(p.damageSources().thorns(p), (float) (amount * thorns / 100.0));
                    } finally {
                        busy = false;
                    }
                }
            }
        }

        if (attacker instanceof ServerPlayer ap && attacker != victim) {
            Data d = Store.get(ap);
            if (src.getDirectEntity() == ap) {
                ItemStack held = ap.getMainHandItem();
                if (held.is(ModItems.GUADANA)) {
                    Shadows.focus(ap, victim);
                } else if (held.is(ModItems.HOJA_JUICIO)) {
                    victim.setRemainingFireTicks(Math.max(victim.getRemainingFireTicks(), 100));
                } else if (held.is(ModItems.COLMILLO)) {
                    victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), ap);
                    ap.heal(1.0F);
                } else if (held.is(ModItems.AGUIJON)) {
                    victim.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1), ap);
                } else if (held.is(ModItems.SOBERANO[Defs.ESPADACHIN])) {
                    sweep(ap, victim, amount * 0.5F, 3.5, false);
                } else if (held.is(ModItems.SOBERANO[Defs.ASESINO])) {
                    victim.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1), ap);
                    victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), ap);
                    ap.heal(Math.max(1.0F, amount * 0.1F));
                } else if (held.is(ModItems.SOBERANO[Defs.TANQUE])) {
                    sweep(ap, victim, amount * 0.4F, 4.0, true);
                    ap.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1, false, true, true));
                }
            }
            if (d.awakened == 1) {
                double steal = d.fx(Trees.FX_LIFESTEAL);
                if (steal > 0) ap.heal((float) (amount * steal / 100.0));
                if (src.getDirectEntity() == ap) {
                    double fire = d.fx(Trees.FX_FIRE_HIT);
                    if (fire > 0) {
                        victim.setRemainingFireTicks(Math.max(victim.getRemainingFireTicks(), (int) (fire * 20)));
                    }
                    double poison = d.fx(Trees.FX_POISON_HIT);
                    if (poison > 0) {
                        victim.addEffect(new MobEffectInstance(MobEffects.POISON, (int) (poison * 20), 0));
                    }
                }
            }
        }
        return true;
    }

    /** Armas del Monarca: golpe en área a los enemigos de alrededor de la víctima (y, si push, los empuja). */
    private static void sweep(ServerPlayer p, LivingEntity victim, float dmg, double radius, boolean push) {
        if (!(p.level() instanceof ServerLevel lvl) || dmg <= 0F) return;
        List<LivingEntity> near = lvl.getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(radius),
                e -> e != victim && e != p && e.isAlive() && !Shadows.allied(p, e) && !(e instanceof ArmorStand)
                        && (e instanceof Enemy || e instanceof Mob m && m.getTarget() == p));
        busy = true;
        try {
            for (LivingEntity e : near) {
                e.hurt(p.damageSources().playerAttack(p), dmg);
                if (push) {
                    Vec3 dir = e.position().subtract(p.position());
                    double len = Math.max(0.1, Math.sqrt(dir.x * dir.x + dir.z * dir.z));
                    e.push(dir.x / len * 0.9, 0.35, dir.z / len * 0.9);
                    e.hurtMarked = true;
                }
            }
        } finally {
            busy = false;
        }
        lvl.sendParticles(ParticleTypes.SWEEP_ATTACK, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                6, radius * 0.4, 0.2, radius * 0.4, 0.0);
        if (push) {
            lvl.sendParticles(ParticleTypes.EXPLOSION, victim.getX(), victim.getY() + 0.3, victim.getZ(), 2, 0.5, 0.1, 0.5, 0.0);
        }
    }
}
