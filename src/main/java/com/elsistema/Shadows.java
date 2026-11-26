package com.elsistema;

import com.elsistema.Data.ShadowRec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RestrictSunGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Ejército de sombras: al matar una criatura su cuerpo suelta humo oscuro unos segundos; con la tecla G
 * («¡Levántate!») el Jugador intenta convertirla en su sombra. Las sombras le siguen y luchan por él,
 * se guardan y se invocan con H y, si caen, vuelven a su sombra para recuperarse.
 */
public final class Shadows {
    private Shadows() {}

    public static final String TAG = "es_sombra";
    private static final String SID = "es_sid:";
    private static final String OWN = "es_own:";
    public static final int STORED = 0;
    public static final int OUT = 1;
    public static final int RECOVERING = 2;
    public static final int BASE_SLOTS = 2;
    public static final int CORPSE_TICKS = 200;
    private static final double ARISE_RANGE = 10.0;
    private static final int MAX_CORPSES = 24;

    /** Criaturas que no pueden ser sombras (su inteligencia no funciona como sombra o son jefes del juego). */
    private static final Set<String> BLOCKED = Set.of(
            "creeper", "piglin", "piglin_brute", "hoglin", "zoglin", "warden", "breeze", "villager",
            "wandering_trader", "axolotl", "frog", "goat", "camel", "allay", "sniffer", "armadillo", "tadpole",
            "wither", "ender_dragon", "elder_guardian", "guardian", "bee", "parrot", "dolphin");
    /** No-muertos que arden al sol: llevan un velo de sombra invisible en la cabeza. */
    private static final Set<String> SUN_UNDEAD = Set.of(
            "zombie", "zombie_villager", "drowned", "skeleton", "stray", "bogged", "phantom");

    private static final class Corpse {
        final UUID owner;
        final ServerLevel level;
        final Vec3 pos;
        final CompoundTag nbt;
        final String type;
        final String key;
        final int rank;
        final int lvl;
        final boolean boss;
        int age = 0;
        int attempts = 3;

        Corpse(UUID owner, ServerLevel level, Vec3 pos, CompoundTag nbt, String type, String key, int rank, int lvl, boolean boss) {
            this.owner = owner;
            this.level = level;
            this.pos = pos;
            this.nbt = nbt;
            this.type = type;
            this.key = key;
            this.rank = rank;
            this.lvl = lvl;
            this.boss = boss;
        }
    }

    private static final List<Corpse> CORPSES = new ArrayList<>();
    /** Sombras fuera: dueño -> (id de sombra -> uuid de la entidad). */
    private static final Map<UUID, Map<Integer, UUID>> ACTIVE = new HashMap<>();
    private static final Map<UUID, Integer> MISSING = new HashMap<>();
    private static final List<Mob> PENDING_DISCARD = new ArrayList<>();
    /** En la prueba automática las sombras sin dueño no se borran. */
    public static boolean selfTest = false;

    // ================================================================ identificar sombras

    public static boolean isShadow(Entity e) {
        return e != null && e.getTags().contains(TAG);
    }

