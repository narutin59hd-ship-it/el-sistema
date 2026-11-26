package com.elsistema;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Tienda del Sistema: comprar con oro (por categorías y rango) y vender núcleos de maná. */
public final class Shop {
    private Shop() {}

    public static final String[] CATS = {"CONSUMIBLES", "ARMAS", "ARMADURAS", "MAZMORRAS"};

    /** nombre, precio, categoría, rango mínimo (0 = E), descripción y lo que se recibe. */
    public record Offer(String name, int price, int cat, int minRank, String desc, Supplier<List<ItemStack>> make) {}

    private static Supplier<List<ItemStack>> one(Supplier<Item> item, int count) {
        return () -> List.of(new ItemStack(item.get(), count));
    }

    private static Supplier<List<ItemStack>> set(int s) {
        return () -> {
            List<ItemStack> l = new ArrayList<>();
            for (Item piece : ModItems.ARMOR[s]) {
                l.add(new ItemStack(piece));
            }
            return l;
        };
    }

    public static final List<Offer> OFFERS = List.of(
            // Consumibles
            new Offer("Pan x8", 20, 0, 0, "Comida básica", one(() -> Items.BREAD, 8)),
            new Offer("Filete cocinado x8", 45, 0, 0, "Comida que llena mucho", one(() -> Items.COOKED_BEEF, 8)),
            new Offer("Poción de maná", 80, 0, 0, "Recupera 60 de maná al instante", one(() -> ModItems.POCION_MANA, 1)),
            new Offer("Poción de curación", 60, 0, 0, "Cura 4 corazones",
                    () -> List.of(PotionContents.createItemStack(Items.POTION, Potions.HEALING))),
            new Offer("Elixir de vida", 400, 0, 1, "Vida al máximo, quita venenos y Regeneración II", one(() -> ModItems.ELIXIR, 1)),
            new Offer("Manzana dorada", 150, 0, 0, "Absorción y regeneración", one(() -> Items.GOLDEN_APPLE, 1)),
            new Offer("Piedra de retorno", 250, 0, 0, "Te lleva a tu cama o te saca de una mazmorra", one(() -> ModItems.PIEDRA_RETORNO, 1)),
            new Offer("Tótem de inmortalidad", 3000, 0, 2, "Te salva de la muerte una vez", one(() -> Items.TOTEM_OF_UNDYING, 1)),
            new Offer("Runa de habilidad", 2500, 0, 1, "+1 punto de habilidad", one(() -> ModItems.RUNE, 1)),
            new Offer("Manzana encantada", 6000, 0, 3, "El mejor alimento del juego", one(() -> Items.ENCHANTED_GOLDEN_APPLE, 1)),
            // Armas
            new Offer("Flechas x32", 40, 1, 0, "Munición para arcos", one(() -> Items.ARROW, 32)),
            new Offer("Espada de diamante", 600, 1, 0, "Una buena espada", one(() -> Items.DIAMOND_SWORD, 1)),
            new Offer("Daga del asesino", 1200, 1, 1, "5 de daño · golpes muy rápidos", one(() -> ModItems.DAGA, 1)),
            new Offer("Báculo arcano", 1500, 1, 2, "Clic derecho: rayo arcano (8 de maná)", one(() -> ModItems.BACULO, 1)),
            new Offer("Espada del caballero negro", 4000, 1, 3, "9 de daño", one(() -> ModItems.ESPADA, 1)),
            new Offer("Hacha del berserker", 4500, 1, 3, "12 de daño · lenta", one(() -> ModItems.HACHA, 1)),
            new Offer("Guadaña del Monarca", 15000, 1, 5, "13 de daño · tus sombras atacan a quien golpeas", one(() -> ModItems.GUADANA, 1)),
            // Armaduras
            new Offer("Armadura de hierro", 400, 2, 0, "Las 4 piezas de hierro", () -> List.of(new ItemStack(Items.IRON_HELMET),
                    new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS))),
            new Offer("Armadura de diamante", 2500, 2, 1, "Las 4 piezas de diamante", () -> List.of(new ItemStack(Items.DIAMOND_HELMET),
                    new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS))),
            new Offer("Armadura del cazador", 2000, 2, 2, "Set: " + ModArmor.SET_BONUS[0], set(0)),
            new Offer("Túnica del archimago", 3000, 2, 3, "Set: " + ModArmor.SET_BONUS[1], set(1)),
            new Offer("Armadura del caballero negro", 8000, 2, 4, "Set: " + ModArmor.SET_BONUS[2], set(2)),
            new Offer("Armadura del Monarca", 25000, 2, 5, "Set: " + ModArmor.SET_BONUS[3], set(3)),
            // Mazmorras
            new Offer("Llave de mazmorra E", 120, 3, 0, "Abre una mazmorra de rango E", () -> List.of(new ItemStack(ModItems.KEYS[0]))),
            new Offer("Llave de mazmorra D", 350, 3, 0, "Abre una mazmorra de rango D", () -> List.of(new ItemStack(ModItems.KEYS[1]))),
            new Offer("Llave de mazmorra C", 900, 3, 0, "Abre una mazmorra de rango C", () -> List.of(new ItemStack(ModItems.KEYS[2]))),
            new Offer("Llave de mazmorra B", 2200, 3, 0, "Abre una mazmorra de rango B", () -> List.of(new ItemStack(ModItems.KEYS[3]))),
            new Offer("Llave de mazmorra A", 5500, 3, 0, "Abre una mazmorra de rango A", () -> List.of(new ItemStack(ModItems.KEYS[4]))),
            new Offer("Llave de mazmorra S", 14000, 3, 0, "Abre una mazmorra de rango S", () -> List.of(new ItemStack(ModItems.KEYS[5]))));

    public static List<ItemStack> make(int i) {
        return i >= 0 && i < OFFERS.size() ? OFFERS.get(i).make().get() : List.of();
    }

    public static ItemStack icon(int i) {
        List<ItemStack> l = make(i);
        return l.isEmpty() ? ItemStack.EMPTY : l.get(0);
    }

    public static void give(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack)) {
            p.drop(stack, false);
        }
    }

    public static void buy(ServerPlayer p, int i, int count) {
        Data d = Store.get(p);
        if (d.awakened != 1 || i < 0 || i >= OFFERS.size()) return;
        Offer o = OFFERS.get(i);
        if (Defs.rank(d.level) < o.minRank()) {
            SystemLogic.notify(p, Net.N_RANK_REQ, o.minRank(), 0);
            return;
        }
        int n = Math.max(1, Math.min(5, count));
        long total = (long) o.price() * n;
        if (d.coins < total) {
            SystemLogic.notify(p, Net.N_NO_COINS, (int) Math.min(Integer.MAX_VALUE, total), (int) Math.min(Integer.MAX_VALUE, d.coins));
            return;
        }
        d.coins -= total;
        for (int k = 0; k < n; k++) {
            for (ItemStack s : make(i)) {
                give(p, s);
            }
        }
        p.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7F, 1.2F);
        SystemLogic.notify(p, Net.N_BOUGHT, i, n);
        SystemLogic.sync(p, d);
    }

    /** Vende todos los núcleos de maná del inventario. */
    public static void sellCores(ServerPlayer p) {
        Data d = Store.get(p);
        if (d.awakened != 1) return;
        Inventory inv = p.getInventory();
        int count = 0;
        long total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            int r = ModItems.coreRank(s);
            if (r >= 0) {
                count += s.getCount();
                total += (long) s.getCount() * ModItems.CORE_VALUE[r];
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        if (count == 0) {
            SystemLogic.notify(p, Net.N_SOLD, 0, 0);
            return;
        }
        d.coins += total;
        p.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 0.9F);
        SystemLogic.notify(p, Net.N_SOLD, count, (int) Math.min(Integer.MAX_VALUE, total));
        SystemLogic.sync(p, d);
    }
}
