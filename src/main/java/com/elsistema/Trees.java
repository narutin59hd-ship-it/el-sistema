package com.elsistema;

/**
 * Árboles de habilidades: uno por raza, uno por clase y el de las Sombras (común a todos).
 * Cada árbol tiene 5 nudos: 0 es la raíz, 1 y 2 salen de 0, 3 sale de 1 y 4 sale de 2.
 */
public final class Trees {
    private Trees() {}

    // Efectos especiales (el valor se multiplica por el rango del nudo)
    public static final int FX_XP = 1;            // % de EXP
    public static final int FX_COINS = 2;         // % de oro
    public static final int FX_SKILL_DMG = 3;     // % de daño de la habilidad de clase
    public static final int FX_SKILL_CD = 4;      // % menos de enfriamiento
    public static final int FX_MP_MAX = 5;        // maná máximo
    public static final int FX_MP_REGEN = 6;      // % de regeneración de maná
    public static final int FX_ARROW_DMG = 7;     // % de daño con flechas
    public static final int FX_LIFESTEAL = 8;     // % del daño hecho que te curas
    public static final int FX_DODGE = 9;         // % de esquivar un golpe
    public static final int FX_THORNS = 10;       // % del daño recibido que devuelves
    public static final int FX_FIRE_IMMUNE = 11;  // inmune al fuego
    public static final int FX_NIGHT_VISION = 12; // visión nocturna
    public static final int FX_FIRE_HIT = 13;     // segundos de fuego al golpear
    public static final int FX_POISON_HIT = 14;   // segundos de veneno al golpear
    public static final int FX_REGEN = 15;        // vida cada 5 s
    public static final int FX_LAST_STAND = 16;   // última resistencia por debajo del 30% de vida
    public static final int FX_GLIDE = 17;        // planear con Mayús en el aire
    public static final int FX_EXTRA_ARROWS = 18; // flechas extra en Disparo múltiple
    public static final int FX_SKILL_RADIUS = 19; // bloques extra de alcance del Tajo giratorio
    public static final int FX_SKILL_TIME = 20;   // segundos extra de Sigilo o Fortaleza
    public static final int FX_FIRE_ARROWS = 21;  // flechas en llamas
    public static final int FX_DROP = 22;         // % de probabilidad de botín
    public static final int FX_SHADOW_SLOTS = 23; // sombras extra en el ejército
    public static final int FX_SHADOW_CHANCE = 24; // % de probabilidad de levantar a un muerto (Perspicacia)
    public static final int FX_SHADOW_POWER = 25; // % de vida y daño de las sombras
    public static final int FX_SHADOW_REGEN = 26; // curación de las sombras

    // Tipos de árbol
    public static final int KIND_RACE = 0;
    public static final int KIND_CLASS = 1;
    public static final int KIND_SHADOW = 2;

    // Efectos de atributo: ATTR + índice en SystemLogic.TREE_ATTRS
    public static final int ATTR = 100;
    public static final int A_HP = ATTR;
    public static final int A_ARMOR = ATTR + 1;
    public static final int A_DMG = ATTR + 2;
    public static final int A_ATKSPD = ATTR + 3;
    public static final int A_SPEED = ATTR + 4;
    public static final int A_KB = ATTR + 5;
    public static final int A_MINE = ATTR + 6;
    public static final int A_TOUGH = ATTR + 7;

    public static final int[] PARENT = {-1, 0, 0, 1, 2};

    /** name, descripción por rango, rango máximo, nivel necesario, efecto, valor por rango. */
    public record Node(String name, String desc, int max, int lvl, int fx, double val) {}

    private static Node n(String name, String desc, int max, int lvl, int fx, double val) {
        return new Node(name, desc, max, lvl, fx, val);
    }

    public static final String[] RACE_TREE = {
            "Potencial ilimitado", "Hijos del bosque", "Forjados en piedra",
            "Fuerza bruta", "Hijos del abismo", "Linaje ancestral"};