    public static UUID ownerOf(Entity e) {
        if (e == null) return null;
        for (String t : e.getTags()) {
            if (t.startsWith(OWN)) {
                try {
                    return UUID.fromString(t.substring(OWN.length()));
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    public static int sidOf(Entity e) {
        for (String t : e.getTags()) {
            if (t.startsWith(SID)) {
                try {
                    return Integer.parseInt(t.substring(SID.length()));
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        return -1;
    }

    public static ServerPlayer ownerPlayer(Entity e) {
        if (!isShadow(e)) return null;
        UUID id = ownerOf(e);
        MinecraftServer server = e.getServer();
        if (id == null || server == null) return null;
        return server.getPlayerList().getPlayer(id);
    }

    /** Bando de una entidad: el UUID del Jugador para él y para sus sombras. */
    public static UUID sideOf(Entity e) {
        if (e instanceof Player p) return p.getUUID();
        if (isShadow(e)) return ownerOf(e);
        return null;
    }

    public static boolean allied(Entity a, Entity b) {
        UUID sa = sideOf(a);
        return sa != null && sa.equals(sideOf(b));
    }

    public static boolean isOwnedBy(Entity e, Player p) {
        return isShadow(e) && p.getUUID().equals(ownerOf(e));
    }

    // ================================================================ capacidad y datos

    public static int maxSlots(Data d) {
        int m = BASE_SLOTS + (int) Math.round(d.fx(Trees.FX_SHADOW_SLOTS));
        if (d.setBonus == ModArmor.SET_MONARCA) m += 3;
        return m;
    }

    public static int count(Data d) {
        return d.shadows.size();
    }

    public static int outCount(ServerPlayer p) {
        Map<Integer, UUID> map = ACTIVE.get(p.getUUID());
        return map == null ? 0 : map.size();
    }

    private static double power(Data d) {
        double pw = d.fx(Trees.FX_SHADOW_POWER) / 100.0;
        if (d.setBonus == ModArmor.SET_MONARCA) pw += 0.15;
        return pw;
    }

    private static long recoverMs(Data d) {
        return Math.max(10_000L, 60_000L - (long) (15_000L * d.fx(Trees.FX_SHADOW_REGEN)));
    }

    public static boolean canExtract(LivingEntity e) {
        if (!(e instanceof PathfinderMob) || isShadow(e)) return false;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        return !("minecraft".equals(id.getNamespace()) && BLOCKED.contains(id.getPath()));
    }

    // ================================================================ muerte de criaturas -> cadáveres

    /** Se llama después de morir cualquier criatura. */
    public static void onDeath(LivingEntity dead, DamageSource src) {
        if (!(dead.level() instanceof ServerLevel lvl) || dead instanceof Player || isShadow(dead)) return;
        Entity killer = src.getEntity();
        ServerPlayer owner = killer instanceof ServerPlayer sp ? sp : ownerPlayer(killer);
        if (owner == null) return;
        if (killer != null && isShadow(killer)) shadowKill(owner, killer);
        if (!canExtract(dead)) return;
        Data d = Store.get(owner);
        if (d.awakened != 1) return;
        CompoundTag tag = new CompoundTag();
        if (!dead.saveAsPassenger(tag)) return;
        clean(tag);
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()).toString();
        int rank = Math.max(0, Ranks.rankOf(dead));
        int level = Ranks.levelOf(dead);
        int mine = 0;
        for (Corpse c : CORPSES) {
            if (c.owner.equals(owner.getUUID())) mine++;
        }
        if (mine >= MAX_CORPSES) {
            for (Iterator<Corpse> it = CORPSES.iterator(); it.hasNext(); ) {
                if (it.next().owner.equals(owner.getUUID())) {
                    it.remove();
                    break;
                }
            }
        }
        CORPSES.add(new Corpse(owner.getUUID(), lvl, dead.position(), tag, type, dead.getType().getDescriptionId(),
                rank, level, Ranks.isBoss(dead)));
        SystemLogic.notify(owner, Net.N_CORPSE, CORPSE_TICKS / 20, 0);
    }

    /** Quita del NBT guardado lo que no debe copiarse a la sombra. */
    private static void clean(CompoundTag tag) {
        for (String k : new String[]{"UUID", "DeathTime", "HurtTime", "HurtByTimestamp", "Fire", "FallDistance", "Motion",
                "Passengers", "leash", "Leash", "active_effects", "ActiveEffects", "Brain", "AngerTime", "AngryAt", "NoAI"}) {
            tag.remove(k);
        }
        tag.putFloat("Health", 4096.0F);
        tag.putBoolean("PersistenceRequired", true);
        tag.putBoolean("CanPickUpLoot", false);
    }

    private static void shadowKill(ServerPlayer owner, Entity killer) {
        Data d = Store.get(owner);
        ShadowRec rec = d.shadow(sidOf(killer));
        if (rec == null) return;
        rec.kills++;
        int need = 10 + rec.level / 5;
        if (rec.kills % need == 0 && rec.level < Defs.MAX_LEVEL && killer instanceof Mob m) {
            rec.level++;
            prepare(m, rec, owner.getUUID(), d);
            ((ServerLevel) m.level()).sendParticles(ParticleTypes.TOTEM_OF_UNDYING, m.getX(), m.getY() + m.getBbHeight() / 2.0, m.getZ(),
                    25, 0.3, 0.5, 0.3, 0.2);
            SystemLogic.notify(owner, Net.N_SHADOW_LEVEL, rec.id, rec.level);
            sendList(owner);
        }
    }

    // ================================================================ ¡Levántate!

    public static void arise(ServerPlayer p) {
        Data d = Store.get(p);
        if (d.awakened != 1) {
            SystemLogic.notify(p, Net.N_NOT_AWAKENED, 0, 0);
            return;
        }
        ServerLevel lvl = (ServerLevel) p.level();
        List<Corpse> mine = new ArrayList<>();
        for (Corpse c : CORPSES) {
            if (c.owner.equals(p.getUUID()) && c.level == lvl && c.pos.distanceToSqr(p.position()) < ARISE_RANGE * ARISE_RANGE) {
                mine.add(c);
            }
        }
        if (mine.isEmpty()) {
            SystemLogic.notify(p, Net.N_NO_CORPSE, 0, 0);
            return;
        }
        mine.sort(Comparator.comparingInt((Corpse c) -> c.rank).reversed());
        sound(lvl, p.getX(), p.getY(), p.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, 1.0F, 0.6F);
        lvl.sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 0.2, p.getZ(), 30, 1.2, 0.1, 1.2, 0.02);
        int ok = 0;
        int failAttempts = -1;
        for (Corpse c : mine) {
            if (count(d) >= maxSlots(d)) {
                SystemLogic.notify(p, Net.N_ARMY_FULL, count(d), maxSlots(d));
                break;
            }
            int cost = 10 + 5 * c.rank + (c.boss ? 20 : 0);
            if (d.mp < cost && !p.isCreative()) {
                SystemLogic.notify(p, Net.N_ARISE_NO_MP, cost, (int) Math.floor(d.mp));
                break;
            }
            if (!p.isCreative()) d.mp -= cost;
            double chance = 65.0 + 10.0 * (Defs.rank(d.level) - c.rank) + d.fx(Trees.FX_SHADOW_CHANCE)
                    + 0.25 * d.bonus(3) - (c.boss ? 25.0 : 0.0);
            chance = Math.max(5.0, Math.min(95.0, chance));
            if (p.getRandom().nextDouble() * 100.0 < chance) {
                ShadowRec rec = new ShadowRec();
                rec.id = d.nextShadow++;
                rec.type = c.type;
                rec.key = c.key;
                rec.rank = c.rank;
                rec.level = c.lvl;
                rec.boss = c.boss;
                rec.nbt = c.nbt.toString();
                rec.state = STORED;
                d.shadows.add(rec);
                CORPSES.remove(c);
                if (summon(p, d, rec, c.pos)) {
                    ok++;
                    lvl.sendParticles(ParticleTypes.SCULK_SOUL, c.pos.x, c.pos.y + 0.5, c.pos.z, 40, 0.4, 0.8, 0.4, 0.06);
                    lvl.sendParticles(ParticleTypes.REVERSE_PORTAL, c.pos.x, c.pos.y + 1.0, c.pos.z, 60, 0.4, 1.0, 0.4, 0.1);
                    SystemLogic.notify(p, Net.N_ARISE_OK, rec.id, rec.rank);
                }
            } else {
                c.attempts--;
                failAttempts = Math.max(failAttempts, c.attempts);
                lvl.sendParticles(ParticleTypes.LARGE_SMOKE, c.pos.x, c.pos.y + 0.5, c.pos.z, 20, 0.3, 0.4, 0.3, 0.02);
                if (c.attempts <= 0) CORPSES.remove(c);
            }
        }
        if (ok == 0 && failAttempts >= 0) {
            SystemLogic.notify(p, Net.N_ARISE_FAIL, failAttempts, 0);
            sound(lvl, p.getX(), p.getY(), p.getZ(), SoundEvents.DISPENSER_FAIL, 1.0F, 0.6F);
        }
        if (ok > 0) {
            sound(lvl, p.getX(), p.getY(), p.getZ(), SoundEvents.WITHER_SPAWN, 0.35F, 1.6F);
            Quests.onArmy(p, d);
        }
        SystemLogic.sync(p, d);
        sendList(p);
    }

    // ================================================================ invocar y guardar

    private static Vec3 spotNear(ServerPlayer p, int index) {
        double ang = index * 2.4 + p.getRandom().nextDouble() * 0.5;
        double r = 1.5 + (index % 3) * 0.8;
        return new Vec3(p.getX() + Math.cos(ang) * r, p.getY(), p.getZ() + Math.sin(ang) * r);
    }

    /** Saca una sombra guardada junto al Jugador (o en «at»). */
    public static boolean summon(ServerPlayer p, Data d, ShadowRec rec, Vec3 at) {
        if (rec.state != STORED) return false;
        ServerLevel lvl = (ServerLevel) p.level();
        CompoundTag tag;
        try {
            tag = TagParser.parseTag(rec.nbt);
        } catch (Exception e) {
            ElSistema.LOGGER.error("No se pudo leer la sombra {}", rec.id, e);
            return false;
        }
        tag.remove("UUID");
        Optional<Entity> made = EntityType.create(tag, lvl);
        if (made.isEmpty() || !(made.get() instanceof Mob m)) {
            ElSistema.LOGGER.warn("La sombra {} ({}) no se pudo crear", rec.id, rec.type);
            return false;
        }
        Vec3 spot = at != null ? at : spotNear(p, outCount(p));
        m.moveTo(spot.x, spot.y, spot.z, p.getYRot(), 0.0F);
        prepare(m, rec, p.getUUID(), d);
        m.setHealth(Math.max(1.0F, rec.hp * m.getMaxHealth()));
        ACTIVE.computeIfAbsent(p.getUUID(), k -> new HashMap<>()).put(rec.id, m.getUUID());
        rec.state = OUT;
        rec.wasOut = false;
        lvl.addFreshEntity(m);
        lvl.sendParticles(ParticleTypes.SQUID_INK, spot.x, spot.y + 0.1, spot.z, 25, 0.5, 0.05, 0.5, 0.02);
        lvl.sendParticles(ParticleTypes.SCULK_SOUL, spot.x, spot.y + 0.5, spot.z, 10, 0.3, 0.6, 0.3, 0.03);
        return true;
    }

    /** Guarda una sombra que está fuera dentro de la sombra del Jugador. */
    public static void store(ServerPlayer p, Data d, ShadowRec rec, Mob m) {
        CompoundTag tag = new CompoundTag();
        if (m.isAlive() && m.saveAsPassenger(tag)) {
            clean(tag);
            rec.nbt = tag.toString();
        }
        rec.hp = Math.max(0.05F, Math.min(1.0F, m.getHealth() / Math.max(1.0F, m.getMaxHealth())));
        rec.state = STORED;
        Map<Integer, UUID> map = ACTIVE.get(p.getUUID());
        if (map != null) map.remove(rec.id);
        if (m.level() instanceof ServerLevel lvl) {
            lvl.sendParticles(ParticleTypes.SQUID_INK, m.getX(), m.getY() + 0.1, m.getZ(), 20, 0.4, 0.05, 0.4, 0.02);
            lvl.sendParticles(ParticleTypes.LARGE_SMOKE, m.getX(), m.getY() + 0.5, m.getZ(), 10, 0.3, 0.4, 0.3, 0.01);
        }
        m.discard();
    }

    /** Busca la entidad de una sombra que está fuera. */
    private static Mob find(ServerPlayer p, UUID id) {
        Entity e = ((ServerLevel) p.level()).getEntity(id);
        if (e == null && p.getServer() != null) {
            for (ServerLevel l : p.getServer().getAllLevels()) {
                e = l.getEntity(id);
                if (e != null) break;
            }
        }
        return e instanceof Mob m ? m : null;
    }

    private static Mob entityOf(ServerPlayer p, ShadowRec rec) {
        Map<Integer, UUID> map = ACTIVE.get(p.getUUID());
        UUID id = map == null ? null : map.get(rec.id);
        return id == null ? null : find(p, id);
    }

    public static void storeAll(ServerPlayer p, boolean remember) {
        Data d = Store.get(p);
        int n = 0;
        for (ShadowRec rec : d.shadows) {
            if (rec.state != OUT) continue;
            Mob m = entityOf(p, rec);
            if (m != null) {
                store(p, d, rec, m);
            } else {
                rec.state = STORED;
            }
            if (remember) rec.wasOut = true;
            n++;
        }
        ACTIVE.remove(p.getUUID());
        if (n > 0 && !remember) {
            sound((ServerLevel) p.level(), p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, 0.6F, 1.6F);
            SystemLogic.notify(p, Net.N_SHADOWS_IN, n, 0);
        }
        sendList(p);
    }

    public static int summonAll(ServerPlayer p) {
        Data d = Store.get(p);
        int n = 0;
        int allowed = maxSlots(d);
        int out = outCount(p);
        for (ShadowRec rec : d.shadows) {
            if (out >= allowed) break;
            if (rec.state == STORED && summon(p, d, rec, null)) {
                n++;
                out++;
            }
        }
        if (n > 0) {
            sound((ServerLevel) p.level(), p.getX(), p.getY(), p.getZ(), SoundEvents.EVOKER_CAST_SPELL, 1.0F, 0.6F);
            SystemLogic.notify(p, Net.N_SHADOWS_OUT, n, 0);
        }
        sendList(p);
        return n;
    }

    /** Tecla H: si hay sombras fuera las guarda; si no, las saca todas. */
    public static void toggle(ServerPlayer p) {
        Data d = Store.get(p);
        if (d.awakened != 1) {
            SystemLogic.notify(p, Net.N_NOT_AWAKENED, 0, 0);
            return;
        }
        if (outCount(p) > 0) {
            storeAll(p, false);
        } else if (summonAll(p) == 0) {
            SystemLogic.notify(p, Net.N_NO_SHADOWS, d.shadows.size(), 0);
        }
    }

    /** Acciones de la ventana: 0 invocar, 1 guardar, 2 liberar. */
    public static void action(ServerPlayer p, int id, int op) {
        Data d = Store.get(p);
        ShadowRec rec = d.shadow(id);
        if (rec == null || d.awakened != 1) return;
        if (op == 0 && rec.state == STORED) {
            if (outCount(p) >= maxSlots(d)) {
                SystemLogic.notify(p, Net.N_ARMY_FULL, count(d), maxSlots(d));
            } else {
                summon(p, d, rec, null);
                sound((ServerLevel) p.level(), p.getX(), p.getY(), p.getZ(), SoundEvents.EVOKER_CAST_SPELL, 0.8F, 0.8F);
            }
        } else if (op == 1 && rec.state == OUT) {
            Mob m = entityOf(p, rec);
            if (m != null) {
                store(p, d, rec, m);
            } else {
                rec.state = STORED;
                Map<Integer, UUID> map = ACTIVE.get(p.getUUID());
                if (map != null) map.remove(rec.id);
            }
        } else if (op == 2) {
            if (rec.state == OUT) {
                Mob m = entityOf(p, rec);
                if (m != null) {
                    ((ServerLevel) m.level()).sendParticles(ParticleTypes.SOUL, m.getX(), m.getY() + 1.0, m.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
                    m.discard();
                }
                Map<Integer, UUID> map = ACTIVE.get(p.getUUID());
                if (map != null) map.remove(rec.id);
            }
            d.shadows.remove(rec);
        }
        SystemLogic.sync(p, d);
        sendList(p);
    }

    /** Lleva junto al Jugador todas sus sombras (al entrar o salir de una mazmorra). */
    public static void recall(ServerPlayer p) {
        Map<Integer, UUID> map = ACTIVE.get(p.getUUID());
        if (map == null) return;
        int i = 0;
        for (UUID id : new ArrayList<>(map.values())) {
            Mob m = find(p, id);
            if (m != null && m.level() == p.level()) {
                Vec3 s = spotNear(p, i++);
                m.teleportTo(s.x, s.y, s.z);
                m.getNavigation().stop();
                m.resetFallDistance();
                m.setTarget(null);
            }
        }
    }

    /** Nigromante (Dominio de las sombras): Fuerza II y Velocidad II para todas las sombras que están fuera. */
    public static void buffOut(ServerPlayer p, int time) {
        Map<Integer, UUID> map = ACTIVE.get(p.getUUID());
        if (map == null) return;
        for (UUID id : map.values()) {
            Mob m = find(p, id);
            if (m == null) continue;
            m.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, time, 1, false, true));
            m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, time, 1, false, true));
            ((ServerLevel) m.level()).sendParticles(ParticleTypes.SOUL_FIRE_FLAME, m.getX(), m.getY() + m.getBbHeight() / 2.0, m.getZ(),
                    12, 0.3, 0.5, 0.3, 0.02);
        }
    }

    /** Guadaña del Monarca: todas las sombras atacan a quien golpea el Jugador. */
    public static void focus(ServerPlayer p, LivingEntity target) {
        Map<Integer, UUID> map = ACTIVE.get(p.getUUID());
        if (map == null || !validTarget(p, target)) return;
        for (UUID id : map.values()) {
            Mob m = find(p, id);
            if (m != null && m.level() == target.level()) m.setTarget(target);
        }
    }

    // ================================================================ preparar la entidad

    /** Etiquetas, nombre, atributos y equipo de una sombra. */
    public static void prepare(Mob m, ShadowRec rec, UUID owner, Data d) {
        for (String t : new ArrayList<>(m.getTags())) {
            if (t.startsWith(SID) || t.startsWith(OWN) || t.equals("es_dg")) m.removeTag(t);
        }
        m.addTag(TAG);
        m.addTag(SID + rec.id);
        m.addTag(OWN + owner);
        m.setPersistenceRequired();
        m.setCanPickUpLoot(false);
        for (EquipmentSlot s : EquipmentSlot.values()) {
            m.setDropChance(s, 0.0F);
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(m.getType());
        if ("minecraft".equals(id.getNamespace()) && SUN_UNDEAD.contains(id.getPath())
                && m.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && ModItems.VEIL != null) {
            m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.VEIL));
        }
        Ranks.applyStats(m, rec.rank, rec.level, rec.boss);
        if (m instanceof SecretBoss sb) sb.asShadow();
        double pw = d == null ? 0.0 : power(d);
        Ranks.mod(m, Attributes.MAX_HEALTH, "sombra_poder", pw, Operation.ADD_MULTIPLIED_TOTAL);
        Ranks.mod(m, Attributes.ATTACK_DAMAGE, "sombra_poder", pw, Operation.ADD_MULTIPLIED_TOTAL);
        MutableComponent name = Component.literal("☾ Sombra · ").withStyle(ChatFormatting.DARK_PURPLE);
        name.append(Component.translatable(rec.key.isEmpty() ? m.getType().getDescriptionId() : rec.key).withStyle(ChatFormatting.LIGHT_PURPLE));
        name.append(Component.literal(" [" + Defs.RANK[Math.max(0, Math.min(5, rec.rank))] + "] Nv." + rec.level).withStyle(ChatFormatting.GRAY));
        m.setCustomName(name);
        m.setCustomNameVisible(false);
        m.clearFire();
    }

    /** Recalcula el poder de las sombras que están fuera (al cambiar el árbol o la armadura). */
    public static void refresh(ServerPlayer p) {
        Data d = Store.get(p);
        for (ShadowRec rec : d.shadows) {
            if (rec.state != OUT) continue;
            Mob m = entityOf(p, rec);
            if (m != null) {
                float frac = m.getHealth() / Math.max(1.0F, m.getMaxHealth());
                prepare(m, rec, p.getUUID(), d);
                m.setHealth(Math.max(1.0F, frac * m.getMaxHealth()));
            }
        }
    }

    /** ENTITY_LOAD: pone la inteligencia de sombra o borra las sombras huérfanas. */
    public static void onLoad(Entity e) {
        if (!(e instanceof Mob m) || !isShadow(m)) return;
        UUID owner = ownerOf(m);
        Map<Integer, UUID> map = owner == null ? null : ACTIVE.get(owner);
        boolean valid = map != null && m.getUUID().equals(map.get(sidOf(m)));
        if (!valid && !selfTest) {
            PENDING_DISCARD.add(m);
            return;
        }
        addGoals(m);
    }

    private static boolean hasGoal(Mob m, Class<?> c) {
        for (WrappedGoal w : m.goalSelector.getAvailableGoals()) {
            if (c.isInstance(w.getGoal())) return true;
        }
        return false;
    }

    public static void addGoals(Mob m) {
        if (hasGoal(m, ShadowGoals.Follow.class)) return;
        m.goalSelector.removeAllGoals(g -> g instanceof RandomStrollGoal || g instanceof AvoidEntityGoal
                || g instanceof FleeSunGoal || g instanceof RestrictSunGoal);
        m.targetSelector.removeAllGoals(g -> true);
        m.setTarget(null);
        m.goalSelector.addGoal(1, new ShadowGoals.Follow(m));
        m.targetSelector.addGoal(1, new ShadowGoals.Defend(m));
        if (m instanceof PathfinderMob pm && !(m instanceof Enemy) && m.getAttribute(Attributes.ATTACK_DAMAGE) != null
                && !hasGoal(m, MeleeAttackGoal.class)) {
            m.goalSelector.addGoal(2, new MeleeAttackGoal(pm, 1.2, true));
        }
    }

    public static boolean validTarget(Player owner, LivingEntity t) {
        if (t == null || !t.isAlive() || t == owner) return false;
        if (allied(owner, t)) return false;
        if (t instanceof net.minecraft.world.entity.decoration.ArmorStand) return false;
        if (t instanceof net.minecraft.world.entity.TamableAnimal ta && ta.isOwnedBy(owner)) return false;
        return !(t instanceof Player pl) || pl.isAlive() && !pl.isCreative() && !pl.isSpectator();
    }

    // ================================================================ muerte de una sombra

    /** ALLOW_DEATH: una sombra nunca muere del todo, vuelve a la sombra de su dueño a recuperarse. */
    public static boolean allowDeath(LivingEntity e, DamageSource src, float amount) {
        if (!isShadow(e) || !(e instanceof Mob m)) return true;
        m.setHealth(m.getMaxHealth());
        ServerPlayer p = ownerPlayer(m);
        Data d = p == null ? null : Store.get(p);
        ShadowRec rec = d == null ? null : d.shadow(sidOf(m));
        if (rec == null) {
            m.discard();
            return false;
        }
        store(p, d, rec, m);
        rec.hp = 1.0F;
        rec.state = RECOVERING;
        long ms = recoverMs(d);
        rec.recoverAt = System.currentTimeMillis() + ms;
        SystemLogic.notify(p, Net.N_SHADOW_BACK, (int) (ms / 1000L), rec.id);
        sendList(p);
        return false;
    }

    // ================================================================ tick

    public static void tick(MinecraftServer server) {
        if (!PENDING_DISCARD.isEmpty()) {
            for (Mob m : PENDING_DISCARD) {
                if (m.isAlive()) m.discard();
            }
            PENDING_DISCARD.clear();
        }
        int t = server.getTickCount();
        for (Iterator<Corpse> it = CORPSES.iterator(); it.hasNext(); ) {
            Corpse c = it.next();
            c.age++;
            if (c.age > CORPSE_TICKS || c.attempts <= 0) {
                it.remove();
                continue;
            }
            if (c.age % 5 == 0) {
                c.level.sendParticles(ParticleTypes.SMOKE, c.pos.x, c.pos.y + 0.3, c.pos.z, 3, 0.25, 0.1, 0.25, 0.01);
                c.level.sendParticles(new DustParticleOptions(new Vector3f(0.35F, 0.1F, 0.55F), 1.4F),
                        c.pos.x, c.pos.y + 0.4, c.pos.z, 3, 0.3, 0.3, 0.3, 0.0);
                if (c.age % 20 == 0) {
                    c.level.sendParticles(ParticleTypes.SOUL, c.pos.x, c.pos.y + 0.5, c.pos.z, 2, 0.2, 0.3, 0.2, 0.02);
                }
            }
        }
        long now = System.currentTimeMillis();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Data d = Store.get(p);
            boolean changed = false;
            if (t % 20 == 0) {
                for (ShadowRec rec : d.shadows) {
                    if (rec.state == RECOVERING && now >= rec.recoverAt) {
                        rec.state = STORED;
                        changed = true;
                    }
                }
            }
            Map<Integer, UUID> map = ACTIVE.get(p.getUUID());
            if (map != null && !map.isEmpty()) {
                int index = 0;
                for (Integer sid : new ArrayList<>(map.keySet())) {
                    index++;
                    ShadowRec rec = d.shadow(sid);
                    UUID eid = map.get(sid);
                    if (rec == null) {
                        map.remove(sid);
                        continue;
                    }
                    Mob m = find(p, eid);
                    if (m == null || !m.isAlive()) {
                        int miss = MISSING.merge(eid, 1, Integer::sum);
                        if (miss > 40) {
                            map.remove(sid);
                            MISSING.remove(eid);
                            rec.state = STORED;
                            changed = true;
                        }
                        continue;
                    }
                    MISSING.remove(eid);
                    if (m.level() != p.level()) {
                        store(p, d, rec, m);
                        summon(p, d, rec, null);
                        changed = true;
                        continue;
                    }
                    if (p.isAlive() && m.distanceToSqr(p) > 24.0 * 24.0) {
                        Vec3 s = spotNear(p, index);
                        m.teleportTo(s.x, s.y, s.z);
                        m.getNavigation().stop();
                        m.resetFallDistance();
                        ((ServerLevel) m.level()).sendParticles(ParticleTypes.SQUID_INK, s.x, s.y + 0.1, s.z, 12, 0.4, 0.05, 0.4, 0.02);
                    }
                    if ((t + index) % 10 == 0) {
                        m.clearFire();
                        ((ServerLevel) m.level()).sendParticles(new DustParticleOptions(new Vector3f(0.3F, 0.05F, 0.5F), 1.0F),
                                m.getX(), m.getY() + 0.15, m.getZ(), 2, m.getBbWidth() * 0.4, 0.05, m.getBbWidth() * 0.4, 0.0);
                    }
                    if (t % 20 == 0) {
                        double regen = d.fx(Trees.FX_SHADOW_REGEN);
                        if (regen > 0 && m.getHealth() < m.getMaxHealth()) m.heal((float) regen);
                        rec.hp = Math.max(0.05F, Math.min(1.0F, m.getHealth() / Math.max(1.0F, m.getMaxHealth())));
                    }
                }
            }
            if (changed || t % 40 == 0 && (map != null && !map.isEmpty() || hasRecovering(d))) {
                sendList(p);
            }
        }
    }

