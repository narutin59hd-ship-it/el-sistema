package com.elsistema;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;

import java.util.ArrayList;
import java.util.List;

/** Lógica del Sistema: experiencia, niveles, estadísticas, razas, clases y maná. */
public final class SystemLogic {
    private SystemLogic() {}

    public static final String ARROW_TAG = "elsistema_flecha";

    private record Mod(Holder<Attribute> attr, double amount, Operation op) {}

    /** Atributos que pueden modificar las razas y las clases. */
    private static final List<Holder<Attribute>> ALL = List.of(
            Attributes.MAX_HEALTH, Attributes.ARMOR, Attributes.ATTACK_DAMAGE, Attributes.ATTACK_SPEED,
            Attributes.MOVEMENT_SPEED, Attributes.KNOCKBACK_RESISTANCE, Attributes.SCALE,
            Attributes.BLOCK_BREAK_SPEED, Attributes.LUCK);

    /** Ventajas de cada raza (mismo orden que Defs.RACE_NAME). */
    private static final List<List<Mod>> RACE_MODS = List.of(
            List.<Mod>of(),
            List.of(
                    new Mod(Attributes.MOVEMENT_SPEED, 0.10, Operation.ADD_MULTIPLIED_BASE),
                    new Mod(Attributes.MAX_HEALTH, -2.0, Operation.ADD_VALUE)),
            List.of(
                    new Mod(Attributes.SCALE, -0.2, Operation.ADD_VALUE),
                    new Mod(Attributes.ARMOR, 4.0, Operation.ADD_VALUE),
                    new Mod(Attributes.BLOCK_BREAK_SPEED, 0.25, Operation.ADD_MULTIPLIED_BASE),
                    new Mod(Attributes.KNOCKBACK_RESISTANCE, 0.2, Operation.ADD_VALUE),
                    new Mod(Attributes.MOVEMENT_SPEED, -0.05, Operation.ADD_MULTIPLIED_BASE)),
            List.of(
                    new Mod(Attributes.SCALE, 0.15, Operation.ADD_VALUE),
                    new Mod(Attributes.MAX_HEALTH, 6.0, Operation.ADD_VALUE),
                    new Mod(Attributes.ATTACK_DAMAGE, 2.0, Operation.ADD_VALUE),
                    new Mod(Attributes.MOVEMENT_SPEED, -0.05, Operation.ADD_MULTIPLIED_BASE)),
            List.of(
                    new Mod(Attributes.MAX_HEALTH, -2.0, Operation.ADD_VALUE),
                    new Mod(Attributes.ATTACK_DAMAGE, 1.0, Operation.ADD_VALUE)));

    /** Ventajas pasivas de cada clase (mismo orden que Defs.CLASS_NAME). */
    private static final List<List<Mod>> CLASS_MODS = List.of(
            List.of(new Mod(Attributes.ATTACK_SPEED, 0.4, Operation.ADD_VALUE)),
            List.of(new Mod(Attributes.MOVEMENT_SPEED, 0.05, Operation.ADD_MULTIPLIED_BASE)),
            List.<Mod>of(),
            List.of(
                    new Mod(Attributes.MOVEMENT_SPEED, 0.15, Operation.ADD_MULTIPLIED_BASE),
                    new Mod(Attributes.ATTACK_SPEED, 0.3, Operation.ADD_VALUE)),
            List.of(
                    new Mod(Attributes.MAX_HEALTH, 8.0, Operation.ADD_VALUE),
                    new Mod(Attributes.ARMOR, 4.0, Operation.ADD_VALUE),
                    new Mod(Attributes.KNOCKBACK_RESISTANCE, 0.3, Operation.ADD_VALUE),
                    new Mod(Attributes.MOVEMENT_SPEED, -0.05, Operation.ADD_MULTIPLIED_BASE)));

