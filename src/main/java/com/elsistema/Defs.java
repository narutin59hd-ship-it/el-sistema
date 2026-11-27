package com.elsistema;

/** Datos fijos del Sistema: estadísticas, rangos, razas y clases. */
public final class Defs {
    private Defs() {}

    public static final int BASE_STAT = 10;
    /** Nivel máximo del Jugador (y de los monstruos, las mazmorras y las sombras). En el 500 se hace el prestigio. */
    public static final int MAX_LEVEL = 500;

    /** EXP necesaria para pasar del nivel indicado al siguiente (a partir del 100 sube más despacio). */
    public static int xpNeeded(int level) {
        if (level <= 100) return 50 + 10 * level;
        return 1050 + 25 * (level - 100);
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
    /** Nivel máximo de cada rango (para mazmorras y monstruos). El rango S llega hasta el nivel máximo. */
    public static final int[] RANK_MAX = {9, 24, 44, 64, 84, MAX_LEVEL};

    // ------------------------------------------------------------ Rango del cazador (examen de ascenso)
    /** Bonus de cada rango de cazador. El rango se gana con el examen de ascenso (pestaña Rango). */
    public static final int[] RANK_DMG = {0, 5, 10, 15, 20, 30};   // % de daño
    public static final int[] RANK_HP = {0, 5, 10, 15, 20, 30};    // % de vida máxima
    public static final int[] RANK_XP = {0, 0, 5, 10, 15, 25};     // % de EXP
    public static final int[] RANK_SLOTS = {0, 0, 0, 1, 2, 3};     // sombras extra en el ejército
    public static final int[] RANK_ARISE = {0, 0, 0, 0, 5, 10};    // % de probabilidad de levantar a un muerto
    public static final int[] RANK_SPEED = {0, 0, 0, 0, 0, 10};    // % de velocidad
    /** Oleadas del examen de ascenso (después sale el examinador). */
    public static final int EXAM_WAVES = 3;

    /** Bonus que da tener el rango indicado (para la pestaña Rango y los avisos). */
    public static java.util.List<String> rankBuffs(int rank) {
        java.util.List<String> l = new java.util.ArrayList<>();
        int r = Math.max(0, Math.min(5, rank));
        if (RANK_DMG[r] > 0) l.add("+" + RANK_DMG[r] + "% de daño");
        if (RANK_HP[r] > 0) l.add("+" + RANK_HP[r] + "% de vida máxima");
        if (RANK_XP[r] > 0) l.add("+" + RANK_XP[r] + "% de EXP");
        if (RANK_SLOTS[r] > 0) l.add("+" + RANK_SLOTS[r] + (RANK_SLOTS[r] == 1 ? " sombra" : " sombras") + " en tu ejército");
        if (RANK_ARISE[r] > 0) l.add("+" + RANK_ARISE[r] + "% de probabilidad de levantar sombras");
        if (RANK_SPEED[r] > 0) l.add("+" + RANK_SPEED[r] + "% de velocidad");
        return l;
    }

    /** Nivel de los monstruos del examen de ascenso al rango indicado. */
    public static int examLevel(int target, int playerLevel) {
        int t = Math.max(1, Math.min(5, target));
        int lvl = Math.max(RANK_MIN[t], Math.min(playerLevel, RANK_MAX[t]));
        return Math.min(MAX_LEVEL, lvl + 2);
    }

    // ------------------------------------------------------------ Prestigio (nivel 500)
    public static final int PRESTIGE_MAX = 10;   // a partir de aquí los bonus ya no suben
    public static final int P_XP = 10;           // % de EXP por prestigio
    public static final int P_DMG = 5;           // % de daño por prestigio
    public static final int P_HP = 5;            // % de vida máxima por prestigio
    public static final int P_SLOTS = 2;         // sombras extra por prestigio
    public static final int P_POINTS = 1;        // puntos de estadística extra por nivel y prestigio
    public static final int MONARCA_COLOR = 0xFFC77DFF;

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    /** Prestigios que cuentan para los bonus (máximo 10). */
    public static int prestigeLevel(int prestige) {
        return Math.max(0, Math.min(PRESTIGE_MAX, prestige));
    }

    /** Rango único del prestigio: MONARCA, MONARCA II, MONARCA III... */
    public static String prestigeName(int prestige) {
        if (prestige <= 0) return "";
        if (prestige == 1) return "MONARCA";
        return "MONARCA " + (prestige < ROMAN.length ? ROMAN[prestige] : String.valueOf(prestige));
    }

    /** Nombre del rango que se enseña: la letra del rango o el rango único del prestigio. */
    public static String rankLabel(int rank, int prestige) {
        return prestige > 0 ? prestigeName(prestige) : RANK[Math.max(0, Math.min(5, rank))];
    }

    public static int rankLabelColor(int rank, int prestige) {
        return prestige > 0 ? MONARCA_COLOR : RANK_COLOR[Math.max(0, Math.min(5, rank))];
    }

    /** Bonus totales del prestigio indicado. */
    public static java.util.List<String> prestigeBuffs(int prestige) {
        java.util.List<String> l = new java.util.ArrayList<>();
        int p = prestigeLevel(prestige);
        if (p <= 0) return l;
        l.add("+" + P_XP * p + "% de EXP");
        l.add("+" + P_DMG * p + "% de daño y +" + P_HP * p + "% de vida");
        l.add("+" + P_SLOTS * p + " sombras en tu ejército");
        l.add("+" + P_POINTS * p + (p == 1 ? " punto" : " puntos") + " extra por nivel");
        return l;
    }

    // ------------------------------------------------------------ Sombras
    /** Probabilidad base (%) de levantar a un muerto de cada rango. */
    public static final int[] ARISE_BASE = {75, 60, 46, 34, 24, 15};
    /** Maná que cuesta cada intento de ¡Levántate! según el rango del muerto (los jefes cuestan un 50% más). */
    public static final int[] ARISE_COST = {10, 15, 22, 30, 40, 50};
    /** Intentos que tienes con cada cadáver. */
    public static final int ARISE_TRIES = 3;
    /** Multiplicador de daño de una sombra según su rango. */
    public static final double[] SHADOW_RANK_DMG = {1.0, 1.25, 1.6, 2.0, 2.6, 3.5};
    /** Días de Minecraft que una sombra caída queda inhabilitada. */
    public static final int SHADOW_DOWN_DAYS = 3;
    public static final long SHADOW_DOWN_TICKS = SHADOW_DOWN_DAYS * 24000L;

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

    // ------------------------------------------------------------ Cambio de clase (mutaciones)
    /** Nivel al que aparece la misión oculta del cambio de clase. */
    public static final int MUTATION_LEVEL = 40;
    public static final String[] MUTATION_NAME = {"Señor de la Guerra", "Cazador Celestial", "Hechicero del Abismo",
            "Nigromante", "Bastión Inmortal"};
    public static final String[] MUTATION_DESC = {
            "El espadachín que sobrevivió a la prueba. Su acero no conoce rival.",
            "Sus flechas caen del cielo como una tormenta de estrellas.",
            "Ha mirado al abismo y ahora el abismo obedece su maná.",
            "El asesino que aprendió a levantar a los muertos. Su ejército crece sin fin.",
            "Una fortaleza viviente. Ni el tiempo consigue derribarla."
    };
    public static final String[][] MUTATION_BONUS = {
            {"+2 de daño de ataque", "+0.2 de velocidad de ataque", "+5% de robo de vida", "+25% de daño de habilidad"},
            {"+25% de daño con flechas", "+10% de velocidad", "+25% de daño de habilidad"},
            {"+40 de maná máximo", "-20% de enfriamiento", "+25% de daño de habilidad"},
            {"+3 sombras en tu ejército", "+10% de probabilidad de levantar sombras", "+20% de poder de las sombras",
                    "+25% de daño de habilidad"},
            {"+4 corazones de vida", "+3 de armadura y +2 de dureza", "Devuelves el 10% del daño", "+25% de daño de habilidad"}
    };
    public static final String[] MUT_SKILL_NAME = {"Tormenta de acero", "Lluvia celestial", "Lanza del abismo",
            "Dominio de las sombras", "Muralla eterna"};
    public static final String[] MUT_SKILL_DESC = {
            "Tajo enorme a tu alrededor con 2.2 veces el daño de tu arma. Ralentiza a los enemigos y te da Fuerza.",
            "Abanico de flechas críticas y, además, una lluvia de flechas cae del cielo donde miras.",
            "Rayo de 32 bloques que estalla al final: daño en área y Marchitamiento. Escala con INT.",
            "Invisible y más fuerte. Tus sombras guardadas salen gratis y reciben Fuerza II y Velocidad II.",
            "Resistencia III, Absorción IV y te curas un 30%. Todos los enemigos cercanos te atacan a ti."
    };

    /** Nombre de la clase (o de su mutación). */
    public static String className(int cls, boolean mutated) {
        if (cls < 0 || cls >= CLASS_NAME.length) return "?";
        return mutated ? MUTATION_NAME[cls] : CLASS_NAME[cls];
    }

    /** Nombre de la habilidad de la clase (o de su mutación). */
    public static String skillName(int cls, boolean mutated) {
        if (cls < 0 || cls >= SKILL_NAME.length) return "?";
        return mutated ? MUT_SKILL_NAME[cls] : SKILL_NAME[cls];
    }

    public static String skillDesc(int cls, boolean mutated) {
        if (cls < 0 || cls >= SKILL_DESC.length) return "";
        return mutated ? MUT_SKILL_DESC[cls] : SKILL_DESC[cls];
    }
}
