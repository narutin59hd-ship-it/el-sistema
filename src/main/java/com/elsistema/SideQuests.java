package com.elsistema;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Misiones secundarias de la ciudad. Las dan la recepcionista de la Asociación (0-3) y el maestro del gremio (4-7);
 * se aceptan y se cobran hablando con ellos en persona. La recompensa está oculta («???») hasta que la cobras.
 */
public final class SideQuests {
    private SideQuests() {}

    public static final int COUNT = 8;
    /** 0 sin aceptar, 1 en curso, 2 hecha (falta cobrarla), 3 cobrada. */
    public static final int OPEN = 0;
    public static final int ACTIVE = 1;
    public static final int READY = 2;
    public static final int CLAIMED = 3;

    public static final int Q_FIRST = 0;
    public static final int Q_GOBLINS = 1;
    public static final int Q_ARMY = 2;
    public static final int Q_TOURIST = 3;
    public static final int Q_ELITE = 4;
    public static final int Q_BREAKER = 5;
    public static final int Q_BOSSES = 6;
    public static final int Q_LEGEND = 7;

    public static final String[] NAME = {"Primer contrato", "Plaga de goblins", "El ejército crece", "Turista del Sistema",
            "Cazador de élite", "Rompepuertas", "Matajefes", "Leyenda oculta"};
    public static final String[] GOAL_TEXT = {"Completa 3 mazmorras", "Mata 40 goblins", "Levanta 10 sombras con ¡Levántate!",
            "Habla con todos los vecinos de la ciudad", "Mata 25 monstruos de rango C o superior",
            "Completa una mazmorra de rango B o superior", "Derrota a 5 jefes de mazmorra", "Derrota a un jefe secreto"};
    public static final int[] GOAL = {3, 40, 10, CityNpc.ROLES, 25, 1, 5, 1};
    /** Quién da cada misión. */
    public static final int[] GIVER = {CityNpc.RECEPTION, CityNpc.RECEPTION, CityNpc.RECEPTION, CityNpc.RECEPTION,
            CityNpc.GUILD, CityNpc.GUILD, CityNpc.GUILD, CityNpc.GUILD};
    /** Recompensa (sólo se ve al cobrarla). */
    public static final String[] REWARD = {"1.500 de oro y 15 esencias", "25 esencias y 3 pociones de curación",
            "+1 sombra para siempre y 20 esencias", "500 de oro y 10 esencias",
            "40 esencias y 3 puntos de estadística", "60 esencias y una runa de habilidad",
            "80 esencias y 5.000 de oro", "150 esencias, 2 puntos de habilidad y +1 sombra para siempre"};

    // ================================================================ aceptar y cobrar

    /** ACT_SIDE: a = misión, b = 0 aceptar, 1 cobrar. Hay que estar junto a quien la da. */
    public static void action(ServerPlayer p, int q, int op) {
        Data d = Store.get(p);
        if (d.awakened != 1 || q < 0 || q >= COUNT) return;
        if (!City.nearRole(p, GIVER[q])) {
            SystemLogic.notify(p, Net.N_FORGE_FAIL, 3, GIVER[q]);
            return;
        }
        if (op == 0 && d.side[q] == OPEN) {
            d.side[q] = ACTIVE;
            d.sideProg[q] = 0;
            if (q == Q_TOURIST) d.sideProg[q] = Integer.bitCount(d.talked);
            SystemLogic.notify(p, Net.N_SIDE, q, 0);
            check(p, d, q);
        } else if (op == 1 && d.side[q] == READY) {
            d.side[q] = CLAIMED;
            reward(p, d, q);
            SystemLogic.notify(p, Net.N_SIDE, q, 2);
            p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.2F);
        }
        SystemLogic.sync(p, d);
    }

    private static void reward(ServerPlayer p, Data d, int q) {
        switch (q) {
            case Q_FIRST -> {
                SystemLogic.addCoins(p, 1500);
                SystemLogic.addEssences(p, 15);
            }
            case Q_GOBLINS -> {
                SystemLogic.addEssences(p, 25);
                Shop.give(p, net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.POTION,
                        net.minecraft.world.item.alchemy.Potions.STRONG_HEALING));
                Shop.give(p, net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.POTION,
                        net.minecraft.world.item.alchemy.Potions.STRONG_HEALING));
                Shop.give(p, net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.POTION,
                        net.minecraft.world.item.alchemy.Potions.STRONG_HEALING));
            }
            case Q_ARMY -> {
                d.bonusSlots++;
                SystemLogic.addEssences(p, 20);
            }
            case Q_TOURIST -> {
                SystemLogic.addCoins(p, 500);
                SystemLogic.addEssences(p, 10);
            }
            case Q_ELITE -> {
                SystemLogic.addEssences(p, 40);
                d.points += 3;
            }
            case Q_BREAKER -> {
                SystemLogic.addEssences(p, 60);
                Shop.give(p, new ItemStack(ModItems.RUNE));
            }
            case Q_BOSSES -> {
                SystemLogic.addEssences(p, 80);
                SystemLogic.addCoins(p, 5000);
            }
            case Q_LEGEND -> {
                SystemLogic.addEssences(p, 150);
                d.skillPoints += 2;
                d.bonusSlots++;
            }
            default -> {
            }
        }
    }

    // ================================================================ progreso

    private static void add(ServerPlayer p, Data d, int q, int n) {
        if (d.side[q] != ACTIVE || n <= 0) return;
        d.sideProg[q] = Math.min(GOAL[q], d.sideProg[q] + n);
        check(p, d, q);
    }

    private static void check(ServerPlayer p, Data d, int q) {
        if (d.side[q] == ACTIVE && d.sideProg[q] >= GOAL[q]) {
            d.side[q] = READY;
            SystemLogic.notify(p, Net.N_SIDE, q, 1);
            p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.6F);
        }
    }

    public static void onKill(ServerPlayer p, LivingEntity dead) {
        if (dead instanceof Player || Shadows.isShadow(dead)) return;
        Data d = Store.get(p);
        if (d.awakened != 1) return;
        if (dead.getType() == ModEntities.GOBLIN) add(p, d, Q_GOBLINS, 1);
        if (dead instanceof Enemy && Ranks.rankOf(dead) >= 2) add(p, d, Q_ELITE, 1);
        if (Ranks.isBoss(dead)) add(p, d, Q_BOSSES, 1);
        if (dead instanceof SecretBoss) add(p, d, Q_LEGEND, 1);
    }

    public static void onDungeonClear(ServerPlayer p, Data d, int rank) {
        add(p, d, Q_FIRST, 1);
        if (rank >= 3) add(p, d, Q_BREAKER, 1);
    }

    public static void onArise(ServerPlayer p, Data d, int n) {
        add(p, d, Q_ARMY, n);
    }

    public static void onTalk(ServerPlayer p, Data d, int role) {
        int bit = 1 << role;
        if ((d.talked & bit) != 0) return;
        d.talked |= bit;
        if (d.side[Q_TOURIST] == ACTIVE) {
            d.sideProg[Q_TOURIST] = Math.min(GOAL[Q_TOURIST], Integer.bitCount(d.talked));
            check(p, d, Q_TOURIST);
            SystemLogic.sync(p, d);
        }
    }
}
