package com.elsistema;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Misiones ocultas del Sistema: no se ven hasta que cumples su condición secreta. También los hitos de nivel
 * (recompensas ocultas al llegar a ciertos niveles) y la prueba del cambio de clase, que hace mutar tu clase.
 */
public final class Quests {
    private Quests() {}

    public static final int COUNT = 8;
    public static final int Q_CLASS = 0;
    public static final int Q_NIGHT = 1;
    public static final int Q_LEGION = 2;
    public static final int Q_BOSSES = 3;
    public static final int Q_BEYOND = 4;
    public static final int Q_ECLIPSE = 5;
    public static final int Q_QUEEN = 6;
    public static final int Q_PRIMORDIAL = 7;

    public static final int HIDDEN = 0;
    public static final int ACTIVE = 1;
    public static final int DONE = 2;

    public static final String[] NAME = {
            "El despertar de la clase", "Cazador nocturno", "La legión silenciosa", "Cazador de jefes",
            "Más allá de tu rango", "La bestia del eclipse", "La reina carmesí", "El coloso primordial"};
    /** Qué dice el Sistema cuando la descubres. */
    public static final String[] DESC = {
            "Has llegado al nivel 40. El Sistema te ofrece cambiar de clase: supera la prueba en una mazmorra especial y derrota a su jefe secreto.",
            "La noche esconde a los monstruos más peligrosos. Cázalos bajo la luna.",
            "Los muertos esperan una orden. Reúne un ejército de sombras digno de un Monarca.",
            "Los grandes caen ante los fuertes. Derrota a jefes de mazmorra (también cuentan el Wither, el dragón, el Warden y el guardián anciano).",
            "Entra en mazmorras de un rango mayor que el tuyo y sal con vida.",
            "Una bestia enorme aparece algunas noches cerca de los cazadores de nivel 30 o más (más a menudo con luna llena). Huye al amanecer.",
            "A veces, en las mazmorras de rango B, A y S, el jefe no es el de siempre: es la Reina Carmesí.",
            "En las mazmorras de rango S duerme algo más antiguo que el propio Sistema."};
    public static final String[] GOAL_TEXT = {
            "Supera la prueba del cambio de clase", "Mata 100 monstruos de noche", "Ten 10 sombras en tu ejército",
            "Derrota a 10 jefes", "Supera 3 mazmorras de un rango mayor que el tuyo", "Derrota a la Bestia del Eclipse",
            "Derrota a la Reina Carmesí", "Derrota al Coloso Primordial"};
    public static final int[] GOAL = {1, 100, 10, 10, 3, 1, 1, 1};
    /** Progreso con el que se descubre la misión (0 = se descubre de otra forma). */
    public static final int[] REVEAL = {0, 10, 5, 1, 1, 0, 0, 0};
    /** Pista que se ve mientras la misión sigue oculta. */
    public static final String[] CLUE = {
            "El Sistema observa cómo creces...", "La noche esconde algo...", "Los muertos esperan una orden...",
            "Los grandes caen ante los fuertes...", "Más allá de tus límites...", "Cuando la luna está llena, algo aúlla...",
            "En los nidos más profundos reina alguien...", "Algo antiguo duerme en los templos..."};
    public static final String[][] REWARD = {
            {"Tu clase muta a una clase superior", "Nueva habilidad mejorada", "+5 puntos de estadística", "Hoja del Juicio (legendaria)"},
            {"+3 puntos de estadística", "+1500 de oro", "3 elixires de vida"},
            {"+2 sombras en tu ejército para siempre"},
            {"+3 puntos de habilidad", "+2500 de oro"},
            {"+5 puntos de estadística", "Una llave del rango siguiente al tuyo"},
            {"Colmillo del Eclipse (legendario)", "+5000 de oro"},
            {"Aguijón Carmesí (legendario)", "+3 puntos de habilidad"},
            {"Corazón Primordial (+2 sombras para siempre)", "+10000 de oro"}};

