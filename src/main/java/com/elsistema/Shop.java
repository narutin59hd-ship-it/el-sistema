package com.elsistema;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.List;

/** Tienda del Sistema: comprar con oro y vender núcleos de maná. */
public final class Shop {
    private Shop() {}

    public static final String[] NAME = {
            "Pan x8", "Filete cocinado x8", "Poción de curación", "Poción de fuerza",
            "Manzana dorada", "Flechas x32", "Armadura de hierro", "Espada de diamante",
            "Armadura de diamante", "Tótem de inmortalidad", "Manzana encantada", "Runa de habilidad",
            "Llave de mazmorra E", "Llave de mazmorra D", "Llave de mazmorra C",
            "Llave de mazmorra B", "Llave de mazmorra A", "Llave de mazmorra S"};

    public static final int[] PRICE = {
            20, 45, 60, 120,
            150, 40, 400, 600,
            2500, 3000, 6000, 2500,
            120, 350, 900, 2200, 5500, 14000};

    public static List<ItemStack> make(int i) {
        return switch (i) {
            case 0 -> List.of(new ItemStack(Items.BREAD, 8));
            case 1 -> List.of(new ItemStack(Items.COOKED_BEEF, 8));
            case 2 -> List.of(PotionContents.createItemStack(Items.POTION, Potions.HEALING));
            case 3 -> List.of(PotionContents.createItemStack(Items.POTION, Potions.STRENGTH));
            case 4 -> List.of(new ItemStack(Items.GOLDEN_APPLE));
            case 5 -> List.of(new ItemStack(Items.ARROW, 32));
            case 6 -> List.of(new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE),
                    new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS));
            case 7 -> List.of(new ItemStack(Items.DIAMOND_SWORD));
            case 8 -> List.of(new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
                    new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS));
            case 9 -> List.of(new ItemStack(Items.TOTEM_OF_UNDYING));
            case 10 -> List.of(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
            case 11 -> List.of(new ItemStack(ModItems.RUNE));
            case 12, 13, 14, 15, 16, 17 -> List.of(new ItemStack(ModItems.KEYS[i - 12]));
            default -> List.of();
        };
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
        if (d.awakened != 1 || i < 0 || i >= PRICE.length) return;
        int n = Math.max(1, Math.min(5, count));
        long total = (long) PRICE[i] * n;
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
