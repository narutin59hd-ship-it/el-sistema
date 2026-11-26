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

    // Datos temporales (no se guardan)
    public transient int cooldown = 0;
    public transient int cooldownTotal = 1;
    public transient int anim = 0;
    public transient int joinDelay = 0;
    public transient boolean swordOn = false;

    /** Puntos por encima del valor inicial (10). */
    public int bonus(int stat) {
        return Math.max(0, stats[stat] - Defs.BASE_STAT);
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
    }
}
