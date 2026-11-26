package com.elsistema;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/** Objetos del Sistema: núcleos, llaves, runas, armas, armaduras y consumibles exclusivos de la Tienda. */
public final class ModItems {
    private ModItems() {}

    public static final Item[] CORES = new Item[6];
    public static final Item[] KEYS = new Item[6];
    public static Item RUNE;
    public static Item DAGA;
    public static Item ESPADA;
    public static Item HACHA;
    public static Item GUADANA;
    public static Item BACULO;
    public static Item POCION_MANA;
    public static Item ELIXIR;
    public static Item PIEDRA_RETORNO;
    public static Item VEIL;
    /** Armaduras por set (cazador, archimago, caballero negro, monarca) y pieza (casco, peto, grebas, botas). */
    public static final Item[][] ARMOR = new Item[4][4];
    private static final String[][] ARMOR_ID = {
            {"cazador_casco", "cazador_peto", "cazador_grebas", "cazador_botas"},
            {"archimago_capucha", "archimago_tunica", "archimago_faldones", "archimago_sandalias"},
            {"caballero_yelmo", "caballero_coraza", "caballero_quijotes", "caballero_escarpes"},
            {"monarca_corona", "monarca_manto", "monarca_grebas", "monarca_botas"}};
    private static final int[] ARMOR_DURABILITY = {25, 22, 35, 40};

    /** Oro que da cada núcleo al venderlo en la tienda. */
    public static final int[] CORE_VALUE = {10, 30, 80, 200, 500, 1500};
    private static final String[] R = {"e", "d", "c", "b", "a", "s"};

