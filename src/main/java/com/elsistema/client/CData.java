package com.elsistema.client;

import java.util.List;

/** Copia de los datos del Jugador en el cliente (la envía el servidor). */
public final class CData {
    private CData() {}

    public static final int SIZE = 28;
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
}
