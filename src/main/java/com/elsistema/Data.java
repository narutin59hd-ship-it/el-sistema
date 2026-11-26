package com.elsistema;

/** Datos de un Jugador (se guardan en la carpeta del mundo, en el_sistema/). */
public class Data {
    public int awakened = 0;
    public int race = -1;
    public int cls = -1;
    public int level = 1;
    public int xp = 0;
    public int points = 0;
    public int[] stats = {Defs.BASE_STAT, Defs.BASE_STAT, Defs.BASE_STAT, Defs.BASE_STAT, Defs.BASE_STAT};
    public double mp = 50.0;
    public float health = -1F;

    // Oro y árboles de habilidades
    public long coins = 0;
    public int skillPoints = 0;
    public int spGranted = 0;
    public int[][] raceTree = new int[6][5];
    public int[][] classTree = new int[5][5];

    // Para volver de una mazmorra si el juego se cierra dentro
    public int inDungeon = 0;
    public double retX = 0;
    public double retY = 0;
    public double retZ = 0;
    public int arenaX = 0;
    public int arenaY = 0;
    public int arenaZ = 0;

    // Datos temporales (no se guardan)
    public transient int cooldown = 0;
    public transient int cooldownTotal = 1;
    public transient int anim = 0;
    public transient int joinDelay = 0;
    public transient boolean swordOn = false;
    public transient int lastStand = 0;
    public transient int gateTimer = 0;

    /** Puntos por encima del valor inicial (10). */
    public int bonus(int stat) {
        return Math.max(0, stats[stat] - Defs.BASE_STAT);
    }

    public int raceRank(int node) {
        return race >= 0 && race < raceTree.length ? raceTree[race][node] : 0;
    }

    public int classRank(int node) {
        return cls >= 0 && cls < classTree.length ? classTree[cls][node] : 0;
    }

    /** Suma de un efecto especial en el árbol de tu raza y el de tu clase. */
    public double fx(int effect) {
        if (awakened != 1) return 0;
        double s = 0;
        Trees.Node[] rt = Trees.tree(true, race);
        if (rt != null) {
            for (int i = 0; i < rt.length; i++) {
                if (rt[i].fx() == effect) s += raceTree[race][i] * rt[i].val();
            }
        }
        Trees.Node[] ct = Trees.tree(false, cls);
        if (ct != null) {
            for (int i = 0; i < ct.length; i++) {
                if (ct[i].fx() == effect) s += classTree[cls][i] * ct[i].val();
            }
        }
        return s;
    }

    /** Corrige datos rotos o de versiones antiguas. */
    public void fix() {
        if (stats == null || stats.length != 5) {
            stats = new int[]{Defs.BASE_STAT, Defs.BASE_STAT, Defs.BASE_STAT, Defs.BASE_STAT, Defs.BASE_STAT};
        }
        for (int i = 0; i < stats.length; i++) {
            if (stats[i] < Defs.BASE_STAT) stats[i] = Defs.BASE_STAT;
        }
        if (level < 1) level = 1;
        if (level > Defs.MAX_LEVEL) level = Defs.MAX_LEVEL;
        if (xp < 0) xp = 0;
        if (points < 0) points = 0;
        if (race < -1 || race >= Defs.RACE_NAME.length) race = -1;
        if (cls < -1 || cls >= Defs.CLASS_NAME.length) cls = -1;
        if (awakened != 1 || race < 0 || cls < 0) awakened = 0;
        if (Double.isNaN(mp) || mp < 0) mp = 0;
        if (Float.isNaN(health)) health = -1F;
        if (coins < 0) coins = 0;
        if (skillPoints < 0) skillPoints = 0;
        raceTree = fixTree(raceTree, Trees.RACE);
        classTree = fixTree(classTree, Trees.CLASS);
        // Puntos de habilidad para los niveles que ya tenías antes de esta versión
        int target = level - 1;
        if (spGranted < target) {
            skillPoints += target - spGranted;
            spGranted = target;
        }
    }

    private static int[][] fixTree(int[][] t, Trees.Node[][] defs) {
        int[][] out = new int[defs.length][5];
        for (int i = 0; i < defs.length; i++) {
            for (int j = 0; j < 5; j++) {
                int v = t != null && i < t.length && t[i] != null && j < t[i].length ? t[i][j] : 0;
                out[i][j] = Math.max(0, Math.min(defs[i][j].max(), v));
            }
        }
        return out;
    }
}
