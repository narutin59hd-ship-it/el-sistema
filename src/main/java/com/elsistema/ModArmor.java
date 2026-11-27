package com.elsistema;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;

/** Materiales de las armas y armaduras exclusivas de la Tienda del Sistema. */
public final class ModArmor {
    private ModArmor() {}

    public static final int SET_CAZADOR = 0;
    public static final int SET_ARCHIMAGO = 1;
    public static final int SET_CABALLERO = 2;
    public static final int SET_MONARCA = 3;
    public static final String[] SET_NAME = {"cazador", "archimago", "caballero_negro", "monarca"};
    public static final String[] SET_BONUS = {
            "Velocidad I",
            "+40 de maná máximo y +50% de regeneración de maná",
            "Resistencia I",
            "+3 sombras en tu ejército y +15% de poder de las sombras"};

    /** Armas: usos, velocidad, daño extra, encantabilidad. */
    public enum Tiers implements Tier {
        CAZADOR(900, 8.0F, 3.0F, 15, () -> Ingredient.of(Items.IRON_INGOT)),
        SOMBRIO(1800, 9.0F, 5.0F, 18, () -> Ingredient.of(Items.DIAMOND)),
        MONARCA(3200, 10.0F, 7.0F, 22, () -> Ingredient.of(Items.NETHERITE_INGOT)),
        /** Armas del Monarca (recompensa del prestigio): indestructibles. */
        SOBERANO(4000, 12.0F, 10.0F, 25, () -> Ingredient.of(Items.NETHERITE_INGOT));

        private final int uses;
        private final float speed;
        private final float damage;
        private final int enchant;
        private final Supplier<Ingredient> repair;

        Tiers(int uses, float speed, float damage, int enchant, Supplier<Ingredient> repair) {
            this.uses = uses;
            this.speed = speed;
            this.damage = damage;
            this.enchant = enchant;
            this.repair = repair;
        }

        @Override
        public int getUses() {
            return this.uses;
        }

        @Override
        public float getSpeed() {
            return this.speed;
        }

        @Override
        public float getAttackDamageBonus() {
            return this.damage;
        }

        @Override
        public TagKey<Block> getIncorrectBlocksForDrops() {
            return BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
        }

        @Override
        public int getEnchantmentValue() {
            return this.enchant;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return this.repair.get();
        }
    }

    public static Holder<ArmorMaterial> CAZADOR;
    public static Holder<ArmorMaterial> ARCHIMAGO;
    public static Holder<ArmorMaterial> CABALLERO;
    public static Holder<ArmorMaterial> MONARCA;

    /** defensa: botas, grebas, peto, casco. */
    private static Holder<ArmorMaterial> material(String name, int[] def, int enchant, float toughness, float kb,
                                                  Supplier<Ingredient> repair) {
        EnumMap<ArmorItem.Type, Integer> map = new EnumMap<>(ArmorItem.Type.class);
        map.put(ArmorItem.Type.BOOTS, def[0]);
        map.put(ArmorItem.Type.LEGGINGS, def[1]);
        map.put(ArmorItem.Type.CHESTPLATE, def[2]);
        map.put(ArmorItem.Type.HELMET, def[3]);
        map.put(ArmorItem.Type.BODY, def[2]);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, name);
        return Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL, id, new ArmorMaterial(map, enchant,
                SoundEvents.ARMOR_EQUIP_NETHERITE, repair, List.of(new ArmorMaterial.Layer(id)), toughness, kb));
    }

    public static void register() {
        CAZADOR = material("cazador", new int[]{2, 5, 6, 2}, 12, 1.0F, 0.0F, () -> Ingredient.of(Items.IRON_INGOT));
        ARCHIMAGO = material("archimago", new int[]{2, 4, 5, 2}, 25, 0.0F, 0.0F, () -> Ingredient.of(Items.LAPIS_LAZULI));
        CABALLERO = material("caballero_negro", new int[]{3, 6, 8, 3}, 15, 3.0F, 0.1F, () -> Ingredient.of(Items.DIAMOND));
        MONARCA = material("monarca", new int[]{4, 7, 9, 4}, 20, 4.0F, 0.15F, () -> Ingredient.of(Items.NETHERITE_INGOT));
    }

    /** Armadura del Sistema con bonus de set. */
    public static class SetArmorItem extends ArmorItem {
        public final int set;

        public SetArmorItem(Holder<ArmorMaterial> material, ArmorItem.Type type, int set, int durability) {
            super(material, type, new Item.Properties().durability(type.getDurability(durability)));
            this.set = set;
        }

        @Override
        public void appendHoverText(net.minecraft.world.item.ItemStack stack, Item.TooltipContext context,
                                    List<net.minecraft.network.chat.Component> lines, net.minecraft.world.item.TooltipFlag flag) {
            lines.add(net.minecraft.network.chat.Component.literal("Exclusivo de la Tienda del Sistema")
                    .withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
            lines.add(net.minecraft.network.chat.Component.literal("Set completo (4 piezas): " + SET_BONUS[this.set])
                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** Devuelve el set que lleva completo el Jugador o -1. */
    public static int fullSet(Player p) {
        int set = -1;
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot s : slots) {
            Item it = p.getItemBySlot(s).getItem();
            if (!(it instanceof SetArmorItem a)) return -1;
            if (set == -1) {
                set = a.set;
            } else if (set != a.set) {
                return -1;
            }
        }
        return set;
    }
}