    public static final Node[][] RACE = {
            { // Humano
                    n("Aprendiz rápido", "+2% de EXP por rango", 10, 1, FX_XP, 2),
                    n("Comerciante nato", "+3% de oro por rango", 10, 5, FX_COINS, 3),
                    n("Determinación", "+1 de vida máxima por rango", 10, 5, A_HP, 1),
                    n("Versatilidad", "+2% de daño de habilidad por rango", 5, 25, FX_SKILL_DMG, 2),
                    n("Voluntad indomable", "Por debajo del 30% de vida: Resistencia II y Fuerza 6 s (cada 60 s)", 1, 40, FX_LAST_STAND, 1)},
            { // Elfo
                    n("Paso ligero", "+1% de velocidad por rango", 10, 1, A_SPEED, 0.01),
                    n("Ojo de halcón", "+3% de daño con flechas por rango", 10, 5, FX_ARROW_DMG, 3),
                    n("Gracia élfica", "+2% de probabilidad de esquivar por rango", 5, 10, FX_DODGE, 2),
                    n("Ojos de la noche", "Visión nocturna permanente", 1, 20, FX_NIGHT_VISION, 1),
                    n("Bendición del bosque", "Recuperas 1 de vida cada 5 s por rango", 3, 35, FX_REGEN, 1)},
            { // Enano
                    n("Piel de piedra", "+1 de armadura por rango", 10, 1, A_ARMOR, 1),
                    n("Minero experto", "+5% de velocidad al picar por rango", 10, 5, A_MINE, 0.05),
                    n("Firmeza enana", "+5% de resistencia al empuje por rango", 5, 10, A_KB, 0.05),
                    n("Buscador de tesoros", "+5% de probabilidad de botín por rango", 5, 20, FX_DROP, 5),
                    n("Corazón de montaña", "+0.5 de dureza de armadura por rango", 5, 35, A_TOUGH, 0.5)},
            { // Orco
                    n("Músculos de hierro", "+0.5 de daño por rango", 10, 1, A_DMG, 0.5),
                    n("Sangre caliente", "+1 de vida máxima por rango", 10, 5, A_HP, 1),
                    n("Sed de batalla", "Te curas el 1% del daño que haces por rango", 5, 10, FX_LIFESTEAL, 1),
                    n("Piel gruesa", "+1 de armadura por rango", 5, 20, A_ARMOR, 1),
                    n("Furia orca", "Por debajo del 30% de vida: Resistencia II y Fuerza 6 s (cada 60 s)", 1, 40, FX_LAST_STAND, 1)},
            { // Demonio
                    n("Pozo de maná", "+5 de maná máximo por rango", 10, 1, FX_MP_MAX, 5),
                    n("Llamas infernales", "Tus golpes queman 1 s por rango", 3, 5, FX_FIRE_HIT, 1),
                    n("Regeneración demoníaca", "+5% de regeneración de maná por rango", 10, 10, FX_MP_REGEN, 5),
                    n("Espinas del abismo", "Devuelves el 5% del daño recibido por rango", 5, 20, FX_THORNS, 5),
                    n("Hambre de almas", "Te curas el 2% del daño que haces por rango", 5, 35, FX_LIFESTEAL, 2)},
            { // Dragón
                    n("Escamas de dragón", "+0.5 de dureza de armadura por rango", 10, 1, A_TOUGH, 0.5),
                    n("Garras", "+0.5 de daño por rango", 10, 5, A_DMG, 0.5),
                    n("Alas", "Planeas al mantener Mayús en el aire", 1, 10, FX_GLIDE, 1),
                    n("Aliento de fuego", "Tus golpes queman 1 s por rango", 3, 20, FX_FIRE_HIT, 1),
                    n("Sangre de dragón", "Inmune al fuego y a la lava", 1, 30, FX_FIRE_IMMUNE, 1)}
    };

    public static final String[] CLASS_TREE = {
            "Camino de la espada", "Ojo del cazador", "Senda arcana", "Arte de las sombras", "Muralla viviente"};

    public static final Node[][] CLASS = {
            { // Espadachín
                    n("Filo afilado", "+3% de daño del Tajo giratorio por rango", 10, 1, FX_SKILL_DMG, 3),
                    n("Torbellino", "+0.3 bloques de alcance del Tajo por rango", 5, 5, FX_SKILL_RADIUS, 0.3),
                    n("Maestría con espada", "+0.3 de daño por rango", 10, 5, A_DMG, 0.3),
                    n("Ritmo de combate", "-4% de enfriamiento por rango", 5, 20, FX_SKILL_CD, 4),
                    n("Sed de acero", "Te curas el 2% del daño que haces por rango", 5, 30, FX_LIFESTEAL, 2)},
            { // Arquero
                    n("Puntería", "+3% de daño con flechas por rango", 10, 1, FX_ARROW_DMG, 3),
                    n("Lluvia de flechas", "+1 flecha en Disparo múltiple por rango", 4, 5, FX_EXTRA_ARROWS, 1),
                    n("Pies ligeros", "+1% de velocidad por rango", 10, 5, A_SPEED, 0.01),
                    n("Recarga rápida", "-4% de enfriamiento por rango", 5, 20, FX_SKILL_CD, 4),
                    n("Flechas de fuego", "Todas tus flechas salen en llamas", 1, 30, FX_FIRE_ARROWS, 1)},
            { // Mago
                    n("Poder arcano", "+4% de daño de la Lanza arcana por rango", 10, 1, FX_SKILL_DMG, 4),
                    n("Reserva de maná", "+8 de maná máximo por rango", 10, 5, FX_MP_MAX, 8),
                    n("Meditación", "+8% de regeneración de maná por rango", 10, 5, FX_MP_REGEN, 8),
                    n("Canalización", "-4% de enfriamiento por rango", 5, 20, FX_SKILL_CD, 4),
                    n("Barrera de maná", "+3% de probabilidad de anular un golpe por rango", 5, 30, FX_DODGE, 3)},
            { // Asesino
                    n("Hoja envenenada", "Tus golpes envenenan 1 s por rango", 5, 1, FX_POISON_HIT, 1),
                    n("Sombra prolongada", "+1 s de Sigilo por rango", 5, 5, FX_SKILL_TIME, 1),
                    n("Reflejos", "+2% de probabilidad de esquivar por rango", 5, 5, FX_DODGE, 2),
                    n("Hoja oculta", "+0.4 de daño por rango", 10, 20, A_DMG, 0.4),
                    n("Paso sombrío", "-5% de enfriamiento por rango", 5, 30, FX_SKILL_CD, 5)},
            { // Tanque
                    n("Muralla", "+1 de vida máxima por rango", 10, 1, A_HP, 1),
                    n("Placas reforzadas", "+1 de armadura por rango", 10, 5, A_ARMOR, 1),
                    n("Represalia", "Devuelves el 5% del daño recibido por rango", 5, 5, FX_THORNS, 5),
                    n("Fortaleza prolongada", "+1 s de Fortaleza por rango", 5, 20, FX_SKILL_TIME, 1),
                    n("Inamovible", "+6% de resistencia al empuje por rango", 5, 30, A_KB, 0.06)}
    };