    private static boolean hasRecovering(Data d) {
        for (ShadowRec r : d.shadows) {
            if (r.state == RECOVERING) return true;
        }
        return false;
    }

    /** Guarda en los datos el estado actual de las sombras que están fuera (antes de guardar en disco). */
    public static void snapshot(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Data d = Store.get(p);
            for (ShadowRec rec : d.shadows) {
                if (rec.state != OUT) continue;
                Mob m = entityOf(p, rec);
                if (m == null) continue;
                CompoundTag tag = new CompoundTag();
                if (m.saveAsPassenger(tag)) {
                    clean(tag);
                    rec.nbt = tag.toString();
                }
            }
        }
    }

    /** Al entrar: vuelven a salir las sombras que estaban fuera cuando se fue. */
    public static void onJoined(ServerPlayer p) {
        Data d = Store.get(p);
        int n = 0;
        int allowed = maxSlots(d);
        for (ShadowRec rec : d.shadows) {
            if (rec.state == OUT && entityOf(p, rec) != null) {
                rec.wasOut = false;
                n++;
                continue;
            }
            if (rec.state == OUT) rec.state = STORED;
            if (rec.wasOut && rec.state == STORED && n < allowed && summon(p, d, rec, null)) n++;
            rec.wasOut = false;
        }
        sendList(p);
    }

    public static void shutdown(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            try {
                storeAll(p, true);
            } catch (Exception e) {
                ElSistema.LOGGER.error("Error al guardar las sombras", e);
            }
        }
        ACTIVE.clear();
        CORPSES.clear();
        MISSING.clear();
        PENDING_DISCARD.clear();
    }

    public static void forget(ServerPlayer p) {
        CORPSES.removeIf(c -> c.owner.equals(p.getUUID()));
    }

    /** Borra todo el ejército (comando /sistema reiniciar). */
    public static void wipe(ServerPlayer p) {
        Data d = Store.get(p);
        for (ShadowRec rec : d.shadows) {
            if (rec.state == OUT) {
                Mob m = entityOf(p, rec);
                if (m != null) m.discard();
            }
        }
        ACTIVE.remove(p.getUUID());
        d.shadows.clear();
        sendList(p);
    }

    // ================================================================ red

    /** Envía al cliente la lista para la pestaña Sombras. */
    public static void sendList(ServerPlayer p) {
        Data d = Store.get(p);
        List<Integer> nums = new ArrayList<>();
        List<String> strs = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (ShadowRec r : d.shadows) {
            nums.add(r.id);
            nums.add(r.rank);
            nums.add(r.level);
            nums.add(r.state);
            nums.add(Math.round(r.hp * 100.0F));
            nums.add(r.state == RECOVERING ? (int) Math.max(0L, (r.recoverAt - now + 999L) / 1000L) : 0);
            nums.add(r.kills | (r.boss ? 1 << 24 : 0));
            strs.add(r.type);
            strs.add(r.key);
        }
        SystemLogic.sendShadows(p, nums, strs);
    }

    private static void sound(ServerLevel lvl, double x, double y, double z, SoundEvent s, float volume, float pitch) {
        lvl.playSound((Player) null, x, y, z, s, SoundSource.PLAYERS, volume, pitch);
    }

    /** Para la prueba automática del cliente: registro de sombra hecho a partir de una criatura viva. */
    public static ShadowRec recordFrom(Mob source, int id) {
        CompoundTag tag = new CompoundTag();
        if (!source.saveAsPassenger(tag)) return null;
        clean(tag);
        ShadowRec rec = new ShadowRec();
        rec.id = id;
        rec.type = BuiltInRegistries.ENTITY_TYPE.getKey(source.getType()).toString();
        rec.key = source.getType().getDescriptionId();
        rec.rank = Math.max(0, Ranks.rankOf(source));
        rec.level = Ranks.levelOf(source);
        rec.boss = Ranks.isBoss(source);
        rec.nbt = tag.toString();
        rec.state = STORED;
        return rec;
    }

    /** Para la prueba automática: convierte una criatura en sombra sin dueño. */
    public static Mob testShadow(Mob source, ServerLevel lvl) {
        CompoundTag tag = new CompoundTag();
        if (!source.saveAsPassenger(tag)) return null;
        clean(tag);
        String snbt = tag.toString();
        CompoundTag back;
        try {
            back = TagParser.parseTag(snbt);
        } catch (Exception e) {
            throw new IllegalStateException("SNBT", e);
        }
        Optional<Entity> made = EntityType.create(back, lvl);
        if (made.isEmpty() || !(made.get() instanceof Mob m)) return null;
        ShadowRec rec = new ShadowRec();
        rec.id = 9999;
        rec.type = BuiltInRegistries.ENTITY_TYPE.getKey(source.getType()).toString();
        rec.key = source.getType().getDescriptionId();
        rec.rank = Math.max(0, Ranks.rankOf(source));
        rec.level = Ranks.levelOf(source);
        m.moveTo(source.getX() + 2.0, source.getY(), source.getZ(), 0.0F, 0.0F);
        prepare(m, rec, UUID.randomUUID(), null);
        lvl.addFreshEntity(m);
        return m;
    }

}
