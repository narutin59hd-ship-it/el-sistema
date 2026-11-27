package com.elsistema;

/**
 * Árbol propio de cada sombra (aparte del árbol general de sombras del Jugador). Las sombras suben de nivel
 * contigo: ganan la misma EXP que tú (y algo más con sus propias bajas) y cada nivel les da 1 punto que se
 * gasta aquí, eligiendo la sombra en la pestaña Sombras.
 */
public final class ShadowTree {
    private ShadowTree() {}

    public static final int DMG = 0;
    public static final int HP = 1;
    public static final int ARMOR = 2;
    public static final int SPEED = 3;
    public static final int STEAL = 4;
    public static final int CRIT = 5;
    public static final int REGEN = 6;

    /** Puntos que gana una sombra por cada nivel que sube. */
    public static final int POINTS_PER_LEVEL = 1;

    /** max = rangos, cost = puntos por rango, val = efecto por rango, unit = cómo se lee el total. */
    public record Node(String name, String desc, int max, int cost, double val, String unit) {}

    public static final Node[] NODES = {
            new Node("Garras afiladas", "+4% de daño por rango", 20, 1, 4.0, "% de daño"),
            new Node("Cuerpo de sombra", "+5% de vida máxima por rango", 20, 1, 5.0, "% de vida"),
            new Node("Coraza oscura", "+1 de armadura por rango", 15, 1, 1.0, " de armadura"),
            new Node("Paso veloz", "+3% de velocidad por rango", 10, 1, 3.0, "% de velocidad"),
            new Node("Sed de sangre", "Se cura el 2% del daño que hace por rango", 10, 2, 2.0, "% de robo de vida"),
            new Node("Golpe brutal", "+3% de probabilidad de golpe doble por rango", 10, 2, 3.0, "% de golpe doble"),
            new Node("Regeneración", "Se cura 0,5 de vida por segundo por rango", 10, 2, 0.5, " de vida por segundo")};

    public static final int SIZE = NODES.length;

    /** Puntos que hacen falta para tener todo el árbol al máximo. */
    public static int totalCost() {
        int s = 0;
        for (Node n : NODES) {
            s += n.max() * n.cost();
        }
        return s;
    }

    public static int rank(int[] ranks, int node) {
        if (ranks == null || node < 0 || node >= ranks.length || node >= SIZE) return 0;
        return Math.max(0, Math.min(NODES[node].max(), ranks[node]));
    }

    /** Efecto total de un nudo (por ejemplo 12 para +12% de daño). */
    public static double value(int[] ranks, int node) {
        return rank(ranks, node) * NODES[node].val();
    }

    private static String num(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-6) return String.valueOf((long) Math.rint(v));
        return String.valueOf(Math.round(v * 10.0) / 10.0).replace('.', ',');
    }

    /** Texto del efecto total de un nudo a un rango dado: "+12% de daño". */
    public static String effect(int node, int rank) {
        Node n = NODES[node];
        return "+" + num(rank * n.val()) + n.unit();
    }

    /** ¿Se puede subir este nudo con estos puntos? */
    public static boolean canUp(int[] ranks, int pts, int node) {
        if (node < 0 || node >= SIZE) return false;
        return rank(ranks, node) < NODES[node].max() && pts >= NODES[node].cost();
    }

    /** Copia los rangos en un array del tamaño correcto (datos viejos o rotos). */
    public static int[] fix(int[] ranks) {
        int[] out = new int[SIZE];
        if (ranks != null) {
            for (int i = 0; i < Math.min(SIZE, ranks.length); i++) {
                out[i] = Math.max(0, Math.min(NODES[i].max(), ranks[i]));
            }
        }
        return out;
    }
}
