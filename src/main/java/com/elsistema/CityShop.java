package com.elsistema;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/**
 * Tiendas físicas de la ciudad (sólo se compra hablando con el tendero): la alquimia de Selene (pociones por oro),
 * el mercader de esencias de Ezequiel (cosas raras por esencias y oro por esencias) y, desde la 1.9.0, el Comedor de
 * Tomás, el Centro Médico de Irene, la Tienda 24 h de Paco, la Estación de Puertas de Hugo y la Arena de Vera.
 */
public final class CityShop {
    private CityShop() {}

    /** Una oferta: nombre, descripción, precio y si se paga en esencias (si no, en oro). */
    public record Offer(String name, String desc, int price, boolean essences) {}

    public static final Offer[] ALCHEMY = {
            new Offer("Poción de curación II (x2)", "Cura 8 corazones al momento", 300, false),
            new Offer("Poción de fuerza", "Fuerza durante 8 minutos", 400, false),
            new Offer("Poción de velocidad", "Velocidad durante 8 minutos", 300, false),
            new Offer("Poción contra el fuego", "Inmune al fuego 8 minutos", 350, false),
            new Offer("Elixir de maná", "Te llena el maná al momento", 250, false),
            new Offer("Tónico del cazador", "Regeneración II y Absorción II durante 1 minuto", 600, false)};

    public static final Offer[] ESSENCE = {
            new Offer("Elixir de estadística", "+1 punto de estadística para siempre", 50, true),
            new Offer("Runa de habilidad", "Un punto de habilidad para tus árboles", 35, true),
            new Offer("Pergamino de sombra", "+1 sombra en tu ejército (máx. 5 de estos)", 120, true),
            new Offer("Llave de mazmorra C", "Abre una puerta de rango C", 25, true),
            new Offer("Llave de mazmorra A", "Abre una puerta de rango A", 60, true),
            new Offer("Cambio: 2.000 de oro", "Te da 10 esencias a cambio de 2.000 de oro", 2000, false)};

    // 1.9.0: el Comedor de Tomás, el Centro Médico de Irene, la Tienda 24 h de Paco, la Estación de Puertas de Hugo
    // y la Arena de Vera
    public static final Offer[] FOOD = {
            new Offer("Menú del cazador", "8 filetes asados: para una mazmorra entera", 120, false),
            new Offer("Pan recién hecho (x16)", "El de siempre, recién salido del horno", 80, false),
            new Offer("Estofado de conejo (x3)", "Llena muchísimo y sabe a gloria", 150, false),
            new Offer("Tarta del Comedor", "Para celebrar un ascenso de rango", 200, false),
            new Offer("Manzanas doradas (x2)", "Absorción y regeneración al momento", 900, false),
            new Offer("Banquete del cazador", "Saciedad, Fuerza I 3 min y Regeneración I 1 min", 500, false)};

    public static final Offer[] CLINIC = {
            new Offer("Cura completa", "Te deja la vida al máximo y te apaga el fuego", 150, false),
            new Offer("Limpieza", "Te quita venenos, debilidad, lentitud y demás efectos malos", 120, false),
            new Offer("Revisión completa", "Vida y maná al máximo, sin efectos malos y Resistencia I 5 min", 400, false),
            new Offer("Tótem de la inmortalidad", "Te salva de la muerte una vez", 8000, false)};

    public static final Offer[] STORE = {
            new Offer("Antorchas (x32)", "Para las cuevas más oscuras", 60, false),
            new Offer("Flechas (x32)", "Para tu arco o tu ballesta", 120, false),
            new Offer("Perlas de ender (x4)", "Para escapar a tiempo", 400, false),
            new Offer("Cohetes (x16)", "Para volar con élitros", 300, false),
            new Offer("Botellas de experiencia (x8)", "Experiencia de Minecraft para encantar", 500, false),
            new Offer("Mapa y brújula", "Para no perderte fuera de la ciudad", 90, false)};

    /** Tarifa de la Asociación por abrir una puerta en la estación (por rango E, D, C, B, A y S). */
    public static final int[] GATE_FEE = {150, 400, 900, 2000, 4500, 10000};

