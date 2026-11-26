package com.elsistema;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Objetos del Sistema: núcleos de maná, llaves de mazmorra y runas de habilidad. */
public final class ModItems {
    private ModItems() {}

    public static final Item[] CORES = new Item[6];
    public static final Item[] KEYS = new Item[6];
    public static Item RUNE;

    /** Oro que da cada núcleo al venderlo en la tienda. */
    public static final int[] CORE_VALUE = {10, 30, 80, 200, 500, 1500};
    private static final String[] R = {"e", "d", "c", "b", "a", "s"};

    public static void register() {
        for (int i = 0; i < 6; i++) {
            CORES[i] = reg("nucleo_" + R[i], new CoreItem(i, props(i)));
            KEYS[i] = reg("llave_" + R[i], new KeyItem(i, props(i).stacksTo(16)));
        }
        RUNE = reg("runa_habilidad", new RuneItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(16)));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register(entries -> {
            for (Item c : CORES) {
                entries.accept(c);
            }
            for (Item k : KEYS) {
                entries.accept(k);
            }
            entries.accept(RUNE);
        });
    }

    private static Item.Properties props(int rank) {
        Rarity r = rank >= 4 ? Rarity.EPIC : rank >= 2 ? Rarity.RARE : rank >= 1 ? Rarity.UNCOMMON : Rarity.COMMON;
        return new Item.Properties().rarity(r);
    }

    private static Item reg(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, name), item);
    }

    public static int coreRank(ItemStack s) {
        for (int i = 0; i < 6; i++) {
            if (CORES[i] != null && s.is(CORES[i])) return i;
        }
        return -1;
    }

    /** Núcleo de maná: se vende en la tienda. */
    public static class CoreItem extends Item {
        private final int rank;

        public CoreItem(int rank, Item.Properties p) {
            super(p);
            this.rank = rank;
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.literal("Cristal de maná de un monstruo de rango " + Defs.RANK[rank] + ".").withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal("Véndelo en la Tienda del Sistema: " + CORE_VALUE[rank] + " de oro.").withStyle(ChatFormatting.GOLD));
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return rank >= 4;
        }
    }

    /** Llave de mazmorra: abre una puerta de su rango delante de ti. */
    public static class KeyItem extends Item {
        private final int rank;

        public KeyItem(int rank, Item.Properties p) {
            super(p);
            this.rank = rank;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide() && player instanceof ServerPlayer sp) {
                if (!Dungeons.openGate(sp, rank, -1, true)) {
                    return InteractionResultHolder.fail(stack);
                }
                if (!sp.isCreative()) stack.shrink(1);
            }
            return InteractionResultHolder.success(stack);
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.literal("Abre una puerta de mazmorra de rango " + Defs.RANK[rank] + ".").withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal("Nivel de los monstruos: " + (rank == 0 ? 1 : Defs.RANK_MAX[rank - 1] + 1) + "-" + Defs.RANK_MAX[rank]).withStyle(ChatFormatting.DARK_AQUA));
            lines.add(Component.literal("Clic derecho para usarla.").withStyle(ChatFormatting.YELLOW));
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return true;
        }
    }

    /** Runa de habilidad: +1 punto de habilidad. */
    public static class RuneItem extends Item {
        public RuneItem(Item.Properties p) {
            super(p);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide() && player instanceof ServerPlayer sp) {
                Data d = Store.get(sp);
                if (d.awakened != 1) {
                    SystemLogic.notify(sp, Net.N_NOT_AWAKENED, 0, 0);
                    return InteractionResultHolder.fail(stack);
                }
                d.skillPoints++;
                if (!sp.isCreative()) stack.shrink(1);
                SystemLogic.notify(sp, Net.N_RUNE, d.skillPoints, 0);
                SystemLogic.sync(sp, d);
            }
            return InteractionResultHolder.success(stack);
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.literal("Contiene un fragmento del poder del Sistema.").withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal("Clic derecho: +1 punto de habilidad.").withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return true;
        }
    }
}
