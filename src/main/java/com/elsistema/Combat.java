package com.elsistema;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

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
            if (src.getDirectEntity() == ap && ap.getMainHandItem().is(ModItems.GUADANA)) {
                Shadows.focus(ap, victim);
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
}
