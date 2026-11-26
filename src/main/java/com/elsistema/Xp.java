package com.elsistema;

import java.util.Map;

/** EXP que da cada criatura al morir: cuanto más peligrosa, más EXP. */
public final class Xp {
    private Xp() {}

    private static final Map<String, Integer> MOBS = Map.ofEntries(
            Map.entry("zombie", 10), Map.entry("husk", 10), Map.entry("drowned", 10), Map.entry("zombie_villager", 10),
            Map.entry("skeleton", 10), Map.entry("stray", 12), Map.entry("bogged", 12),
            Map.entry("spider", 10), Map.entry("cave_spider", 12), Map.entry("silverfish", 5), Map.entry("endermite", 6),
            Map.entry("slime", 6), Map.entry("magma_cube", 8), Map.entry("creeper", 12), Map.entry("witch", 18),
            Map.entry("phantom", 12), Map.entry("pillager", 14), Map.entry("vindicator", 22), Map.entry("vex", 10),
            Map.entry("evoker", 45), Map.entry("illusioner", 45), Map.entry("ravager", 60),
            Map.entry("enderman", 22), Map.entry("blaze", 18), Map.entry("ghast", 22), Map.entry("wither_skeleton", 28),
            Map.entry("zombified_piglin", 10), Map.entry("piglin", 10), Map.entry("piglin_brute", 35),
            Map.entry("hoglin", 18), Map.entry("zoglin", 22), Map.entry("guardian", 18), Map.entry("shulker", 25),
            Map.entry("breeze", 30), Map.entry("elder_guardian", 200), Map.entry("warden", 600),
            Map.entry("wither", 1000), Map.entry("ender_dragon", 2500),
            Map.entry("goblin", 12), Map.entry("lobo_acero", 16), Map.entry("oso_hielo", 28), Map.entry("orco", 26),
            Map.entry("hormiga", 30), Map.entry("caballero", 40), Map.entry("coloso", 60));

    /** EXP de una criatura. Los monstruos que no están en la lista dan 10 y los animales 1. */
    public static int forMob(String id, boolean hostile) {
        Integer v = MOBS.get(id);
        if (v != null) return v;
        return hostile ? 10 : 1;
    }
}