    public static void register() {
        for (int i = 0; i < 6; i++) {
            CORES[i] = reg("nucleo_" + R[i], new CoreItem(i, props(i)));
            KEYS[i] = reg("llave_" + R[i], new KeyItem(i, props(i).stacksTo(16)));
        }
        RUNE = reg("runa_habilidad", new RuneItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(16)));
        ModArmor.register();
        DAGA = reg("daga_asesino", new SystemSword(ModArmor.Tiers.CAZADOR, 1, -1.4F, Rarity.UNCOMMON, "Golpes muy rápidos"));
        ESPADA = reg("espada_caballero", new SystemSword(ModArmor.Tiers.SOMBRIO, 3, -2.4F, Rarity.RARE, "Hoja forjada en la oscuridad"));
        HACHA = reg("hacha_berserker", new SystemSword(ModArmor.Tiers.SOMBRIO, 6, -3.0F, Rarity.RARE, "Golpes lentos pero devastadores"));
        GUADANA = reg("guadana_monarca", new SystemSword(ModArmor.Tiers.MONARCA, 5, -2.6F, Rarity.EPIC, "Tus sombras atacan a quien golpeas"));
        BACULO = reg("baculo_arcano", new StaffItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
        POCION_MANA = reg("pocion_mana", new ConsumableItem(0, new Item.Properties().stacksTo(16)));
        ELIXIR = reg("elixir_vida", new ConsumableItem(1, new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
        PIEDRA_RETORNO = reg("piedra_retorno", new ConsumableItem(2, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
        VEIL = reg("velo_sombra", new Item(new Item.Properties().stacksTo(1)));
        ArmorItem.Type[] types = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};
        for (int set = 0; set < 4; set++) {
            for (int piece = 0; piece < 4; piece++) {
                ARMOR[set][piece] = reg(ARMOR_ID[set][piece], new ModArmor.SetArmorItem(material(set), types[piece], set, ARMOR_DURABILITY[set]));
            }
        }
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register(entries -> {
            for (Item c : CORES) {
                entries.accept(c);
            }
            for (Item k : KEYS) {
                entries.accept(k);
            }
            entries.accept(RUNE);
        });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> {
            entries.accept(DAGA);
            entries.accept(ESPADA);
            entries.accept(HACHA);
            entries.accept(GUADANA);
            entries.accept(BACULO);
            for (Item[] set : ARMOR) {
                for (Item piece : set) {
                    entries.accept(piece);
                }
            }
        });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(entries -> {
            entries.accept(POCION_MANA);
            entries.accept(ELIXIR);
        });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> entries.accept(PIEDRA_RETORNO));
    }

    private static net.minecraft.core.Holder<net.minecraft.world.item.ArmorMaterial> material(int set) {
        return switch (set) {
            case 0 -> ModArmor.CAZADOR;
            case 1 -> ModArmor.ARCHIMAGO;
            case 2 -> ModArmor.CABALLERO;
            default -> ModArmor.MONARCA;
        };
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

    /** Armas exclusivas de la Tienda del Sistema. */
    public static class SystemSword extends SwordItem {
        private final String desc;

        public SystemSword(Tier tier, int damage, float speed, Rarity rarity, String desc) {
            super(tier, new Item.Properties().attributes(SwordItem.createAttributes(tier, damage, speed)).rarity(rarity));
            this.desc = desc;
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.literal(this.desc).withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal("Exclusiva de la Tienda del Sistema").withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    /** Báculo arcano: clic derecho lanza un rayo arcano (8 de maná). */
    public static class StaffItem extends Item {
        public static final int COST = 8;

        public StaffItem(Item.Properties p) {
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
                if (d.mp < COST && !sp.isCreative()) {
                    SystemLogic.notify(sp, Net.N_NO_MP, COST, (int) Math.floor(d.mp));
                    return InteractionResultHolder.fail(stack);
                }
                if (!sp.isCreative()) d.mp -= COST;
                Skills.bolt(sp, d);
                sp.getCooldowns().addCooldown(this, 12);
                SystemLogic.sync(sp, d);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.literal("Clic derecho: rayo arcano (" + COST + " de maná)").withStyle(ChatFormatting.AQUA));
            lines.add(Component.literal("Más daño con más INTELIGENCIA").withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal("Exclusivo de la Tienda del Sistema").withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    /** Poción de maná (0), elixir de vida (1) y piedra de retorno (2): se usan con clic derecho. */
    public static class ConsumableItem extends Item {
        private final int kind;

        public ConsumableItem(int kind, Item.Properties p) {
            super(p);
            this.kind = kind;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide() && player instanceof ServerPlayer sp) {
                Data d = Store.get(sp);
                switch (this.kind) {
                    case 0 -> {
                        int max = SystemLogic.maxMp(d);
                        int got = (int) Math.min(60.0, Math.max(0.0, max - d.mp));
                        d.mp = Math.min(max, d.mp + 60.0);
                        SystemLogic.notify(sp, Net.N_MANA, got, 0);
                        level.playSound((Player) null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0F, 1.2F);
                        ((ServerLevel) level).sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                                sp.getX(), sp.getY() + 1.0, sp.getZ(), 30, 0.4, 0.6, 0.4, 0.4);
                    }
                    case 1 -> {
                        sp.setHealth(sp.getMaxHealth());
                        sp.removeEffect(MobEffects.POISON);
                        sp.removeEffect(MobEffects.WITHER);
                        sp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
                        level.playSound((Player) null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0F, 0.9F);
                        ((ServerLevel) level).sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,
                                sp.getX(), sp.getY() + 1.2, sp.getZ(), 10, 0.5, 0.5, 0.5, 0.1);
                    }
                    default -> {
                        if (!returnHome(sp)) return InteractionResultHolder.fail(stack);
                    }
                }
                if (!sp.isCreative()) stack.shrink(1);
                SystemLogic.sync(sp, d);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
            String t = switch (this.kind) {
                case 0 -> "Clic derecho: recuperas 60 de maná";
                case 1 -> "Clic derecho: vida al máximo, quita venenos y Regeneración II";
                default -> "Clic derecho: vuelves a tu cama (o al inicio del mundo). Sirve para salir de una mazmorra";
            };
            lines.add(Component.literal(t).withStyle(ChatFormatting.GRAY));
        }
    }

    /** Piedra de retorno: sale de la mazmorra o te lleva a tu cama / al inicio del mundo. */
    public static boolean returnHome(ServerPlayer sp) {
        if (Dungeons.leaveNow(sp)) {
            SystemLogic.notify(sp, Net.N_RETURN, 1, 0);
            return true;
        }
        if (sp.getServer() == null) return false;
        ServerLevel target = sp.getServer().getLevel(sp.getRespawnDimension());
        BlockPos pos = sp.getRespawnPosition();
        double y;
        if (target == null || pos == null) {
            target = sp.getServer().overworld();
            pos = target.getSharedSpawnPos();
            y = target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        } else {
            y = pos.getY() + 0.6;
        }
        ((ServerLevel) sp.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                sp.getX(), sp.getY() + 1.0, sp.getZ(), 50, 0.4, 0.8, 0.4, 0.1);
        sp.teleportTo(target, pos.getX() + 0.5, y, pos.getZ() + 0.5, sp.getYRot(), sp.getXRot());
        target.playSound((Player) null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        SystemLogic.notify(sp, Net.N_RETURN, 0, 0);
        Shadows.recall(sp);
        return true;
    }
}