    // ================================================================ Utilidades

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, path);
    }

    /** Pone (o quita si amount = 0) un modificador temporal: no queda guardado si se desinstala el mod. */
    private static void setMod(ServerPlayer p, Holder<Attribute> attr, String path, double amount, Operation op) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        ResourceLocation rid = id(path);
        inst.removeModifier(rid);
        if (amount != 0.0) {
            inst.addTransientModifier(new AttributeModifier(rid, amount, op));
        }
    }

    public static void notify(ServerPlayer p, int kind, int a, int b) {
        ServerPlayNetworking.send(p, new Net.NotifyPayload(kind, a, b));
    }

    public static void sync(ServerPlayer p, Data d) {
        List<Integer> l = new ArrayList<>(16);
        l.add(d.awakened);
        l.add(d.race);
        l.add(d.cls);
        l.add(d.level);
        l.add(d.xp);
        l.add(Defs.xpNeeded(d.level));
        l.add(d.points);
        for (int s : d.stats) {
            l.add(s);
        }
        l.add((int) Math.floor(d.mp));
        l.add(maxMp(d));
        l.add(d.cooldown);
        l.add(Math.max(1, d.cooldownTotal));
        ServerPlayNetworking.send(p, new Net.SyncPayload(l));
    }

    private static void playAt(ServerPlayer p, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        p.level().playSound((Player) null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    // ================================================================ Valores derivados

    public static int maxMp(Data d) {
        int m = 50 + 3 * d.bonus(3);
        if (d.awakened == 1 && d.cls == Defs.MAGO) m += 50;
        if (d.awakened == 1 && d.race == Defs.DEMONIO) m += 30;
        return m;
    }

    public static double mpRegen(Data d) {
        double r = 1.0 + 0.05 * d.bonus(3);
        if (d.awakened == 1 && d.cls == Defs.MAGO) r *= 2.0;
        return r;
    }

    /** Multiplicador de daño de las habilidades: +1% por punto de INT y +1% por nivel. */
    public static double skillMult(Data d) {
        return 1.0 + 0.01 * d.bonus(3) + 0.01 * (d.level - 1);
    }

    /** Reducción de enfriamiento por PERCEPCIÓN (máx. 50%). */
    public static double cdr(Data d) {
        return Math.min(0.5, 0.005 * d.bonus(4));
    }

    /** Multiplicador de daño de las flechas del Jugador (elfo y arquero). */
    public static double arrowBonus(Data d) {
        double b = 1.0;
        if (d.awakened == 1) {
            if (d.race == Defs.ELFO) b += 0.2;
            if (d.cls == Defs.ARQUERO) b += 0.3;
        }
        return b;
    }

    // ================================================================ Atributos

    /** Recalcula todos los bonus del Jugador. */
    public static void applyAll(ServerPlayer p, Data d) {
        setMod(p, Attributes.ATTACK_DAMAGE, "stat_fuerza", 0.2 * d.bonus(0), Operation.ADD_VALUE);
        setMod(p, Attributes.MOVEMENT_SPEED, "stat_agilidad_vel", Math.min(0.6, 0.003 * d.bonus(1)), Operation.ADD_MULTIPLIED_BASE);
        setMod(p, Attributes.ATTACK_SPEED, "stat_agilidad_atq", 0.008 * d.bonus(1), Operation.ADD_VALUE);
        setMod(p, Attributes.MAX_HEALTH, "stat_vitalidad", 0.5 * d.bonus(2), Operation.ADD_VALUE);
        setMod(p, Attributes.LUCK, "stat_percepcion", 0.02 * d.bonus(4), Operation.ADD_VALUE);

        for (int i = 0; i < ALL.size(); i++) {
            setMod(p, ALL.get(i), "raza_" + i, 0.0, Operation.ADD_VALUE);
            setMod(p, ALL.get(i), "clase_" + i, 0.0, Operation.ADD_VALUE);
        }
        if (d.awakened == 1) {
            if (d.race >= 0 && d.race < RACE_MODS.size()) {
                for (Mod m : RACE_MODS.get(d.race)) {
                    setMod(p, m.attr(), "raza_" + ALL.indexOf(m.attr()), m.amount(), m.op());
                }
            }
            if (d.cls >= 0 && d.cls < CLASS_MODS.size()) {
                for (Mod m : CLASS_MODS.get(d.cls)) {
                    setMod(p, m.attr(), "clase_" + ALL.indexOf(m.attr()), m.amount(), m.op());
                }
            }
        }
        d.swordOn = false;
        setMod(p, Attributes.ATTACK_DAMAGE, "clase_espada", 0.0, Operation.ADD_VALUE);
        updateSword(p, d);
        if (p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth());
        }
    }

    /** Espadachín: +20% de daño solo con una espada en la mano. */
    private static void updateSword(ServerPlayer p, Data d) {
        boolean want = d.awakened == 1 && d.cls == Defs.ESPADACHIN && p.getMainHandItem().is(ItemTags.SWORDS);
        if (want != d.swordOn) {
            d.swordOn = want;
            setMod(p, Attributes.ATTACK_DAMAGE, "clase_espada", want ? 0.2 : 0.0, Operation.ADD_MULTIPLIED_BASE);
        }
    }

    // ================================================================ EXP y niveles

    public static void addXp(ServerPlayer p, int amount) {
        Data d = Store.get(p);
        if (d.awakened != 1 || amount <= 0 || d.level >= Defs.MAX_LEVEL) return;
        if (d.race == Defs.HUMANO) {
            amount = (int) Math.round(amount * 1.1);
        }
        d.xp += amount;
        notify(p, Net.N_XP, amount, 0);

        int oldLevel = d.level;
        int oldRank = Defs.rank(d.level);
        int gained = 0;
        while (d.level < Defs.MAX_LEVEL && d.xp >= Defs.xpNeeded(d.level)) {
            d.xp -= Defs.xpNeeded(d.level);
            d.level++;
            int pts = d.race == Defs.HUMANO ? 4 : 3;
            d.points += pts;
            gained += pts;
        }
        if (d.level >= Defs.MAX_LEVEL) {
            d.xp = 0;
        }
        if (d.level > oldLevel) {
            applyAll(p, d);
            p.setHealth(p.getMaxHealth());
            d.mp = maxMp(d);
            d.anim = 40;
            levelUpSounds(p);
            notify(p, Net.N_LEVEL_UP, d.level, gained);
            int newRank = Defs.rank(d.level);
            if (newRank > oldRank) {
                notify(p, Net.N_RANK_UP, newRank, oldRank);
            }
            if (d.level >= Defs.MAX_LEVEL) {
                notify(p, Net.N_MAX_LEVEL, 0, 0);
            }
        }
        sync(p, d);
    }

    private static void levelUpSounds(ServerPlayer p) {
        p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.1F);
        p.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F, 1.6F);
        playAt(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
    }

    /** Columna de luz azul que gira alrededor del Jugador al subir de nivel. */
    private static void animTick(ServerPlayer p, Data d) {
        ServerLevel lvl = (ServerLevel) p.level();
        int age = 40 - d.anim;
        if (age == 0) {
            lvl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 60, 0.6, 1.0, 0.6, 0.15);
            lvl.sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.2, p.getZ(), 80, 0.8, 1.0, 0.8, 0.5);
        }
        double h = age * 0.07;
        for (int k = 0; k < 3; k++) {
            double ang = age * 0.45 + k * (Math.PI * 2.0 / 3.0);
            double x = p.getX() + Math.cos(ang) * 0.9;
            double z = p.getZ() + Math.sin(ang) * 0.9;
            lvl.sendParticles(ParticleTypes.END_ROD, x, p.getY() + h, z, 1, 0.0, 0.0, 0.0, 0.0);
            lvl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, p.getY() + h, z, 1, 0.0, 0.02, 0.0, 0.01);
        }
        d.anim--;
    }

    // ================================================================ Tick del servidor

    public static void tick(MinecraftServer server) {
        int t = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Data d = Store.get(p);
            if (d.cooldown > 0) d.cooldown--;
            if (d.joinDelay > 0) {
                d.joinDelay--;
                if (d.joinDelay == 0) {
                    notify(p, d.awakened == 1 ? Net.N_WELCOME : Net.N_AWAKEN_PROMPT, 0, 0);
                }
            }
            if (d.anim > 0) animTick(p, d);
            if (t % 20 == 0) {
                if (p.isAlive()) d.health = p.getHealth();
                if (d.awakened == 1) {
                    int max = maxMp(d);
                    d.mp = Math.min(max, d.mp + mpRegen(d));
                }
            }
            if (t % 10 == 0) {
                updateSword(p, d);
                if (d.awakened == 1 && d.race == Defs.DEMONIO) {
                    p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, true, false, false));
                }
                sync(p, d);
            }
        }
        if (t % 6000 == 0) {
            Store.saveAll(server);
        }
    }

    // ================================================================ Acciones del cliente

    public static void action(ServerPlayer p, int action, int a, int b) {
        switch (action) {
            case Net.ACT_SKILL -> useSkill(p);
            case Net.ACT_STAT -> addStat(p, a, b);
            case Net.ACT_CHOOSE -> choose(p, a, b);
            default -> {
            }
        }
    }

    public static void useSkill(ServerPlayer p) {
        Data d = Store.get(p);
        if (d.awakened != 1) {
            notify(p, Net.N_NOT_AWAKENED, 0, 0);
            return;
        }
        if (p.isSpectator() || !p.isAlive()) return;
        if (d.cooldown > 0) {
            notify(p, Net.N_COOLDOWN, (d.cooldown + 19) / 20, 0);
            return;
        }
        int cost = Defs.SKILL_COST[d.cls];
        if (d.mp < cost && !p.isCreative()) {
            notify(p, Net.N_NO_MP, cost, (int) Math.floor(d.mp));
            return;
        }
        boolean ok = switch (d.cls) {
            case Defs.ESPADACHIN -> Skills.slash(p, d);
            case Defs.ARQUERO -> Skills.volley(p, d);
            case Defs.MAGO -> Skills.lance(p, d);
            case Defs.ASESINO -> Skills.stealth(p, d);
            case Defs.TANQUE -> Skills.fortress(p, d);
            default -> false;
        };
        if (!ok) return;
        if (!p.isCreative()) d.mp -= cost;
        int cd = (int) Math.round(Defs.SKILL_CD[d.cls] * (1.0 - cdr(d)));
        d.cooldown = Math.max(1, cd);
        d.cooldownTotal = d.cooldown;
        notify(p, Net.N_SKILL, d.cls, 0);
        sync(p, d);
    }

    public static void addStat(ServerPlayer p, int stat, int count) {
        Data d = Store.get(p);
        if (d.awakened != 1 || stat < 0 || stat > 4) return;
        int n = Math.min(Math.max(1, Math.min(10, count)), d.points);
        if (n <= 0) return;
        d.points -= n;
        d.stats[stat] += n;
        applyAll(p, d);
        p.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.6F);
        sync(p, d);
    }

    public static void choose(ServerPlayer p, int race, int cls) {
        Data d = Store.get(p);
        if (d.awakened == 1) return;
        if (race < 0 || race >= Defs.RACE_NAME.length || cls < 0 || cls >= Defs.CLASS_NAME.length) return;
        d.race = race;
        d.cls = cls;
        d.awakened = 1;
        applyAll(p, d);
        p.setHealth(p.getMaxHealth());
        d.mp = maxMp(d);
        d.anim = 40;
        levelUpSounds(p);
        notify(p, Net.N_AWAKENED, race, cls);
        sync(p, d);
        Store.save(p.getServer(), p.getUUID());
    }

    /** Comando /sistema cambiar: volver a elegir raza y clase sin perder el progreso. */
    public static void rechoose(ServerPlayer p) {
        Data d = Store.get(p);
        d.awakened = 0;
        applyAll(p, d);
        sync(p, d);
        notify(p, Net.N_AWAKEN_PROMPT, 0, 0);
    }

    /** Comando /sistema reiniciar: empezar de cero. */
    public static void reset(ServerPlayer p) {
        Data d = Store.get(p);
        d.awakened = 0;
        d.race = -1;
        d.cls = -1;
        d.level = 1;
        d.xp = 0;
        d.points = 0;
        d.stats = new int[]{Defs.BASE_STAT, Defs.BASE_STAT, Defs.BASE_STAT, Defs.BASE_STAT, Defs.BASE_STAT};
        d.mp = 50.0;
        d.cooldown = 0;
        applyAll(p, d);
        sync(p, d);
        notify(p, Net.N_AWAKEN_PROMPT, 0, 0);
    }

    // ================================================================ Eventos

    public static void onJoin(ServerPlayer p, MinecraftServer server) {
        Data d = Store.load(server, p.getUUID());
        applyAll(p, d);
        if (d.health > 0F && p.isAlive()) {
            p.setHealth(Math.min(d.health, p.getMaxHealth()));
        }
        if (d.mp > maxMp(d)) d.mp = maxMp(d);
        d.joinDelay = 60;
        sync(p, d);
    }

    public static void onRespawn(ServerPlayer p) {
        Data d = Store.get(p);
        applyAll(p, d);
        p.setHealth(p.getMaxHealth());
        d.mp = maxMp(d);
        sync(p, d);
    }

    /** Las flechas que dispara el Jugador hacen más daño si es elfo o arquero. */
    public static void onEntityLoad(Entity entity) {
        if (!(entity instanceof AbstractArrow arrow)) return;
        if (arrow.getTags().contains(ARROW_TAG)) return;
        if (!(arrow.getOwner() instanceof ServerPlayer sp)) return;
        arrow.addTag(ARROW_TAG);
        double b = arrowBonus(Store.get(sp));
        if (b > 1.0) {
            arrow.setBaseDamage(arrow.getBaseDamage() * b);
        }
    }
}
