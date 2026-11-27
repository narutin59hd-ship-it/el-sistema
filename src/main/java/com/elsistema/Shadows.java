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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RestrictSunGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
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
 * («¡Levántate!») el Jugador intenta convertirla en su sombra (3 intentos por cadáver; cuanto más alto el rango
 * del muerto, más maná cuesta y menos probabilidad hay). Las sombras le siguen y atacan (más fuerte cuanto mejor
 * es la criatura y su rango), se guardan y se invocan con H y, si caen, quedan inhabilitadas 3 días de Minecraft.
 */
public final class Shadows {
    private Shadows() {}

    public static final String TAG = "es_sombra";
    private static final String SID = "es_sid:";
    private static final String OWN = "es_own:";
    /** Daño por golpe (x10) de las sombras que atacan con el golpe de sombra (animales sin ataque propio). */
    private static final String ATK = "es_atk:";
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
        int attempts = Defs.ARISE_TRIES;

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
    /** Prueba automática: ¡Levántate! siempre sale bien. */
    public static boolean forceArise = false;

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
        if (d.awakened == 1) {
            m += Defs.RANK_SLOTS[d.rank()];
            m += Defs.P_SLOTS * Defs.prestigeLevel(d.prestige);
        }
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

    private static int clampRank(int r) {
        return Math.max(0, Math.min(5, r));
    }

    // ================================================================ daño de las sombras

