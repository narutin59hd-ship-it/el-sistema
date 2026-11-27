package com.elsistema;

import java.util.ArrayList;
import java.util.List;

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
    public int[] shadowTree = new int[5];

    // Ejército de sombras
    public List<ShadowRec> shadows = new ArrayList<>();
    public int nextShadow = 1;

    // Misiones ocultas, hitos de nivel y cambio de clase
    public int mutated = 0;
    /** Estado de cada misión: 0 oculta, 1 descubierta, 2 completada. */
    public int[] quest = new int[Quests.COUNT];
    public int[] questProg = new int[Quests.COUNT];
    /** Hitos de nivel ya cobrados (un bit por hito). */
    public int milestones = 0;
    /** Sombras extra para siempre (recompensas ocultas). */
    public int bonusSlots = 0;
    public int trialTries = 0;

    // Rango de cazador (se sube con el examen de ascenso) y prestigio (nivel 500)
    /** 0 = E ... 5 = S. -1 = datos de antes de la 1.4.0 (se calcula con el nivel al cargar). */
    public int hunterRank = -1;
    public int examTries = 0;
    public int prestige = 0;

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
    public transient int setBonus = -1;
    public transient int bossTimer = 0;

    /** Una sombra del ejército (guardada, fuera o recuperándose). */
    public static class ShadowRec {
        public int id;
        public String type = "";
        public String key = "";
        public int rank;
        public int level = 1;
        public int kills;
        public boolean boss;
        public String nbt = "";
        public int state;           // 0 guardada, 1 fuera, 2 caída (inhabilitada)
        /** (Antiguo, en milisegundos: ya no se usa.) */
        public long recoverAt;
        /** Hora del mundo (getDayTime) y tiempo de juego (getGameTime) a los que vuelve una sombra caída. */
        public long recoverDay;
        public long recoverTick;
        public float hp = 1F;
        public boolean wasOut;
        /** EXP hacia el siguiente nivel (las sombras suben de nivel contigo). */
        public int xp;
        /** Puntos sin gastar de su árbol propio. */
        public int pts;
        /** Rangos de su árbol propio (ShadowTree). */
        public int[] tree = new int[ShadowTree.SIZE];
    }

    public ShadowRec shadow(int id) {
        for (ShadowRec r : shadows) {
            if (r.id == id) return r;
        }
        return null;
    }

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

    public int shadowRank(int node) {
        return node >= 0 && node < shadowTree.length ? shadowTree[node] : 0;
    }

    /** Rangos del árbol indicado (0 raza, 1 clase, 2 sombras) o null. */
    public int[] ranks(int kind) {
        if (kind == Trees.KIND_SHADOW) return shadowTree;
        if (kind == Trees.KIND_RACE) return race >= 0 && race < raceTree.length ? raceTree[race] : null;
        return cls >= 0 && cls < classTree.length ? classTree[cls] : null;
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
        for (int i = 0; i < Trees.SHADOW.length; i++) {
            if (Trees.SHADOW[i].fx() == effect) s += shadowTree[i] * Trees.SHADOW[i].val();
        }
        return s + Quests.fx(this, effect);
    }

    /** Rango de cazador (0 = E ... 5 = S). */
    public int rank() {
        return Math.max(0, Math.min(5, hunterRank));
    }

    public boolean isMutated() {
        return awakened == 1 && mutated == 1;
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
        shadowTree = fixTree(new int[][]{shadowTree}, new Trees.Node[][]{Trees.SHADOW})[0];
        if (shadows == null) shadows = new ArrayList<>();
        shadows.removeIf(r -> r == null || r.nbt == null || r.nbt.isEmpty() || r.type == null);
        int maxId = 0;
        for (ShadowRec r : shadows) {
            maxId = Math.max(maxId, r.id);
            if (r.state == 1) {
                r.state = 0;
                r.wasOut = true;
            }
            if (r.key == null) r.key = "";
            if (Float.isNaN(r.hp) || r.hp <= 0F || r.hp > 1F) r.hp = 1F;
            r.level = Math.max(1, Math.min(Defs.MAX_LEVEL, r.level));
            if (r.xp < 0 || r.level >= Defs.MAX_LEVEL) r.xp = 0;
            if (r.pts < 0) r.pts = 0;
            r.tree = ShadowTree.fix(r.tree);
        }
        if (nextShadow <= maxId) nextShadow = maxId + 1;
        quest = fixArr(quest, 0, 2);
        questProg = fixArr(questProg, 0, Integer.MAX_VALUE);
        if (mutated != 1) mutated = 0;
        bonusSlots = Math.max(0, Math.min(60, bonusSlots));
        if (trialTries < 0) trialTries = 0;
        // Los cazadores de antes de la 1.4.0 conservan el rango que tenían por su nivel
        if (hunterRank < 0) hunterRank = awakened == 1 ? Defs.rank(level) : 0;
        if (hunterRank > 5) hunterRank = 5;
        if (examTries < 0) examTries = 0;
        if (prestige < 0) prestige = 0;
        if (prestige > 99) prestige = 99;
        for (ShadowRec r : shadows) {
            // Sombras que se estaban recuperando con el sistema antiguo: vuelven enseguida
            if (r.state == 2 && r.recoverTick == 0 && r.recoverDay == 0) r.recoverTick = -1;
        }
        // Puntos de habilidad para los niveles que ya tenías antes de esta versión
        int target = level - 1;
        if (spGranted < target) {
            skillPoints += target - spGranted;
            spGranted = target;
        }
    }

    private static int[] fixArr(int[] a, int min, int max) {
        int[] out = new int[Quests.COUNT];
        for (int i = 0; i < out.length; i++) {
            int v = a != null && i < a.length ? a[i] : 0;
            out[i] = Math.max(min, Math.min(max, v));
        }
        return out;
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
