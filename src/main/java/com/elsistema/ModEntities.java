package com.elsistema;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Registro de los monstruos de mazmorra y sus huevos. */
public final class ModEntities {
    private ModEntities() {}

    private static final Map<EntityType<?>, DungeonMonster.Kind> KINDS = new IdentityHashMap<>();
    public static final List<Item> EGGS = new ArrayList<>();

    public static EntityType<DungeonMonster> GOBLIN;
    public static EntityType<DungeonMonster> LOBO;
    public static EntityType<DungeonMonster> OSO;
    public static EntityType<DungeonMonster> ORCO;
    public static EntityType<DungeonMonster> HORMIGA;
    public static EntityType<DungeonMonster> CABALLERO;
    public static EntityType<DungeonMonster> COLOSO;

    // Jefes secretos
    private static final Map<EntityType<?>, SecretBoss.Kind> SECRET = new IdentityHashMap<>();
    private static final Map<SecretBoss.Kind, EntityType<SecretBoss>> SECRET_TYPES = new java.util.EnumMap<>(SecretBoss.Kind.class);
    public static EntityType<SecretBoss> GUARDIAN;
    public static EntityType<SecretBoss> ECLIPSE;
    public static EntityType<SecretBoss> QUEEN;
    public static EntityType<SecretBoss> PRIMORDIAL;

    private static EntityType<DungeonMonster> reg(String name, DungeonMonster.Kind kind, float w, float h, int bg, int fg) {
        EntityType<DungeonMonster> type = Registry.register(BuiltInRegistries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, name),
                EntityType.Builder.of(DungeonMonster::new, MobCategory.MONSTER).sized(w, h).clientTrackingRange(10).build(name));
        KINDS.put(type, kind);
        FabricDefaultAttributeRegistry.register(type, DungeonMonster.attributes(kind));
        Item egg = Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, name + "_huevo"),
                new SpawnEggItem(type, bg, fg, new Item.Properties()));
        EGGS.add(egg);
        return type;
    }

    private static EntityType<SecretBoss> regBoss(String name, SecretBoss.Kind kind, DungeonMonster.Kind base, float w, float h,
                                                  int bg, int fg) {
        EntityType<SecretBoss> type = Registry.register(BuiltInRegistries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, name),
                EntityType.Builder.of(SecretBoss::new, MobCategory.MONSTER).sized(w, h).fireImmune().clientTrackingRange(12).build(name));
        KINDS.put(type, base);
        SECRET.put(type, kind);
        SECRET_TYPES.put(kind, type);
        FabricDefaultAttributeRegistry.register(type, SecretBoss.attributes(kind));
        Item egg = Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, name + "_huevo"),
                new SpawnEggItem(type, bg, fg, new Item.Properties()));
        EGGS.add(egg);
        return type;
    }

    public static void register() {
        GOBLIN = reg("goblin", DungeonMonster.Kind.GOBLIN, 0.5F, 1.4F, 0x4E7A2A, 0x8B5A2B);
        LOBO = reg("lobo_acero", DungeonMonster.Kind.LOBO, 0.8F, 0.95F, 0x5A5F66, 0xC9CED6);
        OSO = reg("oso_hielo", DungeonMonster.Kind.OSO, 1.3F, 1.4F, 0xD8F0FF, 0x6FB8E6);
        ORCO = reg("orco", DungeonMonster.Kind.ORCO, 0.75F, 2.35F, 0x3F6B2E, 0x2B2B2B);
        HORMIGA = reg("hormiga", DungeonMonster.Kind.HORMIGA, 1.1F, 0.85F, 0x3A2A1E, 0xA33A1E);
        CABALLERO = reg("caballero", DungeonMonster.Kind.CABALLERO, 0.65F, 2.2F, 0x2E353C, 0x6EF0D8);
        COLOSO = reg("coloso", DungeonMonster.Kind.COLOSO, 1.2F, 3.4F, 0x8A8A8A, 0x5FD7FF);
        GUARDIAN = regBoss("guardian_juicio", SecretBoss.Kind.GUARDIAN, DungeonMonster.Kind.CABALLERO, 0.8F, 2.5F, 0xD8DCE8, 0xDEB640);
        ECLIPSE = regBoss("bestia_eclipse", SecretBoss.Kind.ECLIPSE, DungeonMonster.Kind.LOBO, 1.5F, 1.8F, 0x28223A, 0xAAA0E6);
        QUEEN = regBoss("reina_carmesi", SecretBoss.Kind.QUEEN, DungeonMonster.Kind.HORMIGA, 2.0F, 1.55F, 0x781024, 0xE6B43C);
        PRIMORDIAL = regBoss("coloso_primordial", SecretBoss.Kind.PRIMORDIAL, DungeonMonster.Kind.COLOSO, 1.4F, 4.3F, 0x362E3C, 0xFF8C28);
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(entries -> {
            for (Item egg : EGGS) {
                entries.accept(egg);
            }
        });
    }

    public static SecretBoss.Kind secretKindOf(EntityType<?> type) {
        SecretBoss.Kind k = SECRET.get(type);
        return k == null ? SecretBoss.Kind.GUARDIAN : k;
    }

    public static EntityType<SecretBoss> secretType(SecretBoss.Kind k) {
        return SECRET_TYPES.get(k);
    }

    public static DungeonMonster.Kind kindOf(EntityType<?> type) {
        DungeonMonster.Kind k = KINDS.get(type);
        return k == null ? DungeonMonster.Kind.GOBLIN : k;
    }
}