    // Hitos de nivel (recompensas ocultas)
    public static final int[] MS_LEVEL = {10, 20, 30, 50, 60, 70, 80, 90, 100};
    public static final String[] MS_NAME = {"Primeros pasos", "Cazador en ciernes", "Veterano", "Medio camino", "Élite",
            "Leyenda viva", "Pesadilla de los monstruos", "Al borde de la cima", "La cima"};
    public static final String[][] MS_REWARD = {
            {"+2 puntos de estadística", "2 pociones de maná"},
            {"+1 punto de habilidad", "+500 de oro"},
            {"+3 puntos de estadística", "1 runa de habilidad"},
            {"+2 puntos de habilidad", "+2000 de oro"},
            {"+4 puntos de estadística", "2 elixires de vida"},
            {"+1 sombra para siempre", "+3000 de oro"},
            {"+3 puntos de habilidad", "1 llave de mazmorra S"},
            {"+6 puntos de estadística", "+5000 de oro"},
            {"+1 sombra para siempre", "+5 puntos de habilidad", "+10000 de oro"}};

    /** Misión de cada jefe secreto (-1 si no tiene: el Guardián es el jefe de la prueba). */
    public static int questOf(SecretBoss.Kind k) {
        return switch (k) {
            case ECLIPSE -> Q_ECLIPSE;
            case QUEEN -> Q_QUEEN;
            case PRIMORDIAL -> Q_PRIMORDIAL;
            default -> -1;
        };
    }

    // ================================================================ bonus pasivos de la mutación

    /** Se suma a Data.fx: sombras extra para siempre y los bonus de la clase mutada. */
    public static double fx(Data d, int effect) {
        double s = effect == Trees.FX_SHADOW_SLOTS ? d.bonusSlots : 0.0;
        if (d.mutated != 1 || d.cls < 0) return s;
        if (effect == Trees.FX_SKILL_DMG) s += 25;
        switch (d.cls) {
            case Defs.ESPADACHIN -> {
                if (effect == Trees.FX_LIFESTEAL) s += 5;
            }
            case Defs.ARQUERO -> {
                if (effect == Trees.FX_ARROW_DMG) s += 25;
            }
            case Defs.MAGO -> {
                if (effect == Trees.FX_MP_MAX) s += 40;
                if (effect == Trees.FX_SKILL_CD) s += 20;
            }
            case Defs.ASESINO -> {
                if (effect == Trees.FX_SHADOW_SLOTS) s += 3;
                if (effect == Trees.FX_SHADOW_CHANCE) s += 10;
                if (effect == Trees.FX_SHADOW_POWER) s += 20;
            }
            case Defs.TANQUE -> {
                if (effect == Trees.FX_THORNS) s += 10;
            }
            default -> {
            }
        }
        return s;
    }

    // ================================================================ descubrir, avanzar y completar

