package com.elsistema;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Herrería de la ciudad: con ESENCIAS se mejoran las armas (+1 ... +10, más daño) y se despiertan las
 * habilidades ocultas de las armas y de las piezas de armadura (una por objeto, se puede cambiar).
 * Los datos van en el propio objeto (componente custom_data): «es_mejora» y «es_oculta» (habilidad + 1).
 */
public final class Forge {
    private Forge() {}

    public static final int MAX_LEVEL = 10;
    /** Daño extra por cada nivel de mejora (+6 % por nivel: +60 % en +10). */
    public static final double DMG_PER_LEVEL = 0.06;
    /** Esencias para subir a +1, +2 ... +10. */
    public static final int[] UPGRADE_COST = {8, 14, 22, 32, 45, 60, 80, 105, 135, 170};
    public static final int AWAKEN_COST = 30;
    public static final int REROLL_COST = 20;

    private static final String KEY_LEVEL = "es_mejora";
    private static final String KEY_HIDDEN = "es_oculta";
    private static final ResourceLocation UPGRADE_ID = ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, "mejora");

    // Habilidades ocultas de las armas
    public static final int W_VAMPIRE = 0;
    public static final int W_BLACK_FLAME = 1;
    public static final int W_ECHO = 2;
    public static final int W_EXECUTIONER = 3;
    public static final int W_BOSS_HUNTER = 4;
    public static final int W_STORM = 5;
    public static final int W_MANA_THIRST = 6;
    public static final int W_FROST = 7;
    public static final String[] WEAPON_NAME = {"Vampirismo", "Llama negra", "Eco", "Verdugo", "Cazador de jefes",
            "Tormenta", "Sed de maná", "Escarcha"};
    public static final String[] WEAPON_DESC = {
            "Te curas el 6 % del daño que haces",
            "Prende fuego al enemigo 4 segundos",
            "15 % de que el golpe se repita (50 % del daño)",
            "+35 % de daño a enemigos con menos del 30 % de vida",
            "+25 % de daño a los jefes",
            "10 % de que caiga un rayo (+30 % de daño)",
            "Cada golpe te devuelve 1,5 de maná",
            "Ralentiza al enemigo 2 segundos"};

    // Habilidades ocultas de las piezas de armadura (se suman si las llevas en varias piezas)
    public static final int A_THORNS = 0;
    public static final int A_EVASION = 1;
    public static final int A_REGEN = 2;
    public static final int A_LIGHT = 3;
    public static final int A_FORTRESS = 4;
    public static final int A_MANA = 5;
    public static final int A_FIREPROOF = 6;
    public static final int A_LAST_BREATH = 7;
    public static final String[] ARMOR_NAME = {"Espinas", "Evasión", "Regeneración", "Ligereza", "Fortaleza", "Pozo de maná",
            "Ignífugo", "Último aliento"};
    public static final String[] ARMOR_DESC = {
            "Devuelve el 10 % del daño cuerpo a cuerpo (por pieza)",
            "4 % de esquivar un golpe (por pieza)",
            "+0,25 de vida por segundo (por pieza)",
            "+4 % de velocidad (por pieza)",
            "+1,5 de armadura y +1 de dureza (por pieza)",
            "+12 de maná máximo (por pieza)",
            "El fuego y la lava no te hacen daño",
            "Una vez cada 5 minutos sobrevives a un golpe mortal"};

    public static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    public static final String[] SLOT_NAME = {"Mano derecha", "Mano izquierda", "Cabeza", "Pecho", "Piernas", "Pies"};
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    // ================================================================ leer y escribir el objeto

    private static CompoundTag tag(ItemStack s) {
        CustomData cd = s.get(DataComponents.CUSTOM_DATA);
        return cd == null ? new CompoundTag() : cd.copyTag();
    }

    /** Nivel de mejora (0 ... 10). */
    public static int level(ItemStack s) {
        if (s == null || s.isEmpty()) return 0;
        return Math.max(0, Math.min(MAX_LEVEL, tag(s).getInt(KEY_LEVEL)));
    }

    /** Habilidad oculta despertada (-1 = todavía oculta). */
    public static int hidden(ItemStack s) {
        if (s == null || s.isEmpty()) return -1;
        int v = tag(s).getInt(KEY_HIDDEN) - 1;
        return v >= 0 && v < 8 ? v : -1;
    }

    /** Armas: espadas, hachas, tridentes, arcos, ballestas... (cualquier objeto que sume daño en la mano). */
    public static boolean isWeapon(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        if (s.getItem() instanceof ProjectileWeaponItem) return true;
        ItemAttributeModifiers mods = s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
            if (e.attribute().equals(Attributes.ATTACK_DAMAGE) && !e.modifier().id().equals(UPGRADE_ID)) return true;
        }
        return false;
    }

    public static boolean isArmor(ItemStack s) {
        return s != null && !s.isEmpty() && s.getItem() instanceof ArmorItem;
    }

    public static boolean isEquipment(ItemStack s) {
        return isWeapon(s) || isArmor(s);
    }

    public static String abilityName(ItemStack s, int id) {
        if (id < 0) return "???";
        return isArmor(s) ? ARMOR_NAME[id] : WEAPON_NAME[id];
    }

    public static String abilityDesc(ItemStack s, int id) {
        if (id < 0) return "Despiértala en la Herrería de la ciudad";
        return isArmor(s) ? ARMOR_DESC[id] : WEAPON_DESC[id];
    }

    private static void setLevel(ItemStack s, int lvl) {
        CustomData.update(DataComponents.CUSTOM_DATA, s, t -> t.putInt(KEY_LEVEL, lvl));
        if (s.getItem() instanceof ProjectileWeaponItem) return;
        // Las armas cuerpo a cuerpo llevan el daño extra como un modificador del propio objeto
        ItemAttributeModifiers base = s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry e : base.modifiers()) {
            if (!e.modifier().id().equals(UPGRADE_ID)) b.add(e.attribute(), e.modifier(), e.slot());
        }
        if (lvl > 0) {
            b.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(UPGRADE_ID, DMG_PER_LEVEL * lvl,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.MAINHAND);
        }
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, b.build().withTooltip(base.showInTooltip()));
    }

    private static void setHidden(ItemStack s, int id) {
        CustomData.update(DataComponents.CUSTOM_DATA, s, t -> t.putInt(KEY_HIDDEN, id + 1));
    }

    /** Líneas que se añaden a la descripción del objeto. */
    public static void tooltip(ItemStack s, List<Component> lines) {
        if (!isEquipment(s)) return;
        int lvl = level(s);
        int pos = Math.min(1, lines.size());
        if (isWeapon(s) && lvl > 0) {
            lines.add(pos++, Component.literal("✦ Mejora +" + lvl + " (+" + Math.round(DMG_PER_LEVEL * lvl * 100) + " % de daño)")
                    .withStyle(ChatFormatting.AQUA));
        }
        int h = hidden(s);
        if (h >= 0) {
            lines.add(pos++, Component.literal("☾ " + abilityName(s, h) + ": " + abilityDesc(s, h)).withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            lines.add(pos++, Component.literal("☾ Habilidad oculta: ???").withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    // ================================================================ herrería (acciones)

    /** ACT_FORGE: a = hueco (0 mano derecha ... 5 pies), b = 0 mejorar, 1 despertar o cambiar la habilidad oculta. */
    public static void action(ServerPlayer p, int slot, int op) {
        Data d = Store.get(p);
        if (d.awakened != 1 || slot < 0 || slot >= SLOTS.length) return;
        if (!City.nearRole(p, CityNpc.SMITH)) {
            SystemLogic.notify(p, Net.N_FORGE_FAIL, 3, 0);
            return;
        }
        ItemStack s = p.getItemBySlot(SLOTS[slot]);
        if (!isEquipment(s)) {
            SystemLogic.notify(p, Net.N_FORGE_FAIL, 1, 0);
            return;
        }
        if (op == 0) {
            if (!isWeapon(s)) {
                SystemLogic.notify(p, Net.N_FORGE_FAIL, 1, 0);
                return;
            }
            int lvl = level(s);
            if (lvl >= MAX_LEVEL) {
                SystemLogic.notify(p, Net.N_FORGE_FAIL, 2, 0);
                return;
            }
            int cost = UPGRADE_COST[lvl];
            if (!pay(p, d, cost)) return;
            setLevel(s, lvl + 1);
            p.level().playSound((Player) null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8F, 1.1F);
            p.level().playSound((Player) null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.4F);
            SystemLogic.notify(p, Net.N_FORGE, 0, lvl + 1);
        } else {
            int old = hidden(s);
            int cost = old < 0 ? AWAKEN_COST : REROLL_COST;
            if (!pay(p, d, cost)) return;
            int id = p.getRandom().nextInt(8);
            if (old >= 0 && id == old) id = (id + 1 + p.getRandom().nextInt(7)) % 8;
            setHidden(s, id);
            p.level().playSound((Player) null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 0.7F);
            p.level().playSound((Player) null, p.getX(), p.getY(), p.getZ(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1.0F, 0.9F);
            SystemLogic.notify(p, Net.N_FORGE, old < 0 ? 1 : 2, id + (isArmor(s) ? 100 : 0));
        }
        p.getInventory().setChanged();
        armorTick(p, d);
        SystemLogic.sync(p, d);
    }

    private static boolean pay(ServerPlayer p, Data d, int cost) {
        if (d.essences < cost && !p.isCreative()) {
            SystemLogic.notify(p, Net.N_FORGE_FAIL, 0, cost);
            return false;
        }
        if (!p.isCreative()) d.essences -= cost;
        return true;
    }

    /** Prueba automática: mejora el objeto y le pone una habilidad sin pasar por la herrería. */
    public static void testSet(ItemStack s, int lvl, int hidden) {
        if (isWeapon(s)) setLevel(s, lvl);
        if (hidden >= 0) setHidden(s, hidden);
    }

    // ================================================================ efectos en combate

    /** Daño extra que llega un tick después del golpe (eco, verdugo, cazador de jefes, tormenta). */
    private record Extra(LivingEntity victim, ServerPlayer attacker, float dmg, int at) {}

    private static final List<Extra> EXTRA = new ArrayList<>();
    private static int clock = 0;

    /** Golpe cuerpo a cuerpo del Jugador con un arma: habilidades ocultas del arma. */
    public static void onPlayerHit(ServerPlayer p, LivingEntity victim, float amount) {
        ItemStack w = p.getMainHandItem();
        int h = hidden(w);
        if (h < 0 || !isWeapon(w) || amount <= 0F) return;
        switch (h) {
            case W_VAMPIRE -> p.heal(amount * 0.06F);
            case W_BLACK_FLAME -> victim.setRemainingFireTicks(Math.max(victim.getRemainingFireTicks(), 80));
            case W_ECHO -> {
                if (p.getRandom().nextInt(100) < 15) {
                    EXTRA.add(new Extra(victim, p, amount * 0.5F, clock + 6));
                }
            }
            case W_EXECUTIONER -> {
                if (victim.getHealth() - amount < victim.getMaxHealth() * 0.3F) EXTRA.add(new Extra(victim, p, amount * 0.35F, clock + 1));
            }
            case W_BOSS_HUNTER -> {
                if (Ranks.isBoss(victim) || victim instanceof SecretBoss) EXTRA.add(new Extra(victim, p, amount * 0.25F, clock + 1));
            }
            case W_STORM -> {
                if (p.getRandom().nextInt(100) < 10 && victim.level() instanceof ServerLevel lvl) {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(lvl);
                    if (bolt != null) {
                        bolt.moveTo(victim.getX(), victim.getY(), victim.getZ(), 0.0F, 0.0F);
                        bolt.setVisualOnly(true);
                        lvl.addFreshEntity(bolt);
                    }
                    EXTRA.add(new Extra(victim, p, amount * 0.3F, clock + 2));
                }
            }
            case W_MANA_THIRST -> {
                Data d = Store.get(p);
                d.mp = Math.min(SystemLogic.maxMp(d), d.mp + 1.5);
            }
            case W_FROST -> victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), p);
            default -> {
            }
        }
    }

    /** Cada tick del servidor: aplica los golpes extra pendientes. */
    public static void tick() {
        clock++;
        if (EXTRA.isEmpty()) return;
        for (Iterator<Extra> it = EXTRA.iterator(); it.hasNext(); ) {
            Extra e = it.next();
            if (e.at() > clock) continue;
            it.remove();
            if (!e.victim().isAlive() || e.attacker().isRemoved()) continue;
            DamageSource src = e.attacker().damageSources().playerAttack(e.attacker());
            Combat.extraHit(e.victim(), src, e.dmg());
            if (e.victim().level() instanceof ServerLevel lvl) {
                Fx.send(lvl, net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT, e.victim().getX(),
                        e.victim().getY() + e.victim().getBbHeight() * 0.6, e.victim().getZ(), 10, 0.3, 0.3, 0.3, 0.2);
            }
        }
    }

    /** Cuántas piezas de armadura puestas tienen esta habilidad. */
    public static int armorCount(LivingEntity e, int ability) {
        int n = 0;
        for (EquipmentSlot s : ARMOR_SLOTS) {
            ItemStack st = e.getItemBySlot(s);
            if (isArmor(st) && hidden(st) == ability) n++;
        }
        return n;
    }

    /**
     * Golpe que recibe el Jugador: habilidades de su armadura. Devuelve false si se anula
     * (esquivado o fuego con Ignífugo).
     */
    public static boolean onPlayerHurt(ServerPlayer p, DamageSource src, float amount) {
        if (armorCount(p, A_FIREPROOF) > 0 && src.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
            p.clearFire();
            return false;
        }
        net.minecraft.world.entity.Entity attacker = src.getEntity();
        if (attacker != null && attacker != p) {
            int ev = armorCount(p, A_EVASION);
            if (ev > 0 && p.getRandom().nextInt(100) < 4 * ev) {
                SystemLogic.notify(p, Net.N_DODGE, 0, 0);
                p.level().playSound((Player) null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 1.0F, 1.6F);
                return false;
            }
            int th = armorCount(p, A_THORNS);
            if (th > 0 && attacker instanceof LivingEntity le && src.getDirectEntity() == attacker) {
                Combat.extraHit(le, p.damageSources().thorns(p), amount * 0.10F * th);
            }
        }
        return true;
    }

    /** Último aliento: true si el Jugador se salva de morir. */
    public static boolean saveFromDeath(ServerPlayer p) {
        Data d = Store.get(p);
        if (d.lastBreath > 0 || armorCount(p, A_LAST_BREATH) <= 0) return false;
        d.lastBreath = 6000;
        p.setHealth(Math.max(1.0F, p.getMaxHealth() * 0.3F));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 2, false, true, true));
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1, false, true, true));
        p.level().playSound((Player) null, p.getX(), p.getY(), p.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 1.3F);
        if (p.level() instanceof ServerLevel lvl) {
            Fx.send(lvl, net.minecraft.core.particles.ParticleTypes.SOUL, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
        }
        SystemLogic.notify(p, Net.N_LAST_BREATH, 0, 0);
        return true;
    }

    /** Cada segundo: regeneración, ligereza, fortaleza y maná de la armadura. */
    public static void armorTick(ServerPlayer p, Data d) {
        int regen = armorCount(p, A_REGEN);
        if (regen > 0 && p.getHealth() < p.getMaxHealth()) p.heal(0.25F * regen);
        mod(p, Attributes.MOVEMENT_SPEED, "forja_ligereza", 0.04 * armorCount(p, A_LIGHT), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        int fort = armorCount(p, A_FORTRESS);
        mod(p, Attributes.ARMOR, "forja_fortaleza", 1.5 * fort, AttributeModifier.Operation.ADD_VALUE);
        mod(p, Attributes.ARMOR_TOUGHNESS, "forja_fortaleza", fort, AttributeModifier.Operation.ADD_VALUE);
        d.forgeMp = 12 * armorCount(p, A_MANA);
        if (armorCount(p, A_FIREPROOF) > 0 && p.isOnFire()) p.clearFire();
        if (d.lastBreath > 0) d.lastBreath = Math.max(0, d.lastBreath - 20);
    }

    private static void mod(LivingEntity e, Holder<Attribute> attr, String path, double v, AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst == null) return;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, path);
        AttributeModifier old = inst.getModifier(id);
        if (old != null && old.amount() == v) return;
        inst.removeModifier(id);
        if (v != 0.0) inst.addTransientModifier(new AttributeModifier(id, v, op));
    }

    /** Flechas: el arco o la ballesta mejorados multiplican el daño. */
    public static double bowMult(Player p) {
        ItemStack w = p.getMainHandItem().getItem() instanceof ProjectileWeaponItem ? p.getMainHandItem() : p.getOffhandItem();
        if (!(w.getItem() instanceof ProjectileWeaponItem)) return 1.0;
        return 1.0 + DMG_PER_LEVEL * level(w);
    }
}
