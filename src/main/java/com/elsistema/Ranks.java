package com.elsistema;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;

/** Monstruos con rango (E a S) y nivel: más fuertes, más grandes y con mejor botín. */
public final class Ranks {
    private Ranks() {}

    public static final String TAG = "es_rank";
    /** Mientras es true, los monstruos que aparecen no reciben rango automático (los pone la mazmorra). */
    public static boolean spawning = false;

    private static final double[] HP = {0.0, 0.3, 0.7, 1.2, 2.0, 3.5};
    private static final double[] SIZE = {0.0, 0.05, 0.1, 0.15, 0.25, 0.4};
    private static final double[] REWARD = {1.0, 1.3, 1.7, 2.3, 3.2, 5.0};
    private static final int[] CORE_CHANCE = {12, 16, 20, 25, 32, 45};
    public static final ChatFormatting[] COLOR = {
            ChatFormatting.GRAY, ChatFormatting.GREEN, ChatFormatting.AQUA,
            ChatFormatting.LIGHT_PURPLE, ChatFormatting.GOLD, ChatFormatting.RED};

    /** Se llama cuando aparece cualquier criatura: los monstruos reciben rango y nivel. */
    public static void onLoad(Entity e) {
        if (spawning) return;
        if (!(e instanceof Mob mob) || !(e instanceof Enemy)) return;
        if (mob instanceof EnderDragon || mob instanceof WitherBoss) return;
        if (mob.getTags().contains(TAG) || mob.hasCustomName()) return;
        if (!(mob.level() instanceof ServerLevel lvl)) return;
        int base;
        Player near = lvl.getNearestPlayer(mob, 128.0);
        if (near instanceof ServerPlayer sp) {
            base = Store.get(sp).level;
        } else {
            base = 1 + (int) (Math.sqrt(mob.blockPosition().distSqr(lvl.getSharedSpawnPos())) / 150.0);
        }
        RandomSource r = mob.getRandom();
        int level = base + r.nextInt(7) - 3;
        if (r.nextInt(100) < 8) level += 8;
        level = Math.max(1, Math.min(Defs.MAX_LEVEL, level));
        apply(mob, Defs.rank(level), level, false);
    }

    /** Da rango y nivel a un monstruo (también lo usan las mazmorras). */
    public static void apply(Mob mob, int rank, int level, boolean boss) {
        for (String t : new ArrayList<>(mob.getTags())) {
            if (t.startsWith("esr_") || t.startsWith("esl_")) mob.removeTag(t);
        }
        mob.addTag(TAG);
        mob.addTag("esr_" + rank);
        mob.addTag("esl_" + level);
        double hp = 0.06 * level + HP[rank] + (boss ? 3.0 + rank * 1.5 : 0.0);
        mod(mob, Attributes.MAX_HEALTH, "rango_vida", hp, Operation.ADD_MULTIPLIED_TOTAL);
        mod(mob, Attributes.ATTACK_DAMAGE, "rango_dano", 0.03 * level + (boss ? 0.5 : 0.0), Operation.ADD_MULTIPLIED_TOTAL);
        mod(mob, Attributes.MOVEMENT_SPEED, "rango_velocidad", 0.02 * rank, Operation.ADD_MULTIPLIED_BASE);
        mod(mob, Attributes.ARMOR, "rango_armadura", 1.5 * rank + (boss ? 4.0 : 0.0), Operation.ADD_VALUE);
        mod(mob, Attributes.SCALE, "rango_tamano", SIZE[rank] + (boss ? 0.5 : 0.0), Operation.ADD_VALUE);
        mob.setHealth(mob.getMaxHealth());
        MutableComponent name = Component.literal((boss ? "★ " : "") + "[" + Defs.RANK[rank] + "] ").withStyle(COLOR[rank]);
        name.append(mob.getType().getDescription());
        name.append(Component.literal(" Nv." + level).withStyle(ChatFormatting.WHITE));
        mob.setCustomName(name);
        mob.setCustomNameVisible(boss);
    }

    private static void mod(Mob mob, Holder<Attribute> attr, String path, double v, Operation op) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst == null) return;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, path);
        inst.removeModifier(id);
        if (v != 0.0) {
            inst.addPermanentModifier(new AttributeModifier(id, v, op));
        }
    }

    private static int tagNumber(Entity e, String prefix) {
        for (String t : e.getTags()) {
            if (t.startsWith(prefix)) {
                try {
                    return Integer.parseInt(t.substring(prefix.length()));
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        return -1;
    }

    public static int rankOf(Entity e) {
        int r = tagNumber(e, "esr_");
        return r >= 0 && r <= 5 ? r : -1;
    }

    public static int levelOf(Entity e) {
        return Math.max(1, tagNumber(e, "esl_"));
    }

    /** Recompensas al matar una criatura: EXP, oro y botín según rango y nivel. */
    public static void onKill(ServerPlayer p, LivingEntity dead) {
        if (dead instanceof Player) return;
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()).getPath();
        boolean hostile = dead instanceof Enemy;
        int base = Xp.forMob(id, hostile);
        int rank = rankOf(dead);
        double mult = rank >= 0 ? REWARD[rank] * (1.0 + 0.04 * levelOf(dead)) : 1.0;
        SystemLogic.addXp(p, (int) Math.round(base * mult));
        if (hostile) {
            SystemLogic.addCoins(p, (int) Math.round(Math.max(1.0, base / 2.0) * mult));
        }
        if (rank >= 0 && hostile) {
            Data d = Store.get(p);
            double bonus = 1.0 + d.fx(Trees.FX_DROP) / 100.0;
            RandomSource r = dead.getRandom();
            if (r.nextInt(100) < CORE_CHANCE[rank] * bonus) dead.spawnAtLocation(new ItemStack(ModItems.CORES[rank]));
            if (r.nextInt(1000) < (5 + rank * 5) * bonus) dead.spawnAtLocation(new ItemStack(ModItems.RUNE));
            if (r.nextInt(1000) < (15 + rank * 3) * bonus) dead.spawnAtLocation(new ItemStack(ModItems.KEYS[rank]));
        }
    }
}
