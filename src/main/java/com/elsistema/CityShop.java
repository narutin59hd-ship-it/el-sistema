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
 * Tiendas físicas de la ciudad (sólo se compra hablando con el tendero): la alquimia de Selene (pociones por oro)
 * y el mercader de esencias de Ezequiel (cosas raras por esencias y oro por esencias).
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

    public static Offer[] offers(int role) {
        return role == CityNpc.ALCHEMIST ? ALCHEMY : role == CityNpc.MERCHANT ? ESSENCE : new Offer[0];
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
        if (o.essences() ? d.essences < o.price() && !creative : d.coins < o.price() && !creative) {
            SystemLogic.notify(p, Net.N_CITY_BUY_FAIL, o.essences() ? 1 : 0, o.price());
            return;
        }
        if (!creative) {
            if (o.essences()) d.essences -= o.price();
            else d.coins -= o.price();
        }
        give(p, d, role, i);
        p.level().playSound((Player) null, p.getX(), p.getY(), p.getZ(), SoundEvents.VILLAGER_TRADE, SoundSource.PLAYERS, 0.8F, 1.1F);
        SystemLogic.notify(p, Net.N_CITY_BUY, role, i);
        SystemLogic.sync(p, d);
    }

    private static void give(ServerPlayer p, Data d, int role, int i) {
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
            return;
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
    }
}