    public static void discover(ServerPlayer p, Data d, int q) {
        if (q < 0 || q >= COUNT || d.quest[q] != HIDDEN || d.awakened != 1) return;
        d.quest[q] = ACTIVE;
        SystemLogic.notify(p, Net.N_QUEST_FOUND, q, 0);
        p.playNotifySound(SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 0.6F);
        p.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.8F);
        SystemLogic.sync(p, d);
    }

    /** Suma progreso a una misión que se cuenta (muertes, jefes, mazmorras). */
    private static void add(ServerPlayer p, Data d, int q, int amount) {
        if (d.quest[q] == DONE) return;
        d.questProg[q] = Math.min(GOAL[q], d.questProg[q] + amount);
        check(p, d, q);
    }

    /** Sube el progreso hasta un valor (por ejemplo, el tamaño del ejército). */
    private static void reach(ServerPlayer p, Data d, int q, int value) {
        if (d.quest[q] == DONE) return;
        d.questProg[q] = Math.max(d.questProg[q], Math.min(GOAL[q], value));
        check(p, d, q);
    }

    private static void check(ServerPlayer p, Data d, int q) {
        if (d.quest[q] == HIDDEN && REVEAL[q] > 0 && d.questProg[q] >= REVEAL[q]) discover(p, d, q);
        if (d.quest[q] == ACTIVE && d.questProg[q] >= GOAL[q]) complete(p, d, q);
    }

    public static void complete(ServerPlayer p, Data d, int q) {
        if (d.quest[q] == DONE || d.awakened != 1) return;
        d.quest[q] = DONE;
        d.questProg[q] = GOAL[q];
        SystemLogic.notify(p, Net.N_QUEST_DONE, q, 0);
        reward(p, d, q);
        p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        ((ServerLevel) p.level()).sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1.0, p.getZ(),
                60, 0.6, 1.0, 0.6, 0.3);
        SystemLogic.applyAll(p, d);
        if (q == Q_CLASS) {
            p.setHealth(p.getMaxHealth());
            d.mp = SystemLogic.maxMp(d);
        }
        Shadows.refresh(p);
        SystemLogic.sync(p, d);
        Shadows.sendList(p);
        if (p.getServer() != null) Store.save(p.getServer(), p.getUUID());
    }

    private static void give(ServerPlayer p, Item item, int count) {
        if (item != null) Shop.give(p, new ItemStack(item, count));
    }

    private static void reward(ServerPlayer p, Data d, int q) {
        switch (q) {
            case Q_CLASS -> {
                d.mutated = 1;
                d.points += 5;
                give(p, ModItems.HOJA_JUICIO, 1);
                SystemLogic.notify(p, Net.N_MUTATION, d.cls, 0);
                p.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.4F, 1.4F);
                d.anim = 40;
            }
            case Q_NIGHT -> {
                d.points += 3;
                d.coins += 1500;
                give(p, ModItems.ELIXIR, 3);
            }
            case Q_LEGION -> {
                d.bonusSlots += 2;
                SystemLogic.notify(p, Net.N_SLOTS_UP, 2, Shadows.maxSlots(d));
            }
            case Q_BOSSES -> {
                d.skillPoints += 3;
                d.coins += 2500;
            }
            case Q_BEYOND -> {
                d.points += 5;
                give(p, ModItems.KEYS[Math.min(5, d.rank() + 1)], 1);
            }
            case Q_ECLIPSE -> {
                give(p, ModItems.COLMILLO, 1);
                d.coins += 5000;
            }
            case Q_QUEEN -> {
                give(p, ModItems.AGUIJON, 1);
                d.skillPoints += 3;
            }
            case Q_PRIMORDIAL -> {
                give(p, ModItems.CORAZON, 1);
                d.coins += 10000;
            }
            default -> {
            }
        }
    }

    private static void milestone(ServerPlayer p, Data d, int i) {
        switch (i) {
            case 0 -> {
                d.points += 2;
                give(p, ModItems.POCION_MANA, 2);
            }
            case 1 -> {
                d.skillPoints += 1;
                d.coins += 500;
            }
            case 2 -> {
                d.points += 3;
                give(p, ModItems.RUNE, 1);
            }
            case 3 -> {
                d.skillPoints += 2;
                d.coins += 2000;
            }
            case 4 -> {
                d.points += 4;
                give(p, ModItems.ELIXIR, 2);
            }
            case 5 -> {
                d.bonusSlots += 1;
                d.coins += 3000;
            }
            case 6 -> {
                d.skillPoints += 3;
                give(p, ModItems.KEYS[5], 1);
            }
            case 7 -> {
                d.points += 6;
                d.coins += 5000;
            }
            default -> {
                d.bonusSlots += 1;
                d.skillPoints += 5;
                d.coins += 10000;
            }
        }
        SystemLogic.notify(p, Net.N_HIDDEN_REWARD, i, 0);
        p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 0.7F);
    }

    // ================================================================ ganchos

    /** Al subir de nivel (y al entrar en el mundo): misión del cambio de clase e hitos de nivel. */
    public static void onLevel(ServerPlayer p, Data d) {
        if (d.awakened != 1) return;
        if (d.mutated == 1 && d.quest[Q_CLASS] != DONE) {
            d.quest[Q_CLASS] = DONE;
            d.questProg[Q_CLASS] = GOAL[Q_CLASS];
        }
        if (d.level >= Defs.MUTATION_LEVEL && d.mutated != 1 && d.quest[Q_CLASS] == HIDDEN) {
            discover(p, d, Q_CLASS);
        }
        boolean any = false;
        for (int i = 0; i < MS_LEVEL.length; i++) {
            if (d.level >= MS_LEVEL[i] && (d.milestones & (1 << i)) == 0) {
                d.milestones |= 1 << i;
                milestone(p, d, i);
                any = true;
            }
        }
        if (any) {
            Shadows.refresh(p);
            SystemLogic.sync(p, d);
        }
    }

    /** Al entrar en el mundo (con un poco de retraso para que el cliente ya pueda mostrar avisos). */
    public static void onJoin(ServerPlayer p, Data d) {
        if (d.awakened != 1) return;
        onLevel(p, d);
        reach(p, d, Q_LEGION, d.shadows.size());
    }

    /** Después de cada ¡Levántate! */
    public static void onArmy(ServerPlayer p, Data d) {
        if (d.awakened == 1) reach(p, d, Q_LEGION, d.shadows.size());
    }

    private static boolean isBigBoss(LivingEntity e) {
        return Ranks.isBoss(e) || e instanceof WitherBoss || e instanceof EnderDragon || e instanceof Warden
                || e instanceof ElderGuardian;
    }

    /** Al matar una criatura (tú o tus sombras). */
    public static void onKill(ServerPlayer p, LivingEntity dead) {
        if (dead instanceof Player || Shadows.isShadow(dead)) return;
        Data d = Store.get(p);
        if (d.awakened != 1) return;
        if (dead instanceof SecretBoss sb) {
            secretBossKilled(sb);
        }
        if (isBigBoss(dead)) add(p, d, Q_BOSSES, 1);
        Level lvl = dead.level();
        if (dead instanceof Enemy && d.inDungeon != 1 && lvl.dimension() == Level.OVERWORLD && lvl.isNight()) {
            add(p, d, Q_NIGHT, 1);
        }
    }

    /** Un jefe secreto ha muerto: completa su misión para todos los cazadores que estaban cerca. */
    private static void secretBossKilled(SecretBoss sb) {
        int q = questOf(sb.secretKind());
        if (q < 0 || !(sb.level() instanceof ServerLevel lvl)) return;
        for (ServerPlayer o : lvl.players()) {
            if (o.distanceToSqr(sb) > 64 * 64) continue;
            Data od = Store.get(o);
            if (od.awakened != 1) continue;
            if (od.quest[q] == HIDDEN) od.quest[q] = ACTIVE;
            complete(o, od, q);
        }
    }

    /** Un jefe secreto acaba de aparecer: descubre su misión a quien esté cerca. */
    public static void secretBossAppeared(ServerLevel lvl, SecretBoss sb, boolean dungeon) {
        int q = questOf(sb.secretKind());
        for (ServerPlayer o : lvl.players()) {
            double dist = Math.sqrt(o.distanceToSqr(sb));
            if (dist > 96) continue;
            Data od = Store.get(o);
            if (od.awakened != 1) continue;
            SystemLogic.notify(o, Net.N_SECRET_BOSS, sb.secretKind().ordinal(), dungeon ? 0 : (int) dist);
            o.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.35F, 0.7F);
            if (q >= 0) discover(o, od, q);
        }
    }

    /** Al terminar una mazmorra normal. */
    public static void onDungeonClear(ServerPlayer p, Data d, int rank) {
        if (d.awakened == 1 && rank > d.rank()) add(p, d, Q_BEYOND, 1);
    }

    // ================================================================ prueba del cambio de clase

    /** ACT_QUEST: el cliente pulsa INICIAR PRUEBA en la pestaña Misiones. */
    public static void action(ServerPlayer p, int q, int op) {
        if (q == Q_CLASS && op == 0) startTrial(p);
    }

    public static void startTrial(ServerPlayer p) {
        Data d = Store.get(p);
        if (d.awakened != 1 || d.mutated == 1 || d.quest[Q_CLASS] != ACTIVE) {
            SystemLogic.notify(p, Net.N_TRIAL_DENIED, 0, 0);
            return;
        }
        if (d.inDungeon == 1 || Dungeons.inDungeon(p)) {
            SystemLogic.notify(p, Net.N_TRIAL_DENIED, 1, 0);
            return;
        }
        if (!p.isAlive() || p.isSpectator()) return;
        if (!Dungeons.startTrial(p, d)) {
            SystemLogic.notify(p, Net.N_TRIAL_DENIED, 2, 0);
        }
    }

    public static void trialWon(ServerPlayer p) {
        Data d = Store.get(p);
        if (d.quest[Q_CLASS] == HIDDEN) d.quest[Q_CLASS] = ACTIVE;
        complete(p, d, Q_CLASS);
    }

    public static void trialFailed(ServerPlayer p, int why) {
        Data d = Store.get(p);
        d.trialTries++;
        SystemLogic.notify(p, Net.N_TRIAL_FAIL, why, d.trialTries);
        SystemLogic.sync(p, d);
    }

    /** Comando /sistema reiniciar. */
    public static void wipe(Data d) {
        d.mutated = 0;
        d.quest = new int[COUNT];
        d.questProg = new int[COUNT];
        d.milestones = 0;
        d.bonusSlots = 0;
        d.trialTries = 0;
    }
}
