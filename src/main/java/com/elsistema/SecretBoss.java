package com.elsistema;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Jefes secretos: solo aparecen en condiciones raras y tienen ataques especiales y barra de jefe propia.
 * <ul>
 *   <li>Guardián del Juicio: el jefe de la prueba del cambio de clase.</li>
 *   <li>Bestia del Eclipse: algunas noches cerca de cazadores de nivel 30 o más. Huye al amanecer.</li>
 *   <li>Reina Carmesí: a veces sustituye al jefe en las mazmorras de rango B, A y S.</li>
 *   <li>Coloso Primordial: a veces sustituye al jefe en las mazmorras de rango S.</li>
 * </ul>
 * Se les puede extraer la sombra con ¡Levántate! como a cualquier otro jefe.
 */
public class SecretBoss extends DungeonMonster {
    public enum Kind { GUARDIAN, ECLIPSE, QUEEN, PRIMORDIAL }

    public static final String[] NAME = {"Guardián del Juicio", "Bestia del Eclipse", "Reina Carmesí", "Coloso Primordial"};
    public static final String[] TITLE = {"El que pone a prueba a los elegidos", "Despierta cuando la luna mira",
            "Señora de todos los nidos", "Más antiguo que el propio Sistema"};
    private static final String DG = "es_dg";
    private static final String NIGHT_TAG = "es_eclipse";

    private static UUID eclipseId;

    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.literal("Jefe secreto"),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private final List<UUID> minions = new ArrayList<>();
    private final List<Cloud> clouds = new ArrayList<>();
    private int cdA = 60;
    private int cdB = 100;
    private int phase = 0;

    private static final class Cloud {
        final Vec3 pos;
        int life;

        Cloud(Vec3 pos, int life) {
            this.pos = pos;
            this.life = life;
        }
    }

    public SecretBoss(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 120;
    }

    public Kind secretKind() {
        return ModEntities.secretKindOf(this.getType());
    }

