package com.elsistema;

/** Datos fijos del Sistema: estadísticas, rangos, razas y clases. */
public final class Defs {
    private Defs() {}

    public static final int BASE_STAT = 10;
    public static final int MAX_LEVEL = 100;

    /** EXP necesaria para pasar del nivel indicado al siguiente. */
    public static int xpNeeded(int level) {
        return 50 + 10 * level;
    }

    // ------------------------------------------------------------ Estadísticas
    public static final String[] STAT_NAME = {"FUERZA", "AGILIDAD", "VITALIDAD", "INTELIGENCIA", "PERCEPCIÓN"};
    public static final String[][] STAT_INFO = {
            {"+0.2 de daño de ataque por punto."},
            {"+0.3% de velocidad por punto (máx. +60%).", "+0.008 de velocidad de ataque por punto."},
            {"+0.5 de vida máxima por punto.", "(1 corazón cada 4 puntos)"},
            {"+3 de maná máximo por punto.", "+0.05 de maná por segundo por punto.", "+1% de daño de habilidad por punto."},
            {"-0.5% de enfriamiento por punto (máx. -50%).", "+0.02 de suerte por punto."}
    };

    // ------------------------------------------------------------ Rangos de cazador
    public static final String[] RANK = {"E", "D", "C", "B", "A", "S"};
    public static final int[] RANK_MIN = {1, 10, 25, 45, 65, 85};
    public static final int[] RANK_COLOR = {0xFFB0B0B0, 0xFF7BE38B, 0xFF5FB8FF, 0xFFB57BFF, 0xFFFFB347, 0xFFFF4D4D};
    /** Nivel máximo de cada rango (para mazmorras y monstruos). */
    public static final int[] RANK_MAX = {9, 24, 44, 64, 84, 100};

    public static int rankIndex(String s) {
        for (int i = 0; i < RANK.length; i++) {
            if (RANK[i].equalsIgnoreCase(s)) return i;
        }
        return -1;
    }

    public static int rank(int level) {
        int r = 0;
        for (int i = 0; i < RANK_MIN.length; i++) {
            if (level >= RANK_MIN[i]) r = i;
        }
        return r;
    }

    // ------------------------------------------------------------ Razas
    public static final int HUMANO = 0;
    public static final int ELFO = 1;
    public static final int ENANO = 2;
    public static final int ORCO = 3;
    public static final int DEMONIO = 4;
    public static final int DRAGON = 5;

    public static final String[] RACE_NAME = {"Humano", "Elfo", "Enano", "Orco", "Demonio", "Dragón"};
    public static final String[] RACE_DESC = {
            "Versátiles y ambiciosos. Crecen más rápido que nadie.",
            "Ágiles hijos del bosque, letales con el arco.",
            "Pequeños, duros como la roca y grandes mineros.",
            "Fuerza bruta en estado puro. Enormes y resistentes.",
            "Nacidos de las llamas. Dominan el maná y el fuego.",
            "Sangre de los antiguos dragones. Poderosos y temidos."
    };
    public static final String[][] RACE_BONUS = {
            {"+10% de experiencia", "+1 punto de estadística extra por nivel"},
            {"+10% de velocidad", "+20% de daño con flechas", "-1 corazón de vida"},
            {"Tamaño 80%", "+4 de armadura", "+25% de velocidad al picar", "+20% de resistencia al empuje", "-5% de velocidad"},
            {"Tamaño 115%", "+3 corazones de vida", "+2 de daño de ataque", "-5% de velocidad"},
            {"Inmune al fuego y a la lava", "+30 de maná máximo", "+1 de daño de ataque", "-1 corazón de vida"},
            {"Tamaño 110%", "+2 corazones de vida", "+2 de armadura", "+1 de daño de ataque", "-10% de experiencia"}
    };

    // ------------------------------------------------------------ Clases
    public static final int ESPADACHIN = 0;
    public static final int ARQUERO = 1;
    public static final int MAGO = 2;
    public static final int ASESINO = 3;
    public static final int TANQUE = 4;

    public static final String[] CLASS_NAME = {"Espadachín", "Arquero", "Mago", "Asesino", "Tanque"};
    public static final String[] CLASS_DESC = {
            "Maestro del combate cuerpo a cuerpo.",
            "Acaba con todo antes de que se acerque.",
            "Convierte el maná en destrucción pura.",
            "Ataca desde las sombras y desaparece.",
            "Un muro que nadie consigue atravesar."
    };
    public static final String[][] CLASS_BONUS = {
            {"+20% de daño con espada", "+0.4 de velocidad de ataque"},
            {"+30% de daño con flechas", "+5% de velocidad"},
            {"+50 de maná máximo", "Regeneración de maná doble"},
            {"+15% de velocidad", "+0.3 de velocidad de ataque"},
            {"+4 corazones de vida", "+4 de armadura", "+30% de resistencia al empuje", "-5% de velocidad"}
    };
    public static final String[] SKILL_NAME = {"Tajo giratorio", "Disparo múltiple", "Lanza arcana", "Sigilo", "Fortaleza"};
    public static final String[] SKILL_DESC = {
            "Golpea a todos los enemigos a tu alrededor con 1.5 veces el daño de tu arma y los empuja.",
            "Dispara 7 flechas críticas en abanico.",
            "Rayo de maná que atraviesa a todos los enemigos en línea. Escala con INT.",
            "Invisible, rápido y más fuerte durante 6 s. Los enemigos te pierden de vista.",
            "Resistencia y absorción durante 8 s. Empuja a los enemigos y los obliga a atacarte."
    };
    public static final int[] SKILL_COST = {20, 25, 15, 30, 30};
    /** Enfriamiento en ticks (20 ticks = 1 segundo). */
    public static final int[] SKILL_CD = {160, 120, 40, 360, 400};
}