    public static final Offer[] GATES = new Offer[6];
    public static final Offer[] ARENA = new Offer[6];

    static {
        for (int r = 0; r < 6; r++) {
            GATES[r] = new Offer("Puerta de rango " + Defs.RANK[r], "Hugo la abre aquí mismo · necesitas rango " + Defs.RANK[r], GATE_FEE[r], false);
            ARENA[r] = new Offer("Combate de rango " + Defs.RANK[r], "3 oleadas · premio: " + Arena.PRIZE_COINS[r] + " de oro y ◆ "
                    + Arena.PRIZE_ESS[r] + " · necesitas rango " + Defs.RANK[r], Arena.FEE[r], false);
        }
    }

    public static Offer[] offers(int role) {
        return switch (role) {
            case CityNpc.ALCHEMIST -> ALCHEMY;
            case CityNpc.MERCHANT -> ESSENCE;
            case CityNpc.COOK -> FOOD;
            case CityNpc.HEALER -> CLINIC;
            case CityNpc.SHOPKEEPER -> STORE;
            case CityNpc.GATEKEEPER -> GATES;
            case CityNpc.TRAINER -> ARENA;
            default -> new Offer[0];
        };
    }

    /** ¿Hace falta un rango de cazador para esta oferta? Devuelve el rango (o -1). */
    public static int rankNeeded(int role, int i) {
        return role == CityNpc.GATEKEEPER || role == CityNpc.TRAINER ? i : -1;
    }

    /** ACT_CITY_BUY: a = tendero, b = oferta. */
    public static void buy(ServerPlayer p, int role, int i) {
        Data d = Store.get(p);
        Offer[] list = offers(role);
        if (d.awakened != 1 || i < 0 || i >= list.length) return;
        if (!City.nearRole(p, role)) {
            SystemLogic.notify(p, Net.N_FORGE_FAIL, 3, role);
            return;
        }
        Offer o = list[i];
        boolean creative = p.isCreative();
        if (role == CityNpc.MERCHANT && i == 2 && d.scrolls >= 5) {
            SystemLogic.notify(p, Net.N_CITY_BUY_FAIL, 2, 0);
            return;
        }
        int need = rankNeeded(role, i);
        if (need >= 0 && d.rank() < need && !creative) {
            SystemLogic.notify(p, Net.N_CITY_BUY_FAIL, 3, need);
            return;
        }
        if (role == CityNpc.GATEKEEPER && Dungeons.gateNear((net.minecraft.server.level.ServerLevel) p.level(), gatePos(), 4.0)) {
            SystemLogic.notify(p, Net.N_CITY_BUY_FAIL, 4, 0);
            return;
        }
        if (role == CityNpc.TRAINER && Arena.busy()) {
            SystemLogic.notify(p, Net.N_CITY_BUY_FAIL, 5, 0);
            return;
        }
        if (o.essences() ? d.essences < o.price() && !creative : d.coins < o.price() && !creative) {
            SystemLogic.notify(p, Net.N_CITY_BUY_FAIL, o.essences() ? 1 : 0, o.price());
            return;
        }
        if (!creative) {
            if (o.essences()) d.essences -= o.price();
            else d.coins -= o.price();
        }
        if (!give(p, d, role, i)) {
            // no se pudo (la puerta no se abrió o la arena se llenó justo ahora): se devuelve el dinero
            if (!creative) {
                if (o.essences()) d.essences += o.price();
                else d.coins += o.price();
            }
            SystemLogic.sync(p, d);
            return;
        }
        p.level().playSound((Player) null, p.getX(), p.getY(), p.getZ(), SoundEvents.VILLAGER_TRADE, SoundSource.PLAYERS, 0.8F, 1.1F);
        SystemLogic.notify(p, Net.N_CITY_BUY, role, i);
        SystemLogic.sync(p, d);
    }

    /** Dónde abre Hugo las puertas: delante del anillo de la Estación de Puertas. */
    public static net.minecraft.core.BlockPos gatePos() {
        return new net.minecraft.core.BlockPos(CityBuilder.GATE_X, City.G, CityBuilder.GATE_Z);
    }

