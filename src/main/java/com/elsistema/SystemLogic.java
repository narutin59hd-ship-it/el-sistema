package com.elsistema;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Lógica del Sistema: experiencia, niveles, estadísticas, razas, clases, árboles, oro y maná. */
public final class SystemLogic {
    private SystemLogic() {}

    public static final String ARROW_TAG = "elsistema_flecha";

    private record Mod(Holder<Attribute> attr, double amount, Operation op) {}

    /** Atributos que pueden modificar las razas y las clases. */
    private static final List<Holder<Attribute>> ALL = List.of(
            Attributes.MAX_HEALTH, Attributes.ARMOR, Attributes.ATTACK_DAMAGE, Attributes.ATTACK_SPEED,
            Attributes.MOVEMENT_SPEED, Attributes.KNOCKBACK_RESISTANCE, Attributes.SCALE,
            Attributes.BLOCK_BREAK_SPEED, Attributes.LUCK);

    /** Atributos de los árboles (mismo orden que Trees.A_*). */
    private static final List<Holder<Attribute>> TREE_ATTRS = List.of(
            Attributes.MAX_HEALTH, Attributes.ARMOR, Attributes.ATTACK_DAMAGE, Attributes.ATTACK_SPEED,
            Attributes.MOVEMENT_SPEED, Attributes.KNOCKBACK_RESISTANCE, Attributes.BLOCK_BREAK_SPEED,
            Attributes.ARMOR_TOUGHNESS);
    private static final Operation[] TREE_OPS = {
            Operation.ADD_VALUE, Operation.ADD_VALUE, Operation.ADD_VALUE, Operation.ADD_VALUE,
            Operation.ADD_MULTIPLIED_BASE, Operation.ADD_VALUE, Operation.ADD_MULTIPLIED_BASE, Operation.ADD_VALUE};

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
                    new Mod(Attributes.ATTACK_DAMAGE, 1.0, Operation.ADD_VALUE)),
            List.of(
                    new Mod(Attributes.SCALE, 0.1, Operation.ADD_VALUE),
                    new Mod(Attributes.MAX_HEALTH, 4.0, Operation.ADD_VALUE),
                    new Mod(Attributes.ARMOR, 2.0, Operation.ADD_VALUE),
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

    /** Atributos que cambia la mutación de clase (y lo que suma cada clase mutada). */
    private static final List<Holder<Attribute>> MUT_ATTRS = List.of(
            Attributes.ATTACK_DAMAGE, Attributes.ATTACK_SPEED, Attributes.MOVEMENT_SPEED, Attributes.MAX_HEALTH,
            Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS);
    private static final List<List<Mod>> MUTATION_MODS = List.of(
            List.of(
                    new Mod(Attributes.ATTACK_DAMAGE, 2.0, Operation.ADD_VALUE),
                    new Mod(Attributes.ATTACK_SPEED, 0.2, Operation.ADD_VALUE)),
            List.of(new Mod(Attributes.MOVEMENT_SPEED, 0.10, Operation.ADD_MULTIPLIED_BASE)),
            List.<Mod>of(),
            List.<Mod>of(),
            List.of(
                    new Mod(Attributes.MAX_HEALTH, 8.0, Operation.ADD_VALUE),
                    new Mod(Attributes.ARMOR, 3.0, Operation.ADD_VALUE),
                    new Mod(Attributes.ARMOR_TOUGHNESS, 2.0, Operation.ADD_VALUE)));

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

    public static void sendShadows(ServerPlayer p, List<Integer> nums, List<String> strs) {
        ServerPlayNetworking.send(p, new Net.ShadowsPayload(nums, strs));
    }

    public static void sync(ServerPlayer p, Data d) {
        List<Integer> l = new ArrayList<>(64);
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
        l.add((int) Math.min(Integer.MAX_VALUE, d.coins));
        l.add(d.skillPoints);
        for (int i = 0; i < 5; i++) {
            l.add(d.raceRank(i));
        }
        for (int i = 0; i < 5; i++) {
            l.add(d.classRank(i));
        }
        for (int i = 0; i < 5; i++) {
            l.add(d.shadowRank(i));
        }
        l.add(Shadows.count(d));
        l.add(Shadows.maxSlots(d));
        l.add(d.setBonus);
        l.add(d.isMutated() ? 1 : 0);
        l.add(d.milestones);
        l.add(d.trialTries);
        for (int i = 0; i < Quests.COUNT; i++) {
            l.add(d.quest[i]);
        }
        for (int i = 0; i < Quests.COUNT; i++) {
            l.add(d.questProg[i]);
        }
        l.add(d.rank());
        l.add(d.prestige);
        l.add(d.examTries);
        // 1.7.0: esencias, misiones secundarias y si está en la ciudad
        l.add(d.essences);
        for (int i = 0; i < SideQuests.COUNT; i++) {
            l.add(d.side[i]);
        }
        for (int i = 0; i < SideQuests.COUNT; i++) {
            l.add(d.sideProg[i]);
        }
        l.add(City.inCity(p) ? 1 : 0);
        ServerPlayNetworking.send(p, new Net.SyncPayload(l));
    }

    private static void playAt(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        p.level().playSound((Player) null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    // ================================================================ Valores derivados

    public static int maxMp(Data d) {
        int m = 50 + 3 * d.bonus(3);
        if (d.awakened == 1 && d.cls == Defs.MAGO) m += 50;
        if (d.awakened == 1 && d.race == Defs.DEMONIO) m += 30;
        if (d.setBonus == ModArmor.SET_ARCHIMAGO) m += 40;
        return m + (int) Math.round(d.fx(Trees.FX_MP_MAX)) + d.forgeMp;
    }

    public static double mpRegen(Data d) {
        double r = 1.0 + 0.05 * d.bonus(3);
        if (d.awakened == 1 && d.cls == Defs.MAGO) r *= 2.0;
        if (d.setBonus == ModArmor.SET_ARCHIMAGO) r *= 1.5;
        return r * (1.0 + d.fx(Trees.FX_MP_REGEN) / 100.0);
    }

    /** Multiplicador de daño de las habilidades: +1% por INT, +1% por nivel y los árboles. */
    public static double skillMult(Data d) {
        return 1.0 + 0.01 * d.bonus(3) + 0.01 * (d.level - 1) + d.fx(Trees.FX_SKILL_DMG) / 100.0;
    }

    /** Reducción de enfriamiento por PERCEPCIÓN y árboles (máx. 60%). */
    public static double cdr(Data d) {
        return Math.min(0.6, 0.005 * d.bonus(4) + d.fx(Trees.FX_SKILL_CD) / 100.0);
    }

    /** Multiplicador de daño de las flechas del Jugador. */
    public static double arrowBonus(Data d) {
        double b = 1.0;
        if (d.awakened == 1) {
            if (d.race == Defs.ELFO) b += 0.2;
            if (d.cls == Defs.ARQUERO) b += 0.3;
            b += d.fx(Trees.FX_ARROW_DMG) / 100.0;
        }
        return b;
    }

    private static double xpMult(Data d) {
        double m = 1.0 + d.fx(Trees.FX_XP) / 100.0;
        if (d.race == Defs.HUMANO) m += 0.1;
        if (d.race == Defs.DRAGON) m -= 0.1;
        m += Defs.RANK_XP[d.rank()] / 100.0;
        m += Defs.P_XP * Defs.prestigeLevel(d.prestige) / 100.0;
        return m;
    }

    /** Puntos de estadística por nivel (Humano +1 y +1 por cada prestigio). */
    public static int pointsPerLevel(Data d) {
        return (d.race == Defs.HUMANO ? 4 : 3) + Defs.P_POINTS * Defs.prestigeLevel(d.prestige);
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
        for (int k = 0; k < 5; k++) {
            for (Holder<Attribute> a : TREE_ATTRS) {
                setMod(p, a, "arbol_raza_" + k, 0.0, Operation.ADD_VALUE);
                setMod(p, a, "arbol_clase_" + k, 0.0, Operation.ADD_VALUE);
            }
        }
        for (int i = 0; i < MUT_ATTRS.size(); i++) {
            setMod(p, MUT_ATTRS.get(i), "mutacion_" + i, 0.0, Operation.ADD_VALUE);
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
            applyTree(p, Trees.tree(true, d.race), d.race >= 0 ? d.raceTree[d.race] : null, "arbol_raza_");
            applyTree(p, Trees.tree(false, d.cls), d.cls >= 0 ? d.classTree[d.cls] : null, "arbol_clase_");
            if (d.isMutated() && d.cls >= 0 && d.cls < MUTATION_MODS.size()) {
                for (Mod m : MUTATION_MODS.get(d.cls)) {
                    setMod(p, m.attr(), "mutacion_" + MUT_ATTRS.indexOf(m.attr()), m.amount(), m.op());
                }
            }
        }
        // Rango de cazador y prestigio: % de daño, de vida y de velocidad
        boolean on = d.awakened == 1;
        int pl = Defs.prestigeLevel(d.prestige);
        setMod(p, Attributes.ATTACK_DAMAGE, "rango_cazador_dano",
                on ? (Defs.RANK_DMG[d.rank()] + Defs.P_DMG * pl) / 100.0 : 0.0, Operation.ADD_MULTIPLIED_TOTAL);
        setMod(p, Attributes.MAX_HEALTH, "rango_cazador_vida",
                on ? (Defs.RANK_HP[d.rank()] + Defs.P_HP * pl) / 100.0 : 0.0, Operation.ADD_MULTIPLIED_TOTAL);
        setMod(p, Attributes.MOVEMENT_SPEED, "rango_cazador_velocidad",
                on ? Defs.RANK_SPEED[d.rank()] / 100.0 : 0.0, Operation.ADD_MULTIPLIED_BASE);
        d.swordOn = false;
        setMod(p, Attributes.ATTACK_DAMAGE, "clase_espada", 0.0, Operation.ADD_VALUE);
        updateSword(p, d);
        if (p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth());
        }
    }

    private static void applyTree(ServerPlayer p, Trees.Node[] tree, int[] ranks, String prefix) {
        if (tree == null || ranks == null) return;
        for (int k = 0; k < tree.length; k++) {
            int a = tree[k].fx() - Trees.ATTR;
            if (a >= 0 && a < TREE_ATTRS.size() && ranks[k] > 0) {
                setMod(p, TREE_ATTRS.get(a), prefix + k, ranks[k] * tree[k].val(), TREE_OPS[a]);
            }
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

    // ================================================================ EXP, niveles y oro

    public static void addXp(ServerPlayer p, int amount) {
        Data d = Store.get(p);
        if (d.awakened != 1 || amount <= 0) return;
        amount = Math.max(1, (int) Math.round(amount * xpMult(d)));
        // Las sombras suben de nivel contigo (también cuando tú ya estás en el nivel máximo)
        Shadows.gainXp(p, d, amount);
        if (d.level >= Defs.MAX_LEVEL) return;
        d.xp += amount;
        notify(p, Net.N_XP, amount, 0);

        int oldLevel = d.level;
        boolean couldExam = Ranking.examReady(d);
        int gained = 0;
        while (d.level < Defs.MAX_LEVEL && d.xp >= Defs.xpNeeded(d.level)) {
            d.xp -= Defs.xpNeeded(d.level);
            d.level++;
            int pts = pointsPerLevel(d);
            d.points += pts;
            gained += pts;
            d.skillPoints++;
            d.spGranted++;
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
            // El rango ya no sube solo: al llegar al nivel del rango siguiente se desbloquea su examen
            if (!couldExam && Ranking.examReady(d)) {
                notify(p, Net.N_EXAM_READY, d.rank() + 1, Defs.RANK_MIN[d.rank() + 1]);
            }
            if (d.level >= Defs.MAX_LEVEL) {
                notify(p, Net.N_MAX_LEVEL, 0, 0);
            }
            Quests.onLevel(p, d);
        }
        sync(p, d);
    }

    /** Suma esencias (la moneda de la herrería y del mercader de la ciudad). */
    public static int addEssences(ServerPlayer p, int amount) {
        Data d = Store.get(p);
        if (d.awakened != 1 || amount <= 0) return 0;
        d.essences += amount;
        notify(p, Net.N_ESSENCES, amount, 0);
        sync(p, d);
        return amount;
    }

    /** Suma oro (con los bonus de los árboles). Devuelve la cantidad final. */
    public static int addCoins(ServerPlayer p, int amount) {
        Data d = Store.get(p);
        if (d.awakened != 1 || amount <= 0) return 0;
        int total = Math.max(1, (int) Math.round(amount * (1.0 + d.fx(Trees.FX_COINS) / 100.0)));
        d.coins += total;
        notify(p, Net.N_COINS, total, 0);
        sync(p, d);
        return total;
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
            Fx.send(lvl, ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 60, 0.6, 1.0, 0.6, 0.15);
            Fx.send(lvl, ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.2, p.getZ(), 80, 0.8, 1.0, 0.8, 0.5);
        }
        double h = age * 0.07;
        for (int k = 0; k < 3; k++) {
            double ang = age * 0.45 + k * (Math.PI * 2.0 / 3.0);
            double x = p.getX() + Math.cos(ang) * 0.9;
            double z = p.getZ() + Math.sin(ang) * 0.9;
            Fx.send(lvl, ParticleTypes.END_ROD, x, p.getY() + h, z, 1, 0.0, 0.0, 0.0, 0.0);
            Fx.send(lvl, ParticleTypes.SOUL_FIRE_FLAME, x, p.getY() + h, z, 1, 0.0, 0.02, 0.0, 0.01);
        }
        d.anim--;
    }

    // ================================================================ Tick del servidor

    public static void tick(MinecraftServer server) {
        int t = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Data d = Store.get(p);
            if (d.cooldown > 0) d.cooldown--;
            if (d.lastStand > 0) d.lastStand--;
            if (d.joinDelay > 0) {
                d.joinDelay--;
                if (d.joinDelay == 0) {
                    notify(p, d.awakened == 1 ? Net.N_WELCOME : Net.N_AWAKEN_PROMPT, 0, 0);
                    if (d.awakened == 1) {
                        Shadows.onJoined(p);
                        Quests.onJoin(p, d);
                    }
                }
            }
            if (d.anim > 0) animTick(p, d);
            // Alas (Dragón): mantener Mayús en el aire para planear
            if (d.awakened == 1 && p.isAlive() && !p.onGround() && p.isShiftKeyDown()
                    && (p.fallDistance > 1.0F || p.hasEffect(MobEffects.SLOW_FALLING)) && d.fx(Trees.FX_GLIDE) > 0) {
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 10, 0, true, false, false));
            }
            if (t % 20 == 0) {
                if (p.isAlive()) d.health = p.getHealth();
                if (d.awakened == 1) {
                    int max = maxMp(d);
                    d.mp = Math.min(max, d.mp + mpRegen(d));
                }
                Dungeons.playerTick(p, d);
                SecretBoss.playerTick(p, d);
                if (d.awakened == 1 && p.isAlive()) Forge.armorTick(p, d);
            }
            if (t % 100 == 0 && d.awakened == 1 && p.isAlive()) {
                double regen = d.fx(Trees.FX_REGEN);
                if (regen > 0 && p.getHealth() < p.getMaxHealth()) p.heal((float) regen);
            }
            if (t % 10 == 0) {
                updateSword(p, d);
                int set = d.awakened == 1 ? ModArmor.fullSet(p) : -1;
                if (set != d.setBonus) {
                    d.setBonus = set;
                    Shadows.refresh(p);
                    if (d.mp > maxMp(d)) d.mp = maxMp(d);
                }
                if (set == ModArmor.SET_CAZADOR) {
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, true, false, true));
                } else if (set == ModArmor.SET_CABALLERO) {
                    p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0, true, false, true));
                }
                if (d.awakened == 1) {
                    if (d.race == Defs.DEMONIO || d.fx(Trees.FX_FIRE_IMMUNE) > 0) {
                        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, true, false, false));
                    }
                    if (d.fx(Trees.FX_NIGHT_VISION) > 0) {
                        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 320, 0, true, false, false));
                    }
                }
                sync(p, d);
            }
        }
        if (t % 6000 == 0) {
            Shadows.snapshot(server);
            Store.saveAll(server);
        }
    }

    // ================================================================ Acciones del cliente

    public static void action(ServerPlayer p, int action, int a, int b) {
        switch (action) {
            case Net.ACT_SKILL -> useSkill(p);
            case Net.ACT_STAT -> addStat(p, a, b);
            case Net.ACT_CHOOSE -> choose(p, a, b);
            case Net.ACT_TREE -> treeUp(p, a, b);
            case Net.ACT_BUY -> Shop.buy(p, a, b);
            case Net.ACT_SELL -> Shop.sellCores(p);
            case Net.ACT_ARISE -> Shadows.arise(p);
            case Net.ACT_SHADOWS_TOGGLE -> Shadows.toggle(p);
            case Net.ACT_QUEST -> Quests.action(p, a, b);
            case Net.ACT_SHADOW -> Shadows.action(p, a, b);
            case Net.ACT_RANK -> Ranking.action(p, a, b);
            case Net.ACT_SHADOW_TREE -> Shadows.treeUp(p, a, b);
            case Net.ACT_SETTINGS -> Fx.set(p, a);
            case Net.ACT_CITY -> City.action(p, a);
            case Net.ACT_FORGE -> Forge.action(p, a, b);
            case Net.ACT_CITY_BUY -> CityShop.buy(p, a, b);
            case Net.ACT_SIDE -> SideQuests.action(p, a, b);
            case Net.ACT_SHADOWS_ALL -> {
                if (a == 1) {
                    if (Shadows.summonAll(p) == 0) notify(p, Net.N_NO_SHADOWS, Store.get(p).shadows.size(), 0);
                } else {
                    Shadows.storeAll(p, false);
                }
            }
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
        notify(p, Net.N_SKILL, d.cls, d.isMutated() ? 1 : 0);
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

    /** Sube un rango de un nudo del árbol indicado (0 raza, 1 clase, 2 sombras). */
    public static void treeUp(ServerPlayer p, int kind, int node) {
        Data d = Store.get(p);
        if (d.awakened != 1 || node < 0 || node > 4 || d.skillPoints <= 0 || kind < 0 || kind > 2) return;
        Trees.Node[] tree = Trees.tree(kind, kind == Trees.KIND_RACE ? d.race : d.cls);
        int[] ranks = d.ranks(kind);
        if (tree == null || ranks == null) return;
        Trees.Node n = tree[node];
        if (ranks[node] >= n.max() || d.level < n.lvl()) return;
        int parent = Trees.PARENT[node];
        if (parent >= 0 && ranks[parent] <= 0) return;
        ranks[node]++;
        d.skillPoints--;
        applyAll(p, d);
        if (kind == Trees.KIND_SHADOW) Shadows.refresh(p);
        p.playNotifySound(SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8F, 1.4F);
        notify(p, Net.N_TREE_UP, kind, node);
        sync(p, d);
        Shadows.sendList(p);
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
        d.coins = 0;
        d.skillPoints = 0;
        d.spGranted = 0;
        d.raceTree = new int[Trees.RACE.length][5];
        d.classTree = new int[Trees.CLASS.length][5];
        d.shadowTree = new int[5];
        d.hunterRank = 0;
        d.examTries = 0;
        d.prestige = 0;
        Quests.wipe(d);
        Shadows.wipe(p);
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
        Dungeons.recover(p, d);
        d.joinDelay = 60;
        sync(p, d);
        Shadows.sendList(p);
    }

    public static void onRespawn(ServerPlayer p) {
        Data d = Store.get(p);
        applyAll(p, d);
        p.setHealth(p.getMaxHealth());
        d.mp = maxMp(d);
        sync(p, d);
    }

    /** Al aparecer una entidad: flechas del Jugador más fuertes, flechas de monstruos con rango y monstruos con rango. */
    public static void onEntityLoad(Entity entity) {
        if (entity instanceof AbstractArrow arrow) {
            if (arrow.getTags().contains(ARROW_TAG)) return;
            Entity owner = arrow.getOwner();
            if (owner instanceof ServerPlayer sp) {
                arrow.addTag(ARROW_TAG);
                Data d = Store.get(sp);
                double b = arrowBonus(d) * Forge.bowMult(sp);
                ItemStack bow = ModItems.SOBERANO[Defs.ARQUERO] == null ? ItemStack.EMPTY
                        : sp.getMainHandItem().is(ModItems.SOBERANO[Defs.ARQUERO]) ? sp.getMainHandItem()
                        : sp.getOffhandItem().is(ModItems.SOBERANO[Defs.ARQUERO]) ? sp.getOffhandItem() : ItemStack.EMPTY;
                if (!bow.isEmpty()) {
                    // Arco del Monarca: flechas críticas, en llamas y con 2,5 veces el daño
                    b *= 2.5;
                    arrow.setCritArrow(true);
                    arrow.setRemainingFireTicks(2000);
                }
                if (b > 1.0) {
                    arrow.setBaseDamage(arrow.getBaseDamage() * b);
                }
                if (d.fx(Trees.FX_FIRE_ARROWS) > 0) {
                    arrow.setRemainingFireTicks(2000);
                }
            } else if (owner instanceof Mob mob && mob.getTags().contains(Ranks.TAG)) {
                // Los arqueros con rango (y las sombras arqueras) disparan más fuerte según su nivel y rango
                arrow.addTag(ARROW_TAG);
                double m = Ranks.arrowMult(mob);
                if (Shadows.isShadow(mob)) m *= Shadows.damageMult(mob);
                if (m > 1.0) arrow.setBaseDamage(arrow.getBaseDamage() * m);
            }
            return;
        }
        if (Shadows.isShadow(entity)) {
            Shadows.onLoad(entity);
            return;
        }
        Ranks.onLoad(entity);
    }
}