    public static final String SHADOW_TREE = "Monarca de las Sombras";

    public static final Node[] SHADOW = {
            n("Ejército de sombras", "+1 sombra en tu ejército por rango", 10, 1, FX_SHADOW_SLOTS, 1),
            n("Perspicacia", "+4% de probabilidad de levantar a un muerto por rango", 10, 5, FX_SHADOW_CHANCE, 4),
            n("Poder de las sombras", "+5% de vida y daño de tus sombras por rango", 10, 5, FX_SHADOW_POWER, 5),
            n("Legión", "+2 sombras en tu ejército por rango", 5, 25, FX_SHADOW_SLOTS, 2),
            n("Soberano de la noche", "Tus sombras se curan 1 de vida por segundo por rango", 3, 40, FX_SHADOW_REGEN, 1)};

    public static Node[] tree(boolean race, int index) {
        Node[][] all = race ? RACE : CLASS;
        return index >= 0 && index < all.length ? all[index] : null;
    }

    /** kind: 0 = raza, 1 = clase, 2 = sombras. */
    public static Node[] tree(int kind, int index) {
        if (kind == KIND_SHADOW) return SHADOW;
        return tree(kind == KIND_RACE, index);
    }

    private static String num(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-6) return String.valueOf((long) Math.rint(v));
        return String.valueOf(Math.round(v * 100.0) / 100.0);
    }

    /** Texto corto con el efecto total de un nudo a un rango dado (para el panel de detalles). */
    public static String effect(Node n, int rank) {
        double v = n.val() * rank;
        String x = num(v);
        String pct = num(v * 100.0);
        return switch (n.fx()) {
            case FX_XP -> "+" + x + "% de EXP";
            case FX_COINS -> "+" + x + "% de oro";
            case FX_SKILL_DMG -> "+" + x + "% de daño de habilidad";
            case FX_SKILL_CD -> "-" + x + "% de enfriamiento";
            case FX_MP_MAX -> "+" + x + " de maná máximo";
            case FX_MP_REGEN -> "+" + x + "% de regeneración de maná";
            case FX_ARROW_DMG -> "+" + x + "% de daño con flechas";
            case FX_LIFESTEAL -> x + "% de robo de vida";
            case FX_DODGE -> x + "% de esquivar";
            case FX_THORNS -> x + "% del daño devuelto";
            case FX_FIRE_IMMUNE -> rank > 0 ? "Inmune al fuego" : "Sin efecto";
            case FX_NIGHT_VISION -> rank > 0 ? "Visión nocturna" : "Sin efecto";
            case FX_FIRE_HIT -> x + " s de fuego al golpear";
            case FX_POISON_HIT -> x + " s de veneno al golpear";
            case FX_REGEN -> "+" + x + " de vida cada 5 s";
            case FX_LAST_STAND -> rank > 0 ? "Última resistencia activa" : "Sin efecto";
            case FX_GLIDE -> rank > 0 ? "Puedes planear" : "Sin efecto";
            case FX_EXTRA_ARROWS -> "+" + x + " flechas";
            case FX_SKILL_RADIUS -> "+" + x + " bloques de alcance";
            case FX_SKILL_TIME -> "+" + x + " s de duración";
            case FX_FIRE_ARROWS -> rank > 0 ? "Flechas en llamas" : "Sin efecto";
            case FX_DROP -> "+" + x + "% de botín";
            case FX_SHADOW_SLOTS -> "+" + x + " sombras";
            case FX_SHADOW_CHANCE -> "+" + x + "% de levantar muertos";
            case FX_SHADOW_POWER -> "+" + x + "% de poder de las sombras";
            case FX_SHADOW_REGEN -> "+" + x + " de vida por segundo";
            case A_HP -> "+" + x + " de vida máxima";
            case A_ARMOR -> "+" + x + " de armadura";
            case A_DMG -> "+" + x + " de daño";
            case A_ATKSPD -> "+" + x + " de velocidad de ataque";
            case A_SPEED -> "+" + pct + "% de velocidad";
            case A_KB -> "+" + pct + "% de resistencia al empuje";
            case A_MINE -> "+" + pct + "% de velocidad al picar";
            case A_TOUGH -> "+" + x + " de dureza";
            default -> x;
        };
    }
}