    /** Daño base de una criatura: su ataque, o (si no ataca, como los animales) según su vida. */
    static double baseDamage(EntityType<?> type) {
        if (type == null) return 2.0;
        try {
            if (DefaultAttributes.hasSupplier(type)) {
                @SuppressWarnings("unchecked")
                AttributeSupplier sup = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type);
                if (sup.hasAttribute(Attributes.ATTACK_DAMAGE)) return Math.max(1.0, sup.getBaseValue(Attributes.ATTACK_DAMAGE));
                if (sup.hasAttribute(Attributes.MAX_HEALTH)) {
                    return Math.max(1.0, Math.min(6.0, sup.getBaseValue(Attributes.MAX_HEALTH) / 4.0));
                }
            }
        } catch (Exception ignored) {
            // criatura rara de otro mod: daño por defecto
        }
        return 2.0;
    }

    /** Multiplicador de daño de una sombra por su rango, el poder de las sombras de su dueño y su árbol propio. */
    public static double damageMult(Mob m) {
        double mult = Defs.SHADOW_RANK_DMG[clampRank(Ranks.rankOf(m))];
        ServerPlayer p = ownerPlayer(m);
        if (p != null) {
            Data d = Store.get(p);
            mult *= 1.0 + power(d);
            ShadowRec rec = d.shadow(sidOf(m));
            if (rec != null) mult *= 1.0 + ShadowTree.value(rec.tree, ShadowTree.DMG) / 100.0;
        }
        return mult;
    }

    /** Daño por golpe de una sombra (el que se ve en la pestaña Sombras): criatura, nivel, rango, poder y árbol. */
    public static double attackOf(ShadowRec rec, Data d) {
        EntityType<?> type = EntityType.byString(rec.type).orElse(null);
        double mult = (1.0 + 0.03 * rec.level + (rec.boss ? 0.5 : 0.0)) * Defs.SHADOW_RANK_DMG[clampRank(rec.rank)];
        if (d != null) mult *= 1.0 + power(d);
        mult *= 1.0 + ShadowTree.value(rec.tree, ShadowTree.DMG) / 100.0;
        return baseDamage(type) * mult;
    }

    /** Registro de la sombra de una entidad (o null si no es una sombra con dueño conectado). */
    public static ShadowRec recOf(Entity e) {
        ServerPlayer p = ownerPlayer(e);
        return p == null ? null : Store.get(p).shadow(sidOf(e));
    }

    /** Daño del golpe de sombra (para las criaturas que no tienen ataque propio). */
    public static float strikeDamage(Mob m) {
        for (String t : m.getTags()) {
            if (t.startsWith(ATK)) {
                try {
                    return Math.max(1.0F, Integer.parseInt(t.substring(ATK.length())) / 10.0F);
                } catch (NumberFormatException ignored) {
                    break;
                }
            }
        }
        return 2.0F;
    }

    // ================================================================ probabilidad y coste de ¡Levántate!

    /** Probabilidad (%) de levantar a un muerto: baja con su rango, sube con tu rango, la Perspicacia y la INT. */
    public static int ariseChance(Data d, int rank, boolean boss) {
        int r = clampRank(rank);
        double c = Defs.ARISE_BASE[r];
        int diff = d.rank() - r;
        c += diff > 0 ? 5.0 * diff : 10.0 * diff;
        c += d.fx(Trees.FX_SHADOW_CHANCE);
        c += Math.min(15.0, 0.1 * d.bonus(3));
        c += Defs.RANK_ARISE[d.rank()];
        if (boss) c *= 0.6;
        return (int) Math.round(Math.max(3.0, Math.min(95.0, c)));
    }

    /** Maná que cuesta cada intento: sube con el rango del muerto (los jefes, un 50% más). */
    public static int ariseCost(int rank, boolean boss) {
        int c = Defs.ARISE_COST[clampRank(rank)];
        return boss ? c + c / 2 : c;
    }

    /** Envía al HUD la probabilidad, los intentos y el coste del mejor cadáver cercano. */
    private static void sendCorpseInfo(ServerPlayer p, Data d) {
        Corpse best = null;
        for (Corpse c : CORPSES) {
            if (!c.owner.equals(p.getUUID()) || c.level != p.level() || c.attempts <= 0) continue;
            if (c.pos.distanceToSqr(p.position()) > ARISE_RANGE * ARISE_RANGE * 4) continue;
            if (best == null || c.rank > best.rank || c.rank == best.rank && c.age < best.age) best = c;
        }
        if (best == null) return;
        SystemLogic.notify(p, Net.N_CORPSE_INFO, ariseChance(d, best.rank, best.boss),
                best.attempts + 10 * ariseCost(best.rank, best.boss));
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
        if (killer != null && isShadow(killer)) shadowKill(owner, killer, dead);
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
        boolean boss = Ranks.isBoss(dead);
        CORPSES.add(new Corpse(owner.getUUID(), lvl, dead.position(), tag, type, dead.getType().getDescriptionId(),
                rank, level, boss));
        SystemLogic.notify(owner, Net.N_CORPSE, CORPSE_TICKS / 20, 0);
        SystemLogic.notify(owner, Net.N_CORPSE_INFO, ariseChance(d, rank, boss),
                Defs.ARISE_TRIES + 10 * ariseCost(rank, boss));
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

    /** Una sombra ha matado a una criatura: cuenta la baja y gana la EXP de esa criatura (además de la compartida). */
    private static void shadowKill(ServerPlayer owner, Entity killer, LivingEntity dead) {
        Data d = Store.get(owner);
        ShadowRec rec = d.shadow(sidOf(killer));
        if (rec == null) return;
        rec.kills++;
        if (d.awakened == 1 && rec.state != RECOVERING) {
            int gained = giveXp(owner, d, rec, Ranks.killXp(dead));
            if (gained > 0) {
                SystemLogic.notify(owner, Net.N_SHADOW_LEVEL, rec.id, rec.level);
                sendList(owner);
            }
        }
        DIRTY.add(owner.getUUID());
    }

    // ================================================================ EXP y árbol propio de cada sombra

    /** Jugadores cuya lista de sombras ha cambiado (EXP) y hay que reenviar al cliente. */
    private static final Set<UUID> DIRTY = new java.util.HashSet<>();

    /**
     * Las sombras suben de nivel contigo: cada vez que ganas EXP, todas tus sombras (menos las caídas) ganan
     * la misma cantidad. Usan la misma tabla de EXP que el Jugador, así que las de nivel bajo te alcanzan rápido.
     */
    public static void gainXp(ServerPlayer p, Data d, int amount) {
        if (amount <= 0 || d.shadows.isEmpty()) return;
        int leveled = 0;
        ShadowRec last = null;
        for (ShadowRec rec : d.shadows) {
            if (rec.state == RECOVERING) continue;
            if (giveXp(p, d, rec, amount) > 0) {
                leveled++;
                last = rec;
            }
        }
        if (leveled == 1) {
            SystemLogic.notify(p, Net.N_SHADOW_LEVEL, last.id, last.level);
        } else if (leveled > 1) {
            int pts = 0;
            for (ShadowRec rec : d.shadows) {
                pts += rec.pts;
            }
            SystemLogic.notify(p, Net.N_SHADOWS_LEVEL, leveled, pts);
        }
        if (leveled > 0) {
            sendList(p);
        } else {
            DIRTY.add(p.getUUID());
        }
    }

    /** Da EXP a una sombra. Devuelve los niveles que ha subido (cada uno le da puntos para su árbol). */
    public static int giveXp(ServerPlayer p, Data d, ShadowRec rec, int amount) {
        if (amount <= 0 || rec.level >= Defs.MAX_LEVEL) return 0;
        rec.xp += amount;
        int gained = 0;
        while (rec.level < Defs.MAX_LEVEL && rec.xp >= Defs.xpNeeded(rec.level)) {
            rec.xp -= Defs.xpNeeded(rec.level);
            rec.level++;
            rec.pts += ShadowTree.POINTS_PER_LEVEL;
            gained++;
        }
        if (rec.level >= Defs.MAX_LEVEL) rec.xp = 0;
        if (gained > 0 && rec.state == OUT) {
            Mob m = entityOf(p, rec);
            if (m != null) {
                float frac = m.getHealth() / Math.max(1.0F, m.getMaxHealth());
                prepare(m, rec, p.getUUID(), d);
                m.setHealth(Math.max(1.0F, frac * m.getMaxHealth()));
                Fx.send((ServerLevel) m.level(), ParticleTypes.TOTEM_OF_UNDYING, m.getX(), m.getY() + m.getBbHeight() / 2.0,
                        m.getZ(), 20, 0.3, 0.5, 0.3, 0.2);
            }
        }
        return gained;
    }

    /** Pestaña Sombras: gasta los puntos de una sombra en un nudo de su árbol propio. */
    public static void treeUp(ServerPlayer p, int id, int node) {
        Data d = Store.get(p);
        ShadowRec rec = d.shadow(id);
        if (rec == null || d.awakened != 1) return;
        rec.tree = ShadowTree.fix(rec.tree);
        if (!ShadowTree.canUp(rec.tree, rec.pts, node)) {
            sendList(p);
            return;
        }
        rec.pts -= ShadowTree.NODES[node].cost();
        rec.tree[node]++;
        if (rec.state == OUT) {
            Mob m = entityOf(p, rec);
            if (m != null) {
                float frac = m.getHealth() / Math.max(1.0F, m.getMaxHealth());
                prepare(m, rec, p.getUUID(), d);
                m.setHealth(Math.max(1.0F, frac * m.getMaxHealth()));
                Fx.send((ServerLevel) m.level(), ParticleTypes.WITCH, m.getX(), m.getY() + m.getBbHeight() / 2.0, m.getZ(),
                        12, 0.3, 0.4, 0.3, 0.05);
            }
        }
        SystemLogic.notify(p, Net.N_SHADOW_TREE, rec.id, node * 100 + rec.tree[node]);
        sendList(p);
    }

    /** Comando /sistema sombras: puntos de árbol para todas las sombras. */
    public static int addPointsAll(ServerPlayer p, int n) {
        Data d = Store.get(p);
        for (ShadowRec rec : d.shadows) {
            rec.pts = Math.max(0, rec.pts + n);
        }
        sendList(p);
        return d.shadows.size();
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
        Fx.send(lvl, ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 0.2, p.getZ(), 30, 1.2, 0.1, 1.2, 0.02);
        int ok = 0;
        int failAttempts = -1;
        for (Corpse c : mine) {
            if (count(d) >= maxSlots(d)) {
                SystemLogic.notify(p, Net.N_ARMY_FULL, count(d), maxSlots(d));
                break;
            }
            int cost = ariseCost(c.rank, c.boss);
            if (d.mp < cost && !p.isCreative()) {
                SystemLogic.notify(p, Net.N_ARISE_NO_MP, cost, (int) Math.floor(d.mp));
                break;
            }
            if (!p.isCreative()) d.mp -= cost;
            int chance = ariseChance(d, c.rank, c.boss);
            if (forceArise || p.getRandom().nextInt(100) < chance) {
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
                if (summon(p, d, rec, c.pos, ARISE, ok * 6)) {
                    ok++;
                    SystemLogic.notify(p, Net.N_ARISE_OK, rec.id, rec.rank);
                }
            } else {
                c.attempts--;
                failAttempts = Math.max(failAttempts, c.attempts);
                Fx.send(lvl, ParticleTypes.LARGE_SMOKE, c.pos.x, c.pos.y + 0.5, c.pos.z, 20, 0.3, 0.4, 0.3, 0.02);
                if (c.attempts <= 0) CORPSES.remove(c);
            }
        }
        if (ok == 0 && failAttempts >= 0) {
            SystemLogic.notify(p, Net.N_ARISE_FAIL, failAttempts, 0);
            sound(lvl, p.getX(), p.getY(), p.getZ(), SoundEvents.DISPENSER_FAIL, 1.0F, 0.6F);
        }
        if (ok > 0) {
            sound(lvl, p.getX(), p.getY(), p.getZ(), SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.5F, 0.6F);
            Quests.onArmy(p, d);
            SideQuests.onArise(p, d, ok);
        }
        sendCorpseInfo(p, d);
        SystemLogic.sync(p, d);
        sendList(p);
    }

    // ================================================================ invocar y guardar

    private static Vec3 spotNear(ServerPlayer p, int index) {
        double ang = index * 2.4 + p.getRandom().nextDouble() * 0.5;
        double r = 1.5 + (index % 3) * 0.8;
        return new Vec3(p.getX() + Math.cos(ang) * r, p.getY(), p.getZ() + Math.sin(ang) * r);
    }

    /** Saca una sombra guardada junto al Jugador (o en «at»): sale del suelo. */
    public static boolean summon(ServerPlayer p, Data d, ShadowRec rec, Vec3 at) {
        return summon(p, d, rec, at, RISE, 0);
    }

    /** Saca una sombra con una animación (RISE: sale deprisa; ARISE: ¡Levántate!) y «delay» ticks de espera. */
    public static boolean summon(ServerPlayer p, Data d, ShadowRec rec, Vec3 at, int anim, int delay) {
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
        Anim an = new Anim(m, lvl, spot, anim, Math.max(0, delay));
        an.hide();
        lvl.addFreshEntity(m);
        ANIMS.add(an);
        return true;
    }

    /** Guarda una sombra que está fuera dentro de la sombra del Jugador (se hunde en el suelo). */
    public static void store(ServerPlayer p, Data d, ShadowRec rec, Mob m) {
        store(p, d, rec, m, true);
    }

    /** Guarda una sombra; con «sink» se hunde en el suelo antes de desaparecer (si no, desaparece al momento). */
    public static void store(ServerPlayer p, Data d, ShadowRec rec, Mob m, boolean sink) {
        Anim old = animOf(m);
        if (old != null) {
            old.restore();
            ANIMS.remove(old);
        }
        CompoundTag tag = new CompoundTag();
        if (m.isAlive() && m.saveAsPassenger(tag)) {
            clean(tag);
            rec.nbt = tag.toString();
        }
        rec.hp = Math.max(0.05F, Math.min(1.0F, m.getHealth() / Math.max(1.0F, m.getMaxHealth())));
        rec.state = STORED;
        Map<Integer, UUID> map = ACTIVE.get(p.getUUID());
        if (map != null) map.remove(rec.id);
        if (sink && m.isAlive() && m.level() instanceof ServerLevel lvl) {
            Anim an = new Anim(m, lvl, m.position(), SINK, 0);
            an.freeze();
            ANIMS.add(an);
            return;
        }
        if (m.level() instanceof ServerLevel lvl) {
            Fx.send(lvl, ParticleTypes.SQUID_INK, m.getX(), m.getY() + 0.1, m.getZ(), 20, 0.4, 0.05, 0.4, 0.02);
            Fx.send(lvl, ParticleTypes.LARGE_SMOKE, m.getX(), m.getY() + 0.5, m.getZ(), 10, 0.3, 0.4, 0.3, 0.01);
        }
        m.discard();
    }

    // ================================================================ animaciones: salir del suelo y hundirse

    /** Invocar (tecla H, al entrar...): la sombra sale deprisa de un charco de oscuridad. */
    public static final int RISE = 0;
    /** ¡Levántate!: humo y almas que se juntan sobre el cadáver y la sombra sale despacio del suelo. */
    public static final int ARISE = 1;
    /** Guardar: la sombra se hunde en el suelo y desaparece. */
    public static final int SINK = 2;
    private static final int GATHER_TICKS = 16;
    private static final List<Anim> ANIMS = new ArrayList<>();
    private static final DustParticleOptions DARK_DUST = new DustParticleOptions(new Vector3f(0.22F, 0.04F, 0.38F), 1.3F);
    private static final DustParticleOptions ARISE_DUST = new DustParticleOptions(new Vector3f(0.45F, 0.12F, 0.85F), 1.5F);

    private static final class Anim {
        final Mob m;
        final ServerLevel lvl;
        final double x;
        final double y;
        final double z;
        final double depth;
        final int kind;
        /** Ticks de espera (la sombra sigue escondida bajo tierra); en ¡Levántate! incluye cuando se junta el humo. */
        final int delay;
        final int dur;
        final boolean noAi;
        final boolean invulnerable;
        final boolean noGravity;
        int age = 0;

        Anim(Mob m, ServerLevel lvl, Vec3 at, int kind, int delay) {
            this.m = m;
            this.lvl = lvl;
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
            this.depth = m.getBbHeight() + 0.25;
            this.kind = kind;
            this.delay = delay + (kind == ARISE ? GATHER_TICKS : 0);
            this.dur = kind == ARISE ? 34 : kind == SINK ? 12 : 14;
            this.noAi = m.isNoAi();
            this.invulnerable = m.isInvulnerable();
            this.noGravity = m.isNoGravity();
        }

        /** Quieta, sin chocar y sin recibir daño mientras dura la animación. */
        void freeze() {
            m.setNoAi(true);
            m.setInvulnerable(true);
            m.setNoGravity(true);
            m.noPhysics = true;
            m.setDeltaMovement(Vec3.ZERO);
        }

        /** Empieza escondida bajo tierra. */
        void hide() {
            freeze();
            m.moveTo(x, y - depth, z, m.getYRot(), 0.0F);
        }

        void restore() {
            m.setNoAi(noAi);
            m.setInvulnerable(invulnerable);
            m.setNoGravity(noGravity);
            m.noPhysics = false;
        }
    }

    private static Anim animOf(Mob m) {
        for (Anim an : ANIMS) {
            if (an.m == m) return an;
        }
        return null;
    }

    /** True mientras la sombra está saliendo del suelo o hundiéndose. */
    public static boolean animating(Entity e) {
        return e instanceof Mob m && animOf(m) != null;
    }

    private static float easeOut(float t) {
        float u = 1F - t;
        return 1F - u * u * u;
    }

    private static void tickAnims() {
        for (Iterator<Anim> it = ANIMS.iterator(); it.hasNext(); ) {
            Anim an = it.next();
            Mob m = an.m;
            if (m.isRemoved() || m.level() != an.lvl) {
                it.remove();
                continue;
            }
            an.age++;
            int t = an.age - an.delay;
            if (t < 0) {
                m.setPos(an.x, an.y - an.depth, an.z);
                m.setDeltaMovement(Vec3.ZERO);
                if (an.kind == ARISE && t >= -GATHER_TICKS) gather(an, t + GATHER_TICKS);
                continue;
            }
            float p = Math.min(1F, t / (float) an.dur);
            double off = an.kind == SINK ? an.depth * p * p : an.depth * (1F - easeOut(p));
            m.setPos(an.x, an.y - off, an.z);
            m.setDeltaMovement(Vec3.ZERO);
            animFx(an, t, p);
            if (p >= 1F) {
                it.remove();
                if (an.kind == SINK) {
                    Fx.send(an.lvl, ParticleTypes.SOUL, an.x, an.y + 0.2, an.z, 4, 0.3, 0.1, 0.3, 0.02);
                    m.discard();
                } else {
                    an.restore();
                    m.setPos(an.x, an.y, an.z);
                    finishFx(an);
                }
            }
        }
    }

    /** ¡Levántate!, primera parte: el humo y las almas se juntan sobre el cadáver. */
    private static void gather(Anim an, int t) {
        ServerLevel l = an.lvl;
        double r = 1.8 - 1.2 * (t / (double) GATHER_TICKS);
        double base = an.age * 0.35;
        for (int i = 0; i < 8; i++) {
            double a = base + i * Math.PI / 4.0;
            Fx.send(l, ARISE_DUST, an.x + Math.cos(a) * r, an.y + 0.08, an.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
        }
        // Almas que vuelan hacia el centro (cantidad 0 = dirección y velocidad)
        double a = an.age * 1.7;
        double sx = Math.cos(a) * 2.2;
        double sz = Math.sin(a) * 2.2;
        // Partículas de portal: aparecen fuera y vuelan hacia el cadáver
        Fx.send(l, ParticleTypes.PORTAL, an.x, an.y + 0.6, an.z, 0, sx, 0.8, sz, 1.0);
        Fx.send(l, ParticleTypes.PORTAL, an.x, an.y + 0.6, an.z, 0, -sz, 0.5, sx, 1.0);
        Fx.send(l, ParticleTypes.LARGE_SMOKE, an.x, an.y + 0.1, an.z, 0, 0.0, 1.0, 0.0, 0.09);
        if (t % 4 == 0) Fx.send(l, ParticleTypes.SQUID_INK, an.x, an.y + 0.05, an.z, 4, 0.5, 0.02, 0.5, 0.01);
        if (t == 0) sound(l, an.x, an.y, an.z, SoundEvents.SCULK_CATALYST_BLOOM, 1.2F, 0.6F);
        if (t == GATHER_TICKS - 4) sound(l, an.x, an.y, an.z, SoundEvents.WARDEN_SONIC_CHARGE, 0.6F, 1.4F);
    }

    /** Mientras sale o se hunde: círculo de oscuridad en el suelo, tinta y almas. */
    private static void animFx(Anim an, int t, float p) {
        ServerLevel l = an.lvl;
        double w = Math.max(0.5, an.m.getBbWidth() * 0.8);
        double base = an.age * 0.45;
        int dots = an.kind == ARISE ? 6 : 4;
        double r = an.kind == SINK ? w * (1.0 - p * 0.6) : w * (0.7 + 0.3 * p);
        for (int i = 0; i < dots; i++) {
            double a = base + i * Math.PI * 2.0 / dots;
            Fx.send(l, an.kind == ARISE ? ARISE_DUST : DARK_DUST, an.x + Math.cos(a) * r, an.y + 0.06, an.z + Math.sin(a) * r,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        Fx.send(l, ParticleTypes.SQUID_INK, an.x, an.y + 0.05, an.z, 1, w * 0.4, 0.02, w * 0.4, 0.01);
        if (an.kind == ARISE) {
            double h = an.m.getBbHeight() * p;
            Fx.send(l, ParticleTypes.REVERSE_PORTAL, an.x, an.y + h * 0.5, an.z, 3, w * 0.5, Math.max(0.2, h * 0.4), w * 0.5, 0.05);
            if (t % 2 == 0) Fx.send(l, ParticleTypes.SCULK_SOUL, an.x, an.y + 0.2, an.z, 1, w * 0.5, 0.1, w * 0.5, 0.04);
            if (t == 0) sound(l, an.x, an.y, an.z, SoundEvents.WARDEN_EMERGE, 0.8F, 1.3F);
        } else if (an.kind == RISE) {
            if (t % 2 == 0) Fx.send(l, ParticleTypes.SCULK_SOUL, an.x, an.y + 0.3, an.z, 1, w * 0.4, 0.2, w * 0.4, 0.03);
            if (t == 0) sound(l, an.x, an.y, an.z, SoundEvents.SCULK_BLOCK_SPREAD, 1.0F, 0.7F);
        } else {
            Fx.send(l, ParticleTypes.LARGE_SMOKE, an.x, an.y + 0.3, an.z, 1, w * 0.3, 0.2, w * 0.3, 0.01);
            if (t == 0) sound(l, an.x, an.y, an.z, SoundEvents.SCULK_BLOCK_BREAK, 1.0F, 0.6F);
        }
    }

    /** Ya ha salido: estallido de almas (más grande en ¡Levántate!). */
    private static void finishFx(Anim an) {
        ServerLevel l = an.lvl;
        double h = an.m.getBbHeight();
        if (an.kind == ARISE) {
            Fx.send(l, ParticleTypes.SCULK_SOUL, an.x, an.y + h * 0.5, an.z, 30, 0.5, h * 0.4, 0.5, 0.08);
            Fx.send(l, ParticleTypes.END_ROD, an.x, an.y + h * 0.6, an.z, 12, 0.4, h * 0.3, 0.4, 0.06);
            Fx.send(l, ParticleTypes.LARGE_SMOKE, an.x, an.y + 0.3, an.z, 10, 0.6, 0.2, 0.6, 0.03);
            sound(l, an.x, an.y, an.z, SoundEvents.WITHER_SPAWN, 0.35F, 1.6F);
            sound(l, an.x, an.y, an.z, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.5F);
        } else {
            Fx.send(l, ParticleTypes.SOUL, an.x, an.y + h * 0.4, an.z, 5, 0.3, h * 0.3, 0.3, 0.03);
        }
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

    /** La entidad de una sombra que está fuera (o null). */
    public static Mob outEntity(ServerPlayer p, ShadowRec rec) {
        return rec == null ? null : entityOf(p, rec);
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
                store(p, d, rec, m, !remember);
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
            if (rec.state == STORED && summon(p, d, rec, null, RISE, Math.min(24, n * 3))) {
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
                    Fx.send((ServerLevel) m.level(), ParticleTypes.SOUL, m.getX(), m.getY() + 1.0, m.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
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
                Anim an = animOf(m);
                if (an != null) {
                    an.restore();
                    ANIMS.remove(an);
                }
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
            Fx.send((ServerLevel) m.level(), ParticleTypes.SOUL_FIRE_FLAME, m.getX(), m.getY() + m.getBbHeight() / 2.0, m.getZ(),
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
        Ranks.clearDifficulty(m);
        if (m instanceof SecretBoss sb) sb.asShadow();
        double pw = d == null ? 0.0 : power(d);
        Ranks.mod(m, Attributes.MAX_HEALTH, "sombra_poder", pw, Operation.ADD_MULTIPLIED_TOTAL);
        Ranks.mod(m, Attributes.ATTACK_DAMAGE, "sombra_poder", pw, Operation.ADD_MULTIPLIED_TOTAL);
        // Cuanto mejor es el rango de la sombra, más daño hace
        Ranks.mod(m, Attributes.ATTACK_DAMAGE, "sombra_rango", Defs.SHADOW_RANK_DMG[clampRank(rec.rank)] - 1.0,
                Operation.ADD_MULTIPLIED_TOTAL);
        // Árbol propio de la sombra
        rec.tree = ShadowTree.fix(rec.tree);
        Ranks.mod(m, Attributes.ATTACK_DAMAGE, "sombra_garras", ShadowTree.value(rec.tree, ShadowTree.DMG) / 100.0,
                Operation.ADD_MULTIPLIED_TOTAL);
        Ranks.mod(m, Attributes.MAX_HEALTH, "sombra_cuerpo", ShadowTree.value(rec.tree, ShadowTree.HP) / 100.0,
                Operation.ADD_MULTIPLIED_TOTAL);
        Ranks.mod(m, Attributes.ARMOR, "sombra_coraza", ShadowTree.value(rec.tree, ShadowTree.ARMOR), Operation.ADD_VALUE);
        Ranks.mod(m, Attributes.MOVEMENT_SPEED, "sombra_paso", ShadowTree.value(rec.tree, ShadowTree.SPEED) / 100.0,
                Operation.ADD_MULTIPLIED_BASE);
        for (String t : new ArrayList<>(m.getTags())) {
            if (t.startsWith(ATK)) m.removeTag(t);
        }
        if (m.getAttribute(Attributes.ATTACK_DAMAGE) == null) {
            m.addTag(ATK + Math.max(10, (int) Math.round(attackOf(rec, d) * 10.0)));
        }
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
                || g instanceof FleeSunGoal || g instanceof RestrictSunGoal || g instanceof PanicGoal
                || g instanceof TemptGoal || g instanceof BreedGoal || g instanceof FollowParentGoal);
        m.targetSelector.removeAllGoals(g -> true);
        m.setTarget(null);
        m.goalSelector.addGoal(1, new ShadowGoals.Follow(m));
        m.targetSelector.addGoal(1, new ShadowGoals.Defend(m));
        if (m.getAttribute(Attributes.ATTACK_DAMAGE) == null) {
            // Criaturas sin ataque propio (vacas, ovejas, caballos...): como sombras también golpean
            m.goalSelector.addGoal(2, new ShadowGoals.Strike(m));
        } else if (m instanceof PathfinderMob pm && !(m instanceof Enemy) && !hasGoal(m, MeleeAttackGoal.class)) {
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

    /**
     * ALLOW_DEATH: cuando matan a una sombra se deshace en humo y vuelve a la sombra de su dueño, pero queda
     * inhabilitada 3 días de Minecraft (no se puede invocar hasta entonces). No se pierde ni suelta botín.
     */
    public static boolean allowDeath(LivingEntity e, DamageSource src, float amount) {
        if (!isShadow(e) || !(e instanceof Mob m)) return true;
        m.setHealth(m.getMaxHealth());
        ServerPlayer p = ownerPlayer(m);
        Data d = p == null ? null : Store.get(p);
        ShadowRec rec = d == null ? null : d.shadow(sidOf(m));
        if (rec == null || p.getServer() == null) {
            m.discard();
            return false;
        }
        if (m.level() instanceof ServerLevel lvl) {
            double y = m.getY() + m.getBbHeight() / 2.0;
            Fx.send(lvl, ParticleTypes.SOUL, m.getX(), y, m.getZ(), 25, 0.4, 0.6, 0.4, 0.05);
            Fx.send(lvl, ParticleTypes.LARGE_SMOKE, m.getX(), y, m.getZ(), 25, 0.4, 0.6, 0.4, 0.03);
            sound(lvl, m.getX(), m.getY(), m.getZ(), SoundEvents.BEACON_DEACTIVATE, 1.0F, 0.5F);
        }
        store(p, d, rec, m);
        rec.hp = 1.0F;
        rec.state = RECOVERING;
        ServerLevel ow = p.getServer().overworld();
        rec.recoverDay = ow.getDayTime() + Defs.SHADOW_DOWN_TICKS;
        rec.recoverTick = ow.getGameTime() + Defs.SHADOW_DOWN_TICKS;
        SystemLogic.notify(p, Net.N_SHADOW_BACK, Defs.SHADOW_DOWN_DAYS, rec.id);
        sendList(p);
        return false;
    }

    /**
     * Ticks de juego que le quedan a una sombra caída. Cuentan los días del mundo (si duermes pasan más rápido)
     * y, como mucho, 3 días de tiempo de juego (por si se para el ciclo de día o se cambia la hora).
     */
    public static long downLeft(ShadowRec r, ServerLevel ow) {
        if (r.state != RECOVERING || r.recoverTick <= 0) return 0L;
        long byDay = Math.min(Defs.SHADOW_DOWN_TICKS, r.recoverDay - ow.getDayTime());
        long byGame = r.recoverTick - ow.getGameTime();
        return Math.max(0L, Math.min(byDay, byGame));
    }

    // ================================================================ tick

    public static void tick(MinecraftServer server) {
        if (!ANIMS.isEmpty()) tickAnims();
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
                Fx.essential(c.level, ParticleTypes.SMOKE, c.pos.x, c.pos.y + 0.3, c.pos.z, 3, 0.25, 0.1, 0.25, 0.01);
                Fx.essential(c.level, new DustParticleOptions(new Vector3f(0.35F, 0.1F, 0.55F), 1.4F),
                        c.pos.x, c.pos.y + 0.4, c.pos.z, 3, 0.3, 0.3, 0.3, 0.0);
                if (c.age % 20 == 0) {
                    Fx.ambient(c.level, ParticleTypes.SOUL, c.pos.x, c.pos.y + 0.5, c.pos.z, 2, 0.2, 0.3, 0.2, 0.02);
                }
            }
        }
        ServerLevel ow = server.overworld();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Data d = Store.get(p);
            boolean changed = false;
            if (t % 20 == 0) {
                for (ShadowRec rec : d.shadows) {
                    if (rec.state == RECOVERING && downLeft(rec, ow) <= 0L) {
                        rec.state = STORED;
                        changed = true;
                        if (rec.recoverTick > 0) SystemLogic.notify(p, Net.N_SHADOW_READY, rec.id, 0);
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
                        Fx.send((ServerLevel) m.level(), ParticleTypes.SQUID_INK, s.x, s.y + 0.1, s.z, 12, 0.4, 0.05, 0.4, 0.02);
                    }
                    if ((t + index) % 10 == 0) {
                        m.clearFire();
                        Fx.ambient((ServerLevel) m.level(), new DustParticleOptions(new Vector3f(0.3F, 0.05F, 0.5F), 1.0F),
                                m.getX(), m.getY() + 0.15, m.getZ(), 2, m.getBbWidth() * 0.4, 0.05, m.getBbWidth() * 0.4, 0.0);
                    }
                    if (t % 20 == 0) {
                        double regen = d.fx(Trees.FX_SHADOW_REGEN) + ShadowTree.value(rec.tree, ShadowTree.REGEN);
                        if (regen > 0 && m.getHealth() < m.getMaxHealth()) m.heal((float) regen);
                        rec.hp = Math.max(0.05F, Math.min(1.0F, m.getHealth() / Math.max(1.0F, m.getMaxHealth())));
                    }
                }
            }
            if (changed || t % 40 == 0 && (map != null && !map.isEmpty() || hasRecovering(d) || DIRTY.contains(p.getUUID()))) {
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
                Anim an = animOf(m);
                if (an != null) an.restore();
                CompoundTag tag = new CompoundTag();
                if (m.saveAsPassenger(tag)) {
                    clean(tag);
                    rec.nbt = tag.toString();
                }
                if (an != null) an.freeze();
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
        for (Anim an : ANIMS) {
            if (an.kind == SINK) an.m.discard();
        }
        ANIMS.clear();
        ACTIVE.clear();
        CORPSES.clear();
        MISSING.clear();
        PENDING_DISCARD.clear();
        DIRTY.clear();
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
        ServerLevel ow = p.getServer() != null ? p.getServer().overworld() : (ServerLevel) p.level();
        for (ShadowRec r : d.shadows) {
            nums.add(r.id);
            nums.add(r.rank);
            nums.add(r.level);
            nums.add(r.state);
            nums.add(Math.round(r.hp * 100.0F));
            nums.add((int) Math.min(Integer.MAX_VALUE, downLeft(r, ow)));
            nums.add(r.kills | (r.boss ? 1 << 24 : 0));
            nums.add((int) Math.min(Integer.MAX_VALUE, Math.round(attackOf(r, d) * 10.0)));
            int need = Defs.xpNeeded(r.level);
            nums.add(r.level >= Defs.MAX_LEVEL ? 1000 : (int) Math.max(0L, Math.min(999L, r.xp * 1000L / Math.max(1, need))));
            nums.add(r.pts);
            r.tree = ShadowTree.fix(r.tree);
            for (int k = 0; k < ShadowTree.SIZE; k++) {
                nums.add(r.tree[k]);
            }
            strs.add(r.type);
            strs.add(r.key);
        }
        SystemLogic.sendShadows(p, nums, strs);
        DIRTY.remove(p.getUUID());
    }

    private static void sound(ServerLevel lvl, double x, double y, double z, SoundEvent s, float volume, float pitch) {
        lvl.playSound((Player) null, x, y, z, s, SoundSource.PLAYERS, volume, pitch);
    }

    /** Para la prueba automática: mata a una sombra que está fuera y devuelve su registro (o null). */
    public static ShadowRec testKillOne(ServerPlayer p) {
        Data d = Store.get(p);
        for (ShadowRec rec : d.shadows) {
            if (rec.state != OUT) continue;
            Mob m = entityOf(p, rec);
            if (m == null) continue;
            m.hurt(m.damageSources().generic(), 1.0E7F);
            return rec;
        }
        return null;
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