    /** Atributos base de cada jefe secreto (antes del nivel). */
    public static AttributeSupplier.Builder attributes(Kind k) {
        double hp;
        double dmg;
        double speed;
        double armor;
        double kb;
        switch (k) {
            case GUARDIAN -> { hp = 700; dmg = 16; speed = 0.29; armor = 12; kb = 0.8; }
            case ECLIPSE -> { hp = 600; dmg = 14; speed = 0.36; armor = 8; kb = 0.7; }
            case QUEEN -> { hp = 850; dmg = 18; speed = 0.32; armor = 14; kb = 0.8; }
            default -> { hp = 1024; dmg = 28; speed = 0.24; armor = 18; kb = 1.0; }
        }
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, hp)
                .add(Attributes.ATTACK_DAMAGE, dmg)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.ARMOR, armor)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, kb)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    private void base(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double v) {
        AttributeInstance a = this.getAttribute(attr);
        if (a != null) a.setBaseValue(v);
    }

    /** Rango, nivel, vida, daño y nombre del jefe secreto. */
    public void setup(int rank, int level) {
        rank = Math.max(0, Math.min(5, rank));
        level = Math.max(1, Math.min(Defs.MAX_LEVEL, level));
        Kind k = secretKind();
        Ranks.apply(this, rank, level, true);
        Ranks.mod(this, Attributes.MAX_HEALTH, "rango_vida", 0.0, Operation.ADD_MULTIPLIED_TOTAL);
        Ranks.mod(this, Attributes.ATTACK_DAMAGE, "rango_dano", 0.0, Operation.ADD_MULTIPLIED_TOTAL);
        Ranks.mod(this, Attributes.ARMOR, "rango_armadura", 0.0, Operation.ADD_VALUE);
        Ranks.mod(this, Attributes.SCALE, "rango_tamano", 0.2, Operation.ADD_VALUE);
        double hp;
        double dmg;
        switch (k) {
            case GUARDIAN -> { hp = 520 + 6.0 * level; dmg = 9 + 0.18 * level; }
            case ECLIPSE -> { hp = 380 + 6.0 * level; dmg = 8 + 0.18 * level; }
            case QUEEN -> { hp = 560 + 5.0 * level; dmg = 9 + 0.18 * level; }
            default -> { hp = 1024; dmg = 12 + 0.2 * level; }
        }
        base(Attributes.MAX_HEALTH, Math.min(1024.0, hp));
        base(Attributes.ATTACK_DAMAGE, dmg);
        this.setHealth(this.getMaxHealth());
        MutableComponent name = Component.literal("☠ " + NAME[k.ordinal()] + " ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        name.append(Component.literal("[" + Defs.RANK[rank] + "] Nv." + level).withStyle(ChatFormatting.WHITE));
        this.setCustomName(name);
        this.setCustomNameVisible(true);
    }

    /** Al convertirse en sombra: sin barra de jefe, tamaño normal y sin los multiplicadores enormes de jefe. */
    public void asShadow() {
        Ranks.mod(this, Attributes.MAX_HEALTH, "rango_vida", 0.0, Operation.ADD_MULTIPLIED_TOTAL);
        Ranks.mod(this, Attributes.ATTACK_DAMAGE, "rango_dano", 0.0, Operation.ADD_MULTIPLIED_TOTAL);
        Ranks.mod(this, Attributes.SCALE, "rango_tamano", 0.0, Operation.ADD_VALUE);
        this.bossEvent.removeAllPlayers();
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        SpawnGroupData spawnGroupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        if (this.secretKind() == Kind.GUARDIAN && ModItems.HOJA_JUICIO != null) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.HOJA_JUICIO));
        }
        if (spawnType == MobSpawnType.SPAWN_EGG || spawnType == MobSpawnType.COMMAND) {
            Player near = level.getNearestPlayer(this, 64.0);
            int lv = near instanceof ServerPlayer sp ? Math.min(Defs.MAX_LEVEL, Store.get(sp).level + 5) : 50;
            this.setup(Defs.rank(lv), lv);
        }
        return data;
    }

    // ================================================================ barra de jefe

    private boolean showsBar() {
        return !Shadows.isShadow(this) && !this.getTags().contains(DG);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (this.showsBar()) this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(name != null ? name : this.getType().getDescription());
    }

    // ================================================================ inteligencia

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(this.level() instanceof ServerLevel lvl)) return;
        boolean shadow = Shadows.isShadow(this);
        if (!this.showsBar()) {
            if (!this.bossEvent.getPlayers().isEmpty()) this.bossEvent.removeAllPlayers();
        } else {
            this.bossEvent.setProgress(Mth.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F));
        }
        Kind k = this.secretKind();
        if (!shadow && k == Kind.ECLIPSE && this.getTags().contains(NIGHT_TAG) && this.tickCount > 40 && lvl.isDay()) {
            this.flee(lvl);
            return;
        }
        this.tickClouds(lvl);
        if (this.cdA > 0) this.cdA--;
        if (this.cdB > 0) this.cdB--;
        LivingEntity t = this.getTarget();
        if (t == null || !t.isAlive() || t.level() != lvl) return;
        double dist = this.distanceTo(t);
        float frac = this.getHealth() / Math.max(1.0F, this.getMaxHealth());
        switch (k) {
            case GUARDIAN -> {
                if (this.cdA == 0 && dist < 5.0) {
                    this.cdA = 120;
                    this.slam(lvl, 5.0, 0.9F, 0.55);
                } else if (this.cdB == 0 && dist > 6.0 && dist < 18.0) {
                    this.cdB = 110;
                    this.charge(lvl, t, 1.7, 0.35);
                }
                if (!shadow && this.phase == 0 && frac < 0.5F) {
                    this.phase = 1;
                    this.enrage(lvl, SoundEvents.WITHER_SPAWN);
                    this.summon(lvl, ModEntities.CABALLERO, 2, t);
                }
            }
            case ECLIPSE -> {
                if (this.cdA == 0 && dist > 3.5 && dist < 14.0) {
                    this.cdA = 70;
                    this.charge(lvl, t, 1.5, 0.45);
                }
                if (this.cdB == 0 && dist < 24.0) {
                    this.cdB = 320;
                    this.howl(lvl, shadow, t);
                }
                if (!shadow && this.phase == 0 && frac < 0.5F) {
                    this.phase = 1;
                    this.enrage(lvl, SoundEvents.WOLF_GROWL);
                }
            }
            case QUEEN -> {
                if (this.cdA == 0 && dist < 16.0) {
                    this.cdA = 150;
                    this.clouds.add(new Cloud(t.position(), 120));
                    this.sound(lvl, SoundEvents.SPIDER_AMBIENT, 1.4F, 0.5F);
                }
                if (!shadow && this.phase == 0 && frac < 0.66F) {
                    this.phase = 1;
                    this.summon(lvl, ModEntities.HORMIGA, 3, t);
                    this.sound(lvl, SoundEvents.EVOKER_PREPARE_SUMMON, 1.2F, 0.6F);
                }
                if (!shadow && this.phase == 1 && frac < 0.33F) {
                    this.phase = 2;
                    this.summon(lvl, ModEntities.HORMIGA, 3, t);
                    this.enrage(lvl, SoundEvents.SPIDER_AMBIENT);
                }
            }
            default -> {
                if (this.cdA == 0 && dist < 10.0) {
                    this.cdA = 140;
                    this.quake(lvl, 8.0);
                }
                if (!shadow && this.phase == 0 && frac < 0.5F) {
                    this.phase = 1;
                    this.enrage(lvl, SoundEvents.ENDER_DRAGON_GROWL);
                }
            }
        }
    }

    /** ¿Le afecta un ataque de área? Un jefe normal ataca a jugadores y sombras; una sombra, a los monstruos. */
    private boolean foe(LivingEntity e) {
        if (e == this || !e.isAlive()) return false;
        if (Shadows.isShadow(this)) {
            if (Shadows.allied(this, e)) return false;
            if (e instanceof Player) return false;
            return e instanceof Enemy || e == this.getTarget();
        }
        if (e instanceof Player pl) return !pl.isCreative() && !pl.isSpectator();
        return Shadows.isShadow(e) || e == this.getTarget();
    }

    private void sound(ServerLevel lvl, SoundEvent s, float volume, float pitch) {
        lvl.playSound((Player) null, this.getX(), this.getY(), this.getZ(), s, SoundSource.HOSTILE, volume, pitch);
    }

    /** Golpe del Juicio: golpe en área que lanza a todos por los aires. */
    private void slam(ServerLevel lvl, double r, float mult, double up) {
        float dmg = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * mult);
        for (LivingEntity e : lvl.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(r, 2.0, r), this::foe)) {
            if (e.distanceToSqr(this) > r * r) continue;
            e.hurt(this.damageSources().mobAttack(this), dmg);
            Vec3 out = new Vec3(e.getX() - this.getX(), 0.0, e.getZ() - this.getZ());
            if (out.lengthSqr() > 1.0E-4) out = out.normalize().scale(1.1);
            e.push(out.x, up, out.z);
            e.hurtMarked = true;
        }
        for (int i = 0; i < 24; i++) {
            double ang = i * Math.PI * 2.0 / 24.0;
            lvl.sendParticles(ParticleTypes.SWEEP_ATTACK, this.getX() + Math.cos(ang) * r * 0.7, this.getY() + 0.4,
                    this.getZ() + Math.sin(ang) * r * 0.7, 1, 0.0, 0.0, 0.0, 0.0);
        }
        lvl.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.5, this.getZ(), 6, r * 0.4, 0.2, r * 0.4, 0.0);
        lvl.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 0.5, this.getZ(), 40, r * 0.5, 0.3, r * 0.5, 0.05);
        this.sound(lvl, SoundEvents.ANVIL_LAND, 1.2F, 0.5F);
        this.sound(lvl, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.6F);
    }

    /** Embestida: salta hacia su objetivo. */
    private void charge(ServerLevel lvl, LivingEntity t, double speed, double up) {
        Vec3 dir = new Vec3(t.getX() - this.getX(), 0.0, t.getZ() - this.getZ());
        if (dir.lengthSqr() < 1.0E-4) return;
        dir = dir.normalize();
        this.setDeltaMovement(dir.x * speed, up, dir.z * speed);
        lvl.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.3, this.getZ(), 15, 0.4, 0.1, 0.4, 0.05);
        this.sound(lvl, SoundEvents.RAVAGER_ROAR, 1.0F, 1.2F);
    }

    /** Aullido del eclipse: oscuridad a su alrededor y una manada de lobos. */
    private void howl(ServerLevel lvl, boolean shadow, LivingEntity t) {
        this.sound(lvl, SoundEvents.WOLF_HOWL, 2.0F, 0.5F);
        for (LivingEntity e : lvl.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(20.0), this::foe)) {
            e.addEffect(new MobEffectInstance(shadow ? MobEffects.WEAKNESS : MobEffects.DARKNESS, 100, 0), this);
        }
        lvl.sendParticles(ParticleTypes.SCULK_SOUL, this.getX(), this.getY() + 1.2, this.getZ(), 30, 1.0, 0.6, 1.0, 0.05);
        lvl.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 0.3, this.getZ(), 40, 2.5, 0.1, 2.5, 0.02);
        if (!shadow) this.summon(lvl, ModEntities.LOBO, 3, t);
    }

    /** Nubes de veneno carmesí de la reina. */
    private void tickClouds(ServerLevel lvl) {
        if (this.clouds.isEmpty()) return;
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.78F, 0.06F, 0.22F), 1.8F);
        for (Iterator<Cloud> it = this.clouds.iterator(); it.hasNext(); ) {
            Cloud c = it.next();
            c.life--;
            if (c.life % 5 == 0) {
                lvl.sendParticles(dust, c.pos.x, c.pos.y + 0.4, c.pos.z, 14, 1.6, 0.3, 1.6, 0.0);
                lvl.sendParticles(ParticleTypes.WITCH, c.pos.x, c.pos.y + 0.6, c.pos.z, 4, 1.4, 0.3, 1.4, 0.0);
            }
            if (c.life % 10 == 0) {
                AABB box = new AABB(c.pos.x - 3.0, c.pos.y - 1.0, c.pos.z - 3.0, c.pos.x + 3.0, c.pos.y + 2.5, c.pos.z + 3.0);
                for (LivingEntity e : lvl.getEntitiesOfClass(LivingEntity.class, box, this::foe)) {
                    e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1), this);
                    e.hurt(this.damageSources().indirectMagic(this, this), 1.0F);
                }
            }
            if (c.life <= 0) it.remove();
        }
    }

    /** Terremoto del coloso: todo lo que está cerca sale volando y queda ralentizado. */
    private void quake(ServerLevel lvl, double r) {
        float dmg = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.75);
        for (LivingEntity e : lvl.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(r, 3.0, r), this::foe)) {
            if (e.distanceToSqr(this) > r * r) continue;
            e.hurt(this.damageSources().mobAttack(this), dmg);
            Vec3 out = new Vec3(e.getX() - this.getX(), 0.0, e.getZ() - this.getZ());
            if (out.lengthSqr() > 1.0E-4) out = out.normalize().scale(0.6);
            e.push(out.x, 0.9, out.z);
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
        }
        DustParticleOptions rock = new DustParticleOptions(new Vector3f(0.42F, 0.36F, 0.32F), 2.4F);
        DustParticleOptions magma = new DustParticleOptions(new Vector3f(1.0F, 0.45F, 0.1F), 1.6F);
        for (int ring = 1; ring <= 3; ring++) {
            double rr = r * ring / 3.0;
            int n = 10 * ring;
            for (int i = 0; i < n; i++) {
                double ang = i * Math.PI * 2.0 / n;
                double x = this.getX() + Math.cos(ang) * rr;
                double z = this.getZ() + Math.sin(ang) * rr;
                lvl.sendParticles(rock, x, this.getY() + 0.2, z, 2, 0.2, 0.1, 0.2, 0.0);
                if (i % 3 == 0) lvl.sendParticles(magma, x, this.getY() + 0.4, z, 1, 0.1, 0.3, 0.1, 0.0);
            }
        }
        lvl.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.5, this.getZ(), 8, r * 0.4, 0.2, r * 0.4, 0.0);
        lvl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getX(), this.getY() + 0.3, this.getZ(), 20, r * 0.4, 0.2, r * 0.4, 0.02);
        this.sound(lvl, SoundEvents.ANVIL_LAND, 1.6F, 0.35F);
        this.sound(lvl, SoundEvents.IRON_GOLEM_ATTACK, 1.6F, 0.5F);
    }

    /** Fase de furia: más fuerte y más rápido hasta el final del combate. */
    private void enrage(ServerLevel lvl, SoundEvent roar) {
        this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 60 * 20, 0, false, true));
        this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 60 * 20, 0, false, true));
        this.bossEvent.setColor(BossEvent.BossBarColor.PURPLE);
        lvl.sendParticles(ParticleTypes.ANGRY_VILLAGER, this.getX(), this.getY() + this.getBbHeight(), this.getZ(), 8, 0.6, 0.3, 0.6, 0.0);
        lvl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 1.0, this.getZ(), 40, 0.8, 1.0, 0.8, 0.05);
        this.sound(lvl, roar, 1.6F, 0.6F);
    }

    /** Invoca ayudantes (desaparecen cuando muere el jefe). */
    private void summon(ServerLevel lvl, EntityType<? extends Mob> type, int n, LivingEntity t) {
        this.minions.removeIf(id -> {
            Entity e = lvl.getEntity(id);
            return e == null || !e.isAlive();
        });
        int rank = Math.max(0, Ranks.rankOf(this));
        int level = Math.max(1, Ranks.levelOf(this) - 5);
        RandomSource r = this.getRandom();
        for (int i = 0; i < n && this.minions.size() < 6; i++) {
            double ang = r.nextDouble() * Math.PI * 2.0;
            BlockPos pos = BlockPos.containing(this.getX() + Math.cos(ang) * 2.5, this.getY(), this.getZ() + Math.sin(ang) * 2.5);
            Mob m;
            Ranks.spawning = true;
            try {
                m = type.spawn(lvl, pos, MobSpawnType.MOB_SUMMONED);
            } finally {
                Ranks.spawning = false;
            }
            if (m == null) continue;
            m.setPersistenceRequired();
            if (this.getTags().contains(DG)) m.addTag(DG);
            Ranks.apply(m, rank, level, false);
            m.setTarget(t);
            this.minions.add(m.getUUID());
            lvl.sendParticles(ParticleTypes.REVERSE_PORTAL, m.getX(), m.getY() + 1.0, m.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
        }
    }

    private void clearMinions(ServerLevel lvl) {
        for (UUID id : this.minions) {
            Entity e = lvl.getEntity(id);
            if (e != null && e.isAlive()) {
                lvl.sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 0.8, e.getZ(), 15, 0.3, 0.5, 0.3, 0.02);
                e.discard();
            }
        }
        this.minions.clear();
    }

    /** La Bestia del Eclipse se desvanece al amanecer. */
    private void flee(ServerLevel lvl) {
        lvl.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.0, this.getZ(), 60, 0.8, 1.0, 0.8, 0.05);
        lvl.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + 1.0, this.getZ(), 60, 0.8, 1.0, 0.8, 0.1);
        this.sound(lvl, SoundEvents.WOLF_HOWL, 1.5F, 0.4F);
        for (ServerPlayer o : lvl.players()) {
            if (o.distanceToSqr(this) < 96 * 96) SystemLogic.notify(o, Net.N_SECRET_FLED, this.secretKind().ordinal(), 0);
        }
        this.clearMinions(lvl);
        this.bossEvent.removeAllPlayers();
        this.discard();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        this.bossEvent.removeAllPlayers();
        if (this.level() instanceof ServerLevel lvl) {
            this.clearMinions(lvl);
            lvl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, this.getX(), this.getY() + this.getBbHeight() / 2.0, this.getZ(),
                    80, 1.0, 1.5, 1.0, 0.4);
            this.sound(lvl, SoundEvents.WITHER_DEATH, 0.5F, 1.4F);
        }
    }

    // ================================================================ aparecer

    /** Crea un jefe secreto en pos. dungeon = dentro de una mazmorra (usa la barra de la mazmorra). */
    public static SecretBoss spawn(ServerLevel lvl, Kind k, BlockPos pos, int rank, int level, boolean dungeon) {
        EntityType<SecretBoss> type = ModEntities.secretType(k);
        SecretBoss b = type.create(lvl);
        if (b == null) return null;
        float yaw = lvl.getRandom().nextFloat() * 360.0F;
        b.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
        b.yHeadRot = yaw;
        b.yBodyRot = yaw;
        b.finalizeSpawn(lvl, lvl.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null);
        b.setPersistenceRequired();
        if (dungeon) b.addTag(DG);
        b.setup(rank, level);
        if (!lvl.addFreshEntity(b)) return null;
        lvl.sendParticles(ParticleTypes.REVERSE_PORTAL, b.getX(), b.getY() + 1.0, b.getZ(), 80, 0.6, 1.4, 0.6, 0.15);
        lvl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, b.getX(), b.getY() + 0.2, b.getZ(), 40, 1.2, 0.1, 1.2, 0.02);
        return b;
    }

    /** Cada segundo: la Bestia del Eclipse puede aparecer de noche cerca de un cazador de nivel 30 o más. */
    public static void playerTick(ServerPlayer p, Data d) {
        if (d.awakened != 1 || d.level < 30 || d.inDungeon == 1 || !p.isAlive() || p.isSpectator()) return;
        if (!(p.level() instanceof ServerLevel lvl) || lvl.dimension() != Level.OVERWORLD || !lvl.isNight()) {
            d.bossTimer = 0;
            return;
        }
        if (++d.bossTimer < 60) return;
        d.bossTimer = 0;
        if (eclipseId != null) {
            Entity e = lvl.getEntity(eclipseId);
            if (e != null && e.isAlive()) return;
        }
        int chance = lvl.getMoonPhase() == 0 ? 120 : 40;
        if (p.getRandom().nextInt(1000) >= chance) return;
        spawnNear(p, Kind.ECLIPSE, false);
    }

    /** Hace aparecer un jefe secreto cerca del Jugador (lejos, de noche; o delante, con /sistema jefe). */
    public static SecretBoss spawnNear(ServerPlayer p, Kind k, boolean ahead) {
        ServerLevel lvl = (ServerLevel) p.level();
        RandomSource r = p.getRandom();
        Data d = Store.get(p);
        int level = Math.min(Defs.MAX_LEVEL, Math.max(1, d.level + 5));
        Vec3 look = p.getLookAngle();
        double lookAng = Math.atan2(look.z, look.x);
        for (int tries = 0; tries < 16; tries++) {
            double ang = ahead ? lookAng + (r.nextDouble() - 0.5) * 0.4 * (tries / 4) : r.nextDouble() * Math.PI * 2.0;
            double dist = ahead ? 7 + tries % 4 : 18 + r.nextInt(9);
            int x = Mth.floor(p.getX() + Math.cos(ang) * dist);
            int z = Mth.floor(p.getZ() + Math.sin(ang) * dist);
            if (!lvl.hasChunkAt(x, z)) continue;
            int y = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - p.getBlockY()) > 16) continue;
            BlockPos pos = new BlockPos(x, y, z);
            if (!lvl.getFluidState(pos.below()).isEmpty()) continue;
            if (!lvl.getBlockState(pos).isAir() || !lvl.getBlockState(pos.above()).isAir()
                    || !lvl.getBlockState(pos.above(2)).isAir()) continue;
            SecretBoss b = spawn(lvl, k, pos, Defs.rank(level), level, false);
            if (b == null) return null;
            if (k == Kind.ECLIPSE && !ahead) {
                b.addTag(NIGHT_TAG);
                eclipseId = b.getUUID();
            }
            b.setTarget(p);
            Quests.secretBossAppeared(lvl, b, false);
            return b;
        }
        return null;
    }

    public static Kind parse(String s) {
        return switch (s.toLowerCase(java.util.Locale.ROOT)) {
            case "guardian", "guardián", "juicio" -> Kind.GUARDIAN;
            case "eclipse", "bestia" -> Kind.ECLIPSE;
            case "reina", "carmesi", "carmesí" -> Kind.QUEEN;
            case "coloso", "primordial" -> Kind.PRIMORDIAL;
            default -> null;
        };
    }

    public static void reset() {
        eclipseId = null;
    }

    /** Prueba automática: usa todas sus habilidades una vez contra «t». */
    public void selfTestAbilities(ServerLevel lvl, LivingEntity t) {
        this.setTarget(t);
        this.slam(lvl, 5.0, 0.2F, 0.2);
        this.charge(lvl, t, 0.6, 0.1);
        this.howl(lvl, false, t);
        this.clouds.add(new Cloud(t.position(), 20));
        for (int i = 0; i < 20; i++) {
            this.tickClouds(lvl);
        }
        this.quake(lvl, 6.0);
        this.enrage(lvl, SoundEvents.ENDER_DRAGON_GROWL);
        this.summon(lvl, ModEntities.GOBLIN, 1, t);
        this.bossEvent.setProgress(0.5F);
        this.clearMinions(lvl);
    }
}
