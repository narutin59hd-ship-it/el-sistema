package com.elsistema.client;

import java.util.List;

/** Copia de los datos del Jugador en el cliente (la envía el servidor). */
public final class CData {
    private CData() {}

    /** 36 números del Jugador + mutación, hitos, intentos de la prueba, 8 misiones (estado y progreso),
     *  rango de cazador, prestigio e intentos del examen de ascenso. */
    public static final int SIZE = 39 + 2 * com.elsistema.Quests.COUNT + 3;
    private static final int R0 = 39 + 2 * com.elsistema.Quests.COUNT;
    private static int[] d = fresh();

    private static int[] fresh() {
        int[] n = new int[SIZE];
        n[1] = -1;
        n[2] = -1;
        n[3] = 1;
        n[5] = 60;
        for (int i = 7; i <= 11; i++) {
            n[i] = 10;
        }
        n[15] = 1;
        n[34] = 2;
        n[35] = -1;
        return n;
    }

    public static void reset() {
        d = fresh();
    }

    public static void set(List<Integer> list) {
        if (list == null || list.size() != SIZE) return;
        int[] n = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            n[i] = list.get(i);
        }
        d = n;
    }

    /** Baja el enfriamiento en el cliente para que el contador sea fluido. */
    public static void tick() {
        if (d[14] > 0) d[14]--;
    }

    public static boolean awakened() { return d[0] == 1; }
    public static int race() { return d[1]; }
    public static int cls() { return d[2]; }
    public static int level() { return d[3]; }
    public static int xp() { return d[4]; }
    public static int need() { return Math.max(1, d[5]); }
    public static int points() { return d[6]; }
    public static int stat(int i) { return d[7 + i]; }
    public static int mp() { return d[12]; }
    public static int maxMp() { return Math.max(1, d[13]); }
    public static int cooldown() { return d[14]; }
    public static int cooldownTotal() { return Math.max(1, d[15]); }
    public static int coins() { return d[16]; }
    public static int skillPoints() { return d[17]; }
    public static int raceRank(int node) { return d[18 + node]; }
    public static int classRank(int node) { return d[23 + node]; }
    public static int shadowRank(int node) { return d[28 + node]; }
    public static int shadowCount() { return d[33]; }
    public static int shadowMax() { return Math.max(0, d[34]); }
    /** Conjunto de armadura completo que lleva puesto (-1 = ninguno). */
    public static int setBonus() { return d[35]; }
    /** ¿Ha mutado su clase (prueba del cambio de clase superada)? */
    public static boolean mutated() { return d[36] == 1; }
    public static int milestones() { return d[37]; }
    public static boolean milestone(int i) { return (d[37] & (1 << i)) != 0; }
    public static int trialTries() { return d[38]; }
    /** 0 oculta, 1 descubierta, 2 completada. */
    public static int quest(int q) { return q >= 0 && q < com.elsistema.Quests.COUNT ? d[39 + q] : 0; }
    public static int questProg(int q) {
        return q >= 0 && q < com.elsistema.Quests.COUNT ? d[39 + com.elsistema.Quests.COUNT + q] : 0;
    }
    /** Rango de cazador: 0 = E ... 5 = S. */
    public static int hunterRank() { return Math.max(0, Math.min(5, d[R0])); }
    public static int prestige() { return Math.max(0, d[R0 + 1]); }
    public static int examTries() { return d[R0 + 2]; }
    /** ¿Puede hacer el examen al rango siguiente? */
    public static boolean examReady() {
        return awakened() && hunterRank() < 5 && level() >= com.elsistema.Defs.RANK_MIN[hunterRank() + 1];
    }
    public static boolean prestigeReady() { return awakened() && level() >= com.elsistema.Defs.MAX_LEVEL; }
    /** Texto del rango: la letra o el rango único del prestigio (MONARCA). */
    public static String rankLabel() { return com.elsistema.Defs.rankLabel(hunterRank(), prestige()); }
    public static int rankColor() { return com.elsistema.Defs.rankLabelColor(hunterRank(), prestige()); }
    public static String className() { return com.elsistema.Defs.className(cls(), mutated()); }
    public static String skillName() { return com.elsistema.Defs.skillName(cls(), mutated()); }

    /** Rango de un nudo: kind 0 = raza, 1 = clase, 2 = sombras. */
    public static int rank(int kind, int node) {
        if (node < 0 || node > 4) return 0;
        return kind == 0 ? raceRank(node) : kind == 1 ? classRank(node) : shadowRank(node);
    }

    /** Índice del árbol de cada tipo (raza, clase o el único de sombras). */
    public static int treeIndex(int kind) {
        return kind == 0 ? race() : kind == 1 ? cls() : 0;
    }
}