    private static boolean give(ServerPlayer p, Data d, int role, int i) {
        switch (role) {
            case CityNpc.COOK -> {
                switch (i) {
                    case 0 -> Shop.give(p, new ItemStack(Items.COOKED_BEEF, 8));
                    case 1 -> Shop.give(p, new ItemStack(Items.BREAD, 16));
                    case 2 -> {
                        for (int k = 0; k < 3; k++) Shop.give(p, new ItemStack(Items.RABBIT_STEW));
                    }
                    case 3 -> Shop.give(p, new ItemStack(Items.CAKE));
                    case 4 -> Shop.give(p, new ItemStack(Items.GOLDEN_APPLE, 2));
                    default -> {
                        p.addEffect(new MobEffectInstance(MobEffects.SATURATION, 100, 0));
                        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 3600, 0));
                        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 1200, 0));
                    }
                }
                return true;
            }
            case CityNpc.HEALER -> {
                if (i == 3) {
                    Shop.give(p, new ItemStack(Items.TOTEM_OF_UNDYING));
                    return true;
                }
                if (i == 0 || i == 2) {
                    p.setHealth(p.getMaxHealth());
                    p.clearFire();
                }
                if (i == 1 || i == 2) {
                    for (MobEffectInstance e : new java.util.ArrayList<>(p.getActiveEffects())) {
                        if (e.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
                            p.removeEffect(e.getEffect());
                        }
                    }
                }
                if (i == 2) {
                    d.mp = SystemLogic.maxMp(d);
                    p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 6000, 0));
                }
                Fx.send((net.minecraft.server.level.ServerLevel) p.level(), net.minecraft.core.particles.ParticleTypes.HEART,
                        p.getX(), p.getY() + 1.2, p.getZ(), 8, 0.4, 0.4, 0.4, 0.05);
                return true;
            }
            case CityNpc.SHOPKEEPER -> {
                switch (i) {
                    case 0 -> Shop.give(p, new ItemStack(Items.TORCH, 32));
                    case 1 -> Shop.give(p, new ItemStack(Items.ARROW, 32));
                    case 2 -> Shop.give(p, new ItemStack(Items.ENDER_PEARL, 4));
                    case 3 -> Shop.give(p, new ItemStack(Items.FIREWORK_ROCKET, 16));
                    case 4 -> Shop.give(p, new ItemStack(Items.EXPERIENCE_BOTTLE, 8));
                    default -> {
                        Shop.give(p, new ItemStack(Items.MAP));
                        Shop.give(p, new ItemStack(Items.COMPASS));
                    }
                }
                return true;
            }
            case CityNpc.GATEKEEPER -> {
                return Dungeons.openGateAt(p, gatePos(), i, -1);
            }
            case CityNpc.TRAINER -> {
                return Arena.start(p, i);
            }
            default -> {
            }
        }
        if (role == CityNpc.ALCHEMIST) {
            switch (i) {
                case 0 -> {
                    Shop.give(p, PotionContents.createItemStack(Items.POTION, Potions.STRONG_HEALING));
                    Shop.give(p, PotionContents.createItemStack(Items.POTION, Potions.STRONG_HEALING));
                }
                case 1 -> Shop.give(p, PotionContents.createItemStack(Items.POTION, Potions.LONG_STRENGTH));
                case 2 -> Shop.give(p, PotionContents.createItemStack(Items.POTION, Potions.LONG_SWIFTNESS));
                case 3 -> Shop.give(p, PotionContents.createItemStack(Items.POTION, Potions.LONG_FIRE_RESISTANCE));
                case 4 -> d.mp = SystemLogic.maxMp(d);
                case 5 -> {
                    p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 1200, 1));
                    p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1200, 1));
                }
                default -> {
                }
            }
            return true;
        }
        switch (i) {
            case 0 -> d.points++;
            case 1 -> Shop.give(p, new ItemStack(ModItems.RUNE));
            case 2 -> {
                d.bonusSlots++;
                d.scrolls++;
            }
            case 3 -> Shop.give(p, new ItemStack(ModItems.KEYS[2]));
            case 4 -> Shop.give(p, new ItemStack(ModItems.KEYS[4]));
            case 5 -> SystemLogic.addEssences(p, 10);
            default -> {
            }
        }
        return true;
    }
}
