package com.elsistema;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Rango de cazador y prestigio (pestaña Rango de la ventana del Sistema).
 * <ul>
 *   <li>Examen de ascenso: al llegar al nivel mínimo del rango siguiente puedes hacer un examen (una mazmorra privada
 *   con 3 oleadas y un examinador). Si lo superas subes de rango y ganas sus bonus para siempre.</li>
 *   <li>Prestigio: en el nivel 500 vuelves al nivel 1 a cambio del rango único MONARCA, bonus permanentes
 *   y el arma del Monarca de tu clase. Se puede repetir.</li>
 * </ul>
 */
public final class Ranking {
    private Ranking() {}

    public static final int OP_EXAM = 0;
    public static final int OP_PRESTIGE = 1;

    /** ACT_RANK: botones de la pestaña Rango. */
    public static void action(ServerPlayer p, int op, int arg) {
        if (op == OP_EXAM) {
            startExam(p);
        } else if (op == OP_PRESTIGE) {
            prestige(p);
        }
    }

    /** ¿Puede hacer ya el examen al rango siguiente? */
    public static boolean examReady(Data d) {
        return d.awakened == 1 && d.rank() < 5 && d.level >= Defs.RANK_MIN[d.rank() + 1];
    }

    public static boolean prestigeReady(Data d) {
        return d.awakened == 1 && d.level >= Defs.MAX_LEVEL;
    }

    // ================================================================ examen de ascenso

    public static void startExam(ServerPlayer p) {
        Data d = Store.get(p);
        if (d.awakened != 1) {
            SystemLogic.notify(p, Net.N_NOT_AWAKENED, 0, 0);
            return;
        }
        if (d.rank() >= 5) {
            SystemLogic.notify(p, Net.N_EXAM_DENIED, 3, 0);
            return;
        }
        int target = d.rank() + 1;
        if (d.level < Defs.RANK_MIN[target]) {
            SystemLogic.notify(p, Net.N_EXAM_DENIED, 0, Defs.RANK_MIN[target]);
            return;
        }
        if (d.inDungeon == 1 || Dungeons.inDungeon(p)) {
            SystemLogic.notify(p, Net.N_EXAM_DENIED, 1, 0);
            return;
        }
        if (!p.isAlive() || p.isSpectator()) return;
        if (!Dungeons.startExam(p, d, target)) {
            SystemLogic.notify(p, Net.N_EXAM_DENIED, 2, 0);
        }
    }

    /** Examen superado: sube el rango y aplica sus bonus. */
    public static void examWon(ServerPlayer p, int rank) {
        Data d = Store.get(p);
        if (d.awakened != 1 || rank <= d.rank() || rank > 5) return;
        int old = d.rank();
        d.hunterRank = rank;
        SystemLogic.applyAll(p, d);
        p.setHealth(p.getMaxHealth());
        d.mp = SystemLogic.maxMp(d);
        d.anim = 40;
        SystemLogic.notify(p, Net.N_RANK_UP, rank, old);
        p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        p.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.2F);
        ((ServerLevel) p.level()).sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1.0, p.getZ(),
                80, 0.7, 1.0, 0.7, 0.35);
        Shadows.refresh(p);
        SystemLogic.sync(p, d);
        Shadows.sendList(p);
        if (p.getServer() != null) Store.save(p.getServer(), p.getUUID());
    }

    public static void examFailed(ServerPlayer p, int why) {
        Data d = Store.get(p);
        d.examTries++;
        SystemLogic.notify(p, Net.N_EXAM_FAIL, why, d.examTries);
        SystemLogic.sync(p, d);
    }

    // ================================================================ prestigio

    /** Arma del Monarca de cada clase (la recompensa del prestigio). */
    public static Item weaponFor(int cls) {
        if (cls < 0 || cls >= ModItems.SOBERANO.length) return null;
        return ModItems.SOBERANO[cls];
    }

    public static void prestige(ServerPlayer p) {
        Data d = Store.get(p);
        if (d.awakened != 1) {
            SystemLogic.notify(p, Net.N_NOT_AWAKENED, 0, 0);
            return;
        }
        if (d.level < Defs.MAX_LEVEL) {
            SystemLogic.notify(p, Net.N_PRESTIGE_DENIED, 0, Defs.MAX_LEVEL);
            return;
        }
        if (d.inDungeon == 1 || Dungeons.inDungeon(p)) {
            SystemLogic.notify(p, Net.N_PRESTIGE_DENIED, 1, 0);
            return;
        }
        d.prestige++;
        // Vuelves al nivel 1: se reinician el nivel, la EXP y las estadísticas.
        // Se quedan la raza, la clase, la mutación, los árboles, las sombras, el oro, los objetos y el rango.
        d.level = 1;
        d.xp = 0;
        d.points = 0;
        d.stats = new int[]{Defs.BASE_STAT, Defs.BASE_STAT, Defs.BASE_STAT, Defs.BASE_STAT, Defs.BASE_STAT};
        Item weapon = weaponFor(d.cls);
        if (weapon != null) Shop.give(p, new ItemStack(weapon));
        SystemLogic.applyAll(p, d);
        p.setHealth(p.getMaxHealth());
        d.mp = SystemLogic.maxMp(d);
        d.anim = 40;
        SystemLogic.notify(p, Net.N_PRESTIGE, d.prestige, d.cls);
        p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 0.8F);
        p.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.5F, 1.3F);
        ServerLevel lvl = (ServerLevel) p.level();
        lvl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1.0, p.getZ(), 120, 0.8, 1.2, 0.8, 0.45);
        lvl.sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 0.3, p.getZ(), 60, 1.2, 0.2, 1.2, 0.04);
        lvl.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1.0, p.getZ(), 80, 0.6, 1.0, 0.6, 0.1);
        Shadows.refresh(p);
        SystemLogic.sync(p, d);
        Shadows.sendList(p);
        if (p.getServer() != null) Store.save(p.getServer(), p.getUUID());
    }
}
