package com.elsistema;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Puertas de mazmorra: aparecen cerca del Jugador (o con una llave). Al entrar te llevan a una sala
 * cerrada muy alta en el cielo con oleadas de monstruos del rango de la puerta y un jefe final.
 * Al terminar, la sala se borra entera y vuelves a donde estaba la puerta.
 */
public final class Dungeons {
    private Dungeons() {}

    private static final int SIZE = 27;
    private static final int H = 12;
    private static final int[] WAVES = {3, 3, 4, 4, 5, 5};
    private static final int GATE_LIFE = 6000;
    private static final int RUN_LIMIT = 20 * 60 * 15;
    private static final float[][] RGB = {
            {0.75F, 0.75F, 0.75F}, {0.45F, 0.95F, 0.5F}, {0.35F, 0.75F, 1.0F},
            {0.75F, 0.45F, 1.0F}, {1.0F, 0.7F, 0.25F}, {1.0F, 0.25F, 0.25F}};
    private static final String[] BOSS_NAME = {
            "Rey goblin", "Licántropo alfa", "Señor de la guerra orco",
            "Oso de hielo ancestral", "Reina hormiga", "Coloso de piedra"};
    private static final String[] THEME = {
            "Cueva de los goblins", "Guarida de los lobos", "Fortaleza orca",
            "Caverna helada", "Nido de hormigas", "Templo del coloso"};

    /** Jefe de cada rango (monstruos propios de las mazmorras). */
    private static EntityType<? extends Mob> boss(int rank) {
        return switch (rank) {
            case 0 -> ModEntities.GOBLIN;
            case 1 -> ModEntities.LOBO;
            case 2 -> ModEntities.ORCO;
            case 3 -> ModEntities.OSO;
            case 4 -> ModEntities.HORMIGA;
            default -> ModEntities.COLOSO;
        };
    }

    /** Monstruos de cada rango: solo aparecen dentro de las mazmorras. */
    private static List<EntityType<? extends Mob>> pool(int rank) {
        return switch (rank) {
            case 0 -> List.<EntityType<? extends Mob>>of(ModEntities.GOBLIN, ModEntities.GOBLIN, ModEntities.LOBO);
            case 1 -> List.<EntityType<? extends Mob>>of(ModEntities.GOBLIN, ModEntities.LOBO, ModEntities.LOBO);
            case 2 -> List.<EntityType<? extends Mob>>of(ModEntities.LOBO, ModEntities.ORCO, ModEntities.ORCO, ModEntities.OSO);
            case 3 -> List.<EntityType<? extends Mob>>of(ModEntities.ORCO, ModEntities.OSO, ModEntities.OSO, ModEntities.HORMIGA);
            case 4 -> List.<EntityType<? extends Mob>>of(ModEntities.HORMIGA, ModEntities.HORMIGA, ModEntities.CABALLERO, ModEntities.ORCO);
            default -> List.<EntityType<? extends Mob>>of(ModEntities.CABALLERO, ModEntities.CABALLERO, ModEntities.HORMIGA);
        };
    }

    /** Prueba del cambio de clase: 4 oleadas de élite y el Guardián del Juicio. */
    private static final int TRIAL_WAVES = 5;
    private static final int[] TRIAL_SIZE = {3, 4, 5, 4};

    private static List<EntityType<? extends Mob>> trialPool(int wave) {
        return switch (wave) {
            case 1 -> List.<EntityType<? extends Mob>>of(ModEntities.CABALLERO);
            case 2 -> List.<EntityType<? extends Mob>>of(ModEntities.ORCO, ModEntities.ORCO, ModEntities.CABALLERO);
            case 3 -> List.<EntityType<? extends Mob>>of(ModEntities.LOBO);
            default -> List.<EntityType<? extends Mob>>of(ModEntities.CABALLERO, ModEntities.CABALLERO, ModEntities.ORCO);
        };
    }

    /** Para la prueba automática: el jefe de las mazmorras B, A y S siempre es secreto. */
    public static boolean forceSecret = false;

    /** A veces el jefe de una mazmorra de rango B, A o S es un jefe secreto. */
    private static SecretBoss.Kind rollSecret(int rank, RandomSource rnd) {
        int roll = forceSecret ? 0 : rnd.nextInt(100);
        if (rank >= 5) return roll < 10 ? SecretBoss.Kind.PRIMORDIAL : roll < 16 ? SecretBoss.Kind.QUEEN : null;
        if (rank >= 3) return roll < 8 ? SecretBoss.Kind.QUEEN : null;
        return null;
    }

    private static final List<Gate> GATES = new ArrayList<>();
    private static int nextId = 1;

    private static final class Gate {
        final int id;
        final ServerLevel level;
        final BlockPos pos;
        final int rank;
        final int lvl;
        int age = 0;
        /** 0 = puerta normal, 1 = prueba del cambio de clase, 2 = examen de ascenso de rango (privadas, sin puerta). */
        int special = 0;
        Run run;

        Gate(int id, ServerLevel level, BlockPos pos, int rank, int lvl) {
            this.id = id;
            this.level = level;
            this.pos = pos;
            this.rank = rank;
            this.lvl = lvl;
        }

        double cx() {
            return pos.getX() + 0.5;
        }

        double cy() {
            return pos.getY() + 1.0;
        }

        double cz() {
            return pos.getZ() + 0.5;
        }
    }

    private static final class Run {
        final int x0;
        final int y0;
        final int z0;
        final List<BlockPos> placed = new ArrayList<>();
        final Set<UUID> players = new LinkedHashSet<>();
        final List<UUID> mobs = new ArrayList<>();
        ServerBossEvent bar;
        int wave = 0;
        int total = 3;
        int waveSize = 1;
        int timer = 60;
        int age = 0;
        int endTimer = -1;
        boolean won = false;
        UUID boss;

        Run(int x0, int y0, int z0) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
        }

        double cx() {
            return x0 + SIZE / 2 + 0.5;
        }

        double cz() {
            return z0 + SIZE / 2 + 0.5;
        }
    }

    // ================================================================ abrir puertas

    /** Abre una puerta de mazmorra cerca del Jugador. level <= 0 = nivel aleatorio del rango. */
    public static boolean openGate(ServerPlayer p, int rank, int level, boolean ahead) {
        if (rank < 0 || rank > 5) return false;
        ServerLevel lvl = (ServerLevel) p.level();
        BlockPos pos = findSpot(p, ahead);
        if (pos == null) {
            if (ahead) SystemLogic.notify(p, Net.N_GATE_FAIL, 0, 0);
            return false;
        }
        RandomSource r = p.getRandom();
        int min = rank == 0 ? 1 : Defs.RANK_MAX[rank - 1] + 1;
        int max = Defs.RANK_MAX[rank];
        int gl;
        if (level > 0) {
            gl = level;
        } else {
            // Cerca del nivel del Jugador, dentro de los límites del rango (el rango S llega hasta el 500)
            int want = Store.get(p).level + r.nextInt(9) - 3;
            gl = want < min ? min + r.nextInt(Math.min(5, max - min + 1)) : Math.min(max, want);
        }
        Gate g = new Gate(nextId++, lvl, pos, rank, gl);
        GATES.add(g);
        sound(g, SoundEvents.END_PORTAL_SPAWN, 0.8F, 1.3F);
        for (ServerPlayer o : lvl.players()) {
            double dist = Math.sqrt(o.distanceToSqr(g.cx(), g.cy(), g.cz()));
            if (dist < 80) {
                SystemLogic.notify(o, Net.N_GATE_OPEN, rank, (int) dist);
            }
        }
        return true;
    }

    private static BlockPos findSpot(ServerPlayer p, boolean ahead) {
        ServerLevel l = (ServerLevel) p.level();
        RandomSource r = p.getRandom();
        Vec3 look = p.getLookAngle();
        double lx = look.x;
        double lz = look.z;
        double len = Math.sqrt(lx * lx + lz * lz);
        if (len < 0.01) {
            lx = 1;
            lz = 0;
            len = 1;
        }
        lx /= len;
        lz /= len;
        for (int tries = 0; tries < 24; tries++) {
            double dx;
            double dz;
            double dist;
            if (ahead) {
                dist = 3.0 + tries % 4;
                dx = lx;
                dz = lz;
            } else {
                double ang = r.nextDouble() * Math.PI * 2.0;
                dist = 10 + r.nextInt(8);
                dx = Math.cos(ang);
                dz = Math.sin(ang);
            }
            int x = (int) Math.floor(p.getX() + dx * dist);
            int z = (int) Math.floor(p.getZ() + dz * dist);
            int baseY = p.getBlockY();
            for (int dy = 3; dy >= -5; dy--) {
                BlockPos pos = new BlockPos(x, baseY + dy, z);
                if (l.getBlockState(pos).isAir() && l.getBlockState(pos.above()).isAir()
                        && l.getBlockState(pos.above(2)).isAir() && !l.getBlockState(pos.below()).isAir()) {
                    return pos;
                }
            }
        }
        return null;
    }

    /** Cada cierto tiempo puede abrirse una puerta aleatoria cerca de un Jugador. */
    public static void playerTick(ServerPlayer p, Data d) {
        if (d.awakened != 1 || d.inDungeon == 1 || p.isSpectator() || !p.isAlive()) return;
        d.gateTimer += 20;
        if (d.gateTimer < 7200) return;
        d.gateTimer = 0;
        if (p.getRandom().nextInt(100) >= 35) return;
        for (Gate g : GATES) {
            if (g.level == p.level() && p.distanceToSqr(g.cx(), g.cy(), g.cz()) < 96 * 96) return;
        }
        int pr = Defs.rank(d.level);
        int roll = p.getRandom().nextInt(100);
        int rank = roll < 15 ? pr - 1 : roll < 85 ? pr : pr + 1;
        openGate(p, Math.max(0, Math.min(5, rank)), -1, false);
    }

    // ================================================================ tick

    public static void tick(MinecraftServer server) {
        if (GATES.isEmpty()) return;
        Iterator<Gate> it = GATES.iterator();
        while (it.hasNext()) {
            Gate g = it.next();
            g.age++;
            boolean remove;
            try {
                remove = tickGate(g);
            } catch (Exception e) {
                ElSistema.LOGGER.error("Error en una mazmorra", e);
                try {
                    cleanup(g);
                } catch (Exception e2) {
                    ElSistema.LOGGER.error("Error al limpiar una mazmorra", e2);
                }
                remove = true;
            }
            if (remove) it.remove();
        }
    }

    private static boolean tickGate(Gate g) {
        if (g.special != 0) {
            if (g.run == null) return true;
            return runTick(g);
        }
        if (g.age % 4 == 0) particles(g);
        if (g.age % 80 == 0) sound(g, SoundEvents.PORTAL_AMBIENT, 0.5F, 1.4F);
        boolean canJoin = g.run == null || !g.run.won;
        if (canJoin) {
            for (ServerPlayer p : new ArrayList<>(g.level.players())) {
                if (!p.isAlive() || p.isSpectator()) continue;
                if (g.run != null && g.run.players.contains(p.getUUID())) continue;
                if (p.distanceToSqr(g.cx(), g.cy(), g.cz()) > 2.25) continue;
                Data d = Store.get(p);
                if (d.inDungeon == 1) continue;
                if (d.awakened != 1) {
                    if (g.age % 40 == 0) SystemLogic.notify(p, Net.N_NOT_AWAKENED, 0, 0);
                    continue;
                }
                if (g.run == null && !build(g)) {
                    SystemLogic.notify(p, Net.N_GATE_FAIL, 1, 0);
                    closeEffects(g);
                    return true;
                }
                join(g, p);
            }
        }
        if (g.run == null) {
            if (g.age > GATE_LIFE) {
                closeEffects(g);
                return true;
            }
            return false;
        }
        return runTick(g);
    }

    // ================================================================ sala

    private static boolean clear(ServerLevel l, int x0, int y0, int z0) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                for (int y = 0; y < H; y++) {
                    m.set(x0 + x, y0 + y, z0 + z);
                    if (!l.getBlockState(m).isAir()) return false;
                }
            }
        }
        return true;
    }

    private static BlockState glass(int rank) {
        return switch (rank) {
            case 0 -> Blocks.LIGHT_GRAY_STAINED_GLASS.defaultBlockState();
            case 1 -> Blocks.LIME_STAINED_GLASS.defaultBlockState();
            case 2 -> Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
            case 3 -> Blocks.PURPLE_STAINED_GLASS.defaultBlockState();
            case 4 -> Blocks.ORANGE_STAINED_GLASS.defaultBlockState();
            default -> Blocks.RED_STAINED_GLASS.defaultBlockState();
        };
    }

    private static BlockState blockAt(int x, int y, int z, BlockState glass) {
        boolean ex = x == 0 || x == SIZE - 1;
        boolean ez = z == 0 || z == SIZE - 1;
        if (y == 0) {
            if (ex || ez) return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
            if (x % 5 == 1 && z % 5 == 1) return Blocks.SEA_LANTERN.defaultBlockState();
            return (x + z) % 2 == 0 ? Blocks.DEEPSLATE_TILES.defaultBlockState() : Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        }
        if (y == H - 1) {
            if (!ex && !ez && x % 5 == 1 && z % 5 == 1) return Blocks.SEA_LANTERN.defaultBlockState();
            return Blocks.BLACKSTONE.defaultBlockState();
        }
        if (ex || ez) {
            if (ex && ez) return Blocks.CRYING_OBSIDIAN.defaultBlockState();
            if (y == 3 || y == 8) return glass;
            return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        }
        return null;
    }

    private static boolean isArenaBlock(BlockState st) {
        return st.is(Blocks.POLISHED_BLACKSTONE_BRICKS) || st.is(Blocks.SEA_LANTERN) || st.is(Blocks.DEEPSLATE_TILES)
                || st.is(Blocks.POLISHED_DEEPSLATE) || st.is(Blocks.BLACKSTONE) || st.is(Blocks.CRYING_OBSIDIAN)
                || st.is(Blocks.LIGHT_GRAY_STAINED_GLASS) || st.is(Blocks.LIME_STAINED_GLASS)
                || st.is(Blocks.LIGHT_BLUE_STAINED_GLASS) || st.is(Blocks.PURPLE_STAINED_GLASS)
                || st.is(Blocks.ORANGE_STAINED_GLASS) || st.is(Blocks.RED_STAINED_GLASS) || st.is(Blocks.YELLOW_STAINED_GLASS);
    }

    private static boolean build(Gate g) {
        ServerLevel l = g.level;
        int y0 = l.getMaxBuildHeight() - H - 12;
        if (y0 < g.pos.getY() + 20) return false;
        for (int attempt = 0; attempt < 6; attempt++) {
            int x0 = g.pos.getX() - SIZE / 2 + attempt * 64;
            int z0 = g.pos.getZ() - SIZE / 2;
            if (!clear(l, x0, y0, z0)) continue;
            Run r = new Run(x0, y0, z0);
            BlockState glass = g.special == 1 ? Blocks.YELLOW_STAINED_GLASS.defaultBlockState() : glass(g.rank);
            for (int x = 0; x < SIZE; x++) {
                for (int z = 0; z < SIZE; z++) {
                    for (int y = 0; y < H; y++) {
                        BlockState st = blockAt(x, y, z, glass);
                        if (st != null) {
                            BlockPos pos = new BlockPos(x0 + x, y0 + y, z0 + z);
                            l.setBlock(pos, st, 2);
                            r.placed.add(pos);
                        }
                    }
                }
            }
            r.total = WAVES[g.rank];
            r.bar = new ServerBossEvent(Component.literal(THEME[g.rank] + " (rango " + Defs.RANK[g.rank] + ") · Preparándose..."),
                    BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
            r.bar.setProgress(1.0F);
            g.run = r;
            return true;
        }
        return false;
    }

    private static boolean inside(Run r, Entity e) {
        return e.getX() >= r.x0 + 1 && e.getX() <= r.x0 + SIZE - 1
                && e.getZ() >= r.z0 + 1 && e.getZ() <= r.z0 + SIZE - 1
                && e.getY() >= r.y0 && e.getY() <= r.y0 + H;
    }

    private static boolean near(Run r, Entity e, double range) {
        double dx = e.getX() - r.cx();
        double dz = e.getZ() - r.cz();
        return dx * dx + dz * dz < range * range && e.getY() > r.y0 - 80;
    }

    // ================================================================ jugadores

    private static void join(Gate g, ServerPlayer p) {
        Run r = g.run;
        r.players.add(p.getUUID());
        r.bar.addPlayer(p);
        Data d = Store.get(p);
        d.inDungeon = 1;
        if (g.special != 0) {
            d.retX = p.getX();
            d.retY = p.getY();
            d.retZ = p.getZ();
        } else {
            d.retX = g.cx() + 2.0;
            d.retY = g.pos.getY();
            d.retZ = g.cz();
        }
        d.arenaX = r.x0;
        d.arenaY = r.y0;
        d.arenaZ = r.z0;
        p.teleportTo(r.cx(), r.y0 + 1, r.cz());
        Shadows.recall(p);
        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false, false));
        p.playNotifySound(SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.6F);
        SystemLogic.notify(p, g.special == 1 ? Net.N_TRIAL_START : g.special == 2 ? Net.N_EXAM_START : Net.N_DUNGEON_ENTER,
                g.rank, g.lvl);
    }

    /** Aviso de mazmorra fallida (o de prueba fallida). why = 1 si se acabó el tiempo. */
    private static void failed(Gate g, ServerPlayer p, int why) {
        if (g.special == 1) {
            Quests.trialFailed(p, why);
        } else if (g.special == 2) {
            Ranking.examFailed(p, why);
        } else {
            SystemLogic.notify(p, Net.N_DUNGEON_FAIL, why, 0);
        }
    }

    /** Prueba del cambio de clase: una mazmorra privada solo para ti, sin puerta. */
    public static boolean startTrial(ServerPlayer p, Data d) {
        ServerLevel lvl = (ServerLevel) p.level();
        int level = Math.min(Defs.MAX_LEVEL, Math.max(45, d.level + 3));
        Gate g = new Gate(nextId++, lvl, p.blockPosition(), 3, level);
        g.special = 1;
        if (!build(g)) return false;
        g.run.total = TRIAL_WAVES;
        g.run.timer = 100;
        g.run.bar.setName(Component.literal("Prueba del cambio de clase · Preparándose..."));
        g.run.bar.setColor(BossEvent.BossBarColor.YELLOW);
        GATES.add(g);
        join(g, p);
        return true;
    }

    /** Examen de ascenso de rango: mazmorra privada con 3 oleadas del rango al que quieres subir y un examinador. */
    public static boolean startExam(ServerPlayer p, Data d, int target) {
        ServerLevel lvl = (ServerLevel) p.level();
        int rank = Math.max(1, Math.min(5, target));
        Gate g = new Gate(nextId++, lvl, p.blockPosition(), rank, Defs.examLevel(rank, d.level));
        g.special = 2;
        if (!build(g)) return false;
        g.run.total = Defs.EXAM_WAVES + 1;
        g.run.timer = 100;
        g.run.bar.setName(Component.literal("Examen de ascenso a rango " + Defs.RANK[rank] + " · Preparándose..."));
        g.run.bar.setColor(BossEvent.BossBarColor.BLUE);
        GATES.add(g);
        join(g, p);
        return true;
    }

    private static String runTitle(Gate g) {
        return g.special == 1 ? "Prueba del cambio de clase" : g.special == 2 ? "Examen de rango " + Defs.RANK[g.rank]
                : "Mazmorra de rango " + Defs.RANK[g.rank];
    }

    private static Gate gateOf(ServerPlayer p) {
        for (Gate g : GATES) {
            if (g.run != null && g.run.players.contains(p.getUUID())) return g;
        }
        return null;
    }

    /** Prueba automática: salta directamente a la oleada del jefe de la mazmorra del Jugador. */
    public static boolean testSkipToBoss(ServerPlayer p) {
        Gate g = gateOf(p);
        if (g == null) return false;
        Run r = g.run;
        for (UUID u : r.mobs) {
            Entity e = g.level.getEntity(u);
            if (e != null) e.discard();
        }
        r.mobs.clear();
        r.wave = r.total - 1;
        r.timer = 1;
        return true;
    }

    /** Prueba automática: el Jugador acaba con todo lo que queda en su mazmorra. */
    public static int testWin(ServerPlayer p) {
        Gate g = gateOf(p);
        if (g == null) return -1;
        int n = 0;
        for (UUID u : new ArrayList<>(g.run.mobs)) {
            Entity e = g.level.getEntity(u);
            if (e instanceof LivingEntity le && le.isAlive()) {
                le.hurt(p.damageSources().playerAttack(p), 1.0E7F);
                n++;
            }
        }
        return n;
    }

    /** Saca a un jugador de la mazmorra (si está vivo y cerca, lo devuelve junto a la puerta). */
    private static void leave(ServerPlayer p, Run r) {
        r.bar.removePlayer(p);
        Data d = Store.get(p);
        if (d.inDungeon == 1 && p.isAlive() && near(r, p, 64)) {
            p.teleportTo(d.retX, d.retY, d.retZ);
            Shadows.recall(p);
        }
        d.inDungeon = 0;
    }

    /** ¿Está el Jugador dentro de una mazmorra activa? */
    public static boolean inDungeon(ServerPlayer p) {
        for (Gate g : GATES) {
            if (g.run != null && g.run.players.contains(p.getUUID())) return true;
        }
        return false;
    }

    /** Piedra de retorno: sale de la mazmorra (cuenta como abandonarla). */
    public static boolean leaveNow(ServerPlayer p) {
        for (Gate g : GATES) {
            if (g.run != null && g.run.players.remove(p.getUUID())) {
                if (!g.run.won) failed(g, p, 0);
                leave(p, g.run);
                return true;
            }
        }
        return false;
    }

    private static ServerPlayer nearestPlayer(Gate g, Entity from) {
        MinecraftServer server = g.level.getServer();
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (UUID id : g.run.players) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null || !p.isAlive()) continue;
            double dd = p.distanceToSqr(from);
            if (dd < bestD) {
                bestD = dd;
                best = p;
            }
        }
        return best;
    }

    // ================================================================ oleadas

    private static boolean runTick(Gate g) {
        Run r = g.run;
        r.age++;
        MinecraftServer server = g.level.getServer();
        Iterator<UUID> pit = r.players.iterator();
        while (pit.hasNext()) {
            ServerPlayer p = server.getPlayerList().getPlayer(pit.next());
            if (p == null) {
                pit.remove();
                continue;
            }
            if (!p.isAlive() || p.level() != g.level || !near(r, p, 48)) {
                if (!r.won) failed(g, p, p.isAlive() ? 0 : 2);
                leave(p, r);
                pit.remove();
                continue;
            }
            if (!inside(r, p)) {
                p.teleportTo(r.cx(), r.y0 + 1, r.cz());
            }
        }
        if (r.players.isEmpty()) {
            cleanup(g);
            return true;
        }
        if (!r.won && r.age > RUN_LIMIT) {
            for (UUID id : r.players) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) failed(g, p, 1);
            }
            cleanup(g);
            return true;
        }
        if (r.endTimer >= 0) {
            r.endTimer--;
            if (r.endTimer <= 0) {
                cleanup(g);
                return true;
            }
            return false;
        }
        if (r.timer > 0) {
            r.timer--;
            if (r.timer == 0) spawnWave(g);
            return false;
        }
        if (r.age % 10 == 0) {
            int alive = 0;
            for (UUID u : r.mobs) {
                Entity e = g.level.getEntity(u);
                if (e != null && e.isAlive()) {
                    alive++;
                    if (r.age % 40 == 0 && !inside(r, e)) {
                        e.teleportTo(r.cx(), r.y0 + 1, r.cz() + 4);
                    }
                }
            }
            float prog = alive / (float) r.waveSize;
            if (r.boss != null) {
                Entity b = g.level.getEntity(r.boss);
                if (b instanceof LivingEntity le && le.isAlive()) prog = le.getHealth() / Math.max(1.0F, le.getMaxHealth());
            }
            r.bar.setProgress(Math.max(0.0F, Math.min(1.0F, prog)));
            if (alive == 0) {
                if (r.wave >= r.total) {
                    victory(g);
                } else {
                    r.timer = 80;
                    r.bar.setName(Component.literal(runTitle(g) + " · Siguiente oleada..."));
                }
            }
        }
        return false;
    }

    private static void spawnWave(Gate g) {
        Run r = g.run;
        r.wave++;
        boolean boss = r.wave >= r.total;
        boolean trial = g.special == 1;
        boolean exam = g.special == 2;
        List<EntityType<? extends Mob>> pool = trial ? trialPool(boss ? 4 : r.wave) : pool(g.rank);
        RandomSource rnd = g.level.getRandom();
        List<UUID> list = new ArrayList<>();
        int n = trial ? (boss ? 2 : TRIAL_SIZE[Math.max(0, Math.min(TRIAL_SIZE.length - 1, r.wave - 1))])
                : exam ? (boss ? 1 + g.rank / 2 : 2 + g.rank / 2 + r.wave)
                : boss ? 2 + g.rank / 2 : 3 + g.rank + r.wave;
        SecretBoss.Kind secret = !boss || exam ? null : trial ? SecretBoss.Kind.GUARDIAN : rollSecret(g.rank, rnd);
        SecretBoss secretBoss = null;
        r.boss = null;
        Ranks.spawning = true;
        try {
            for (int i = 0; i < n; i++) {
                Mob m = spawnMob(g, pool.get(rnd.nextInt(pool.size())), false);
                if (m != null) list.add(m.getUUID());
            }
            if (boss) {
                Mob b;
                if (secret != null) {
                    secretBoss = spawnSecret(g, secret);
                    b = secretBoss;
                } else {
                    b = spawnMob(g, boss(g.rank), true);
                }
                if (b != null) {
                    list.add(b.getUUID());
                    r.boss = b.getUUID();
                }
            }
        } finally {
            Ranks.spawning = false;
        }
        r.mobs.clear();
        r.mobs.addAll(list);
        r.waveSize = Math.max(1, list.size());
        if (boss && secretBoss != null) {
            r.bar.setName(Component.literal("☠ JEFE SECRETO: " + SecretBoss.NAME[secret.ordinal()] + " · Rango " + Defs.RANK[g.rank]));
            r.bar.setColor(BossEvent.BossBarColor.RED);
        } else if (boss && exam) {
            r.bar.setName(Component.literal("★ EXAMINADOR DE RANGO " + Defs.RANK[g.rank] + " · Derrótalo para ascender"));
            r.bar.setColor(BossEvent.BossBarColor.RED);
        } else if (boss) {
            r.bar.setName(Component.literal("★ JEFE: " + BOSS_NAME[g.rank] + " · Rango " + Defs.RANK[g.rank]));
            r.bar.setColor(BossEvent.BossBarColor.RED);
        } else {
            r.bar.setName(Component.literal((trial ? "Prueba del cambio de clase" : exam ? "Examen de rango " + Defs.RANK[g.rank]
                    : THEME[g.rank]) + " · Oleada " + r.wave + "/" + r.total));
            r.bar.setColor(trial ? BossEvent.BossBarColor.YELLOW : exam ? BossEvent.BossBarColor.BLUE : BossEvent.BossBarColor.PURPLE);
        }
        r.bar.setProgress(1.0F);
        MinecraftServer server = g.level.getServer();
        for (UUID id : r.players) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            if (boss) {
                if (secretBoss == null) SystemLogic.notify(p, Net.N_BOSS, g.rank, exam ? 1 : 0);
                p.playNotifySound(SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0F, 0.6F);
            } else {
                SystemLogic.notify(p, Net.N_WAVE, r.wave, r.total);
            }
        }
        if (secretBoss != null) Quests.secretBossAppeared(g.level, secretBoss, true);
    }

    private static SecretBoss spawnSecret(Gate g, SecretBoss.Kind k) {
        Run r = g.run;
        BlockPos pos = new BlockPos(r.x0 + SIZE / 2, r.y0 + 1, r.z0 + SIZE / 2 + 6);
        int lvl = Math.max(1, Math.min(Defs.MAX_LEVEL, g.lvl + 5));
        SecretBoss b = SecretBoss.spawn(g.level, k, pos, g.rank, lvl, true);
        if (b == null) return null;
        ServerPlayer target = nearestPlayer(g, b);
        if (target != null) b.setTarget(target);
        return b;
    }

    private static Mob spawnMob(Gate g, EntityType<? extends Mob> type, boolean boss) {
        Run r = g.run;
        RandomSource rnd = g.level.getRandom();
        BlockPos pos;
        if (boss) {
            pos = new BlockPos(r.x0 + SIZE / 2, r.y0 + 1, r.z0 + SIZE / 2 + 6);
        } else {
            int x;
            int z;
            do {
                x = 2 + rnd.nextInt(SIZE - 4);
                z = 2 + rnd.nextInt(SIZE - 4);
            } while (Math.abs(x - SIZE / 2) < 4 && Math.abs(z - SIZE / 2) < 4);
            pos = new BlockPos(r.x0 + x, r.y0 + 1, r.z0 + z);
        }
        Mob m = type.spawn(g.level, pos, MobSpawnType.MOB_SUMMONED);
        if (m == null) return null;
        m.setPersistenceRequired();
        m.addTag("es_dg");
        int lvl = Math.max(1, Math.min(Defs.MAX_LEVEL, g.lvl + (boss ? 5 : rnd.nextInt(5) - 2)));
        Ranks.apply(m, g.rank, lvl, boss);
        if (boss) {
            m.setGlowingTag(true);
            String title = g.special == 2 ? "Examinador de rango " + Defs.RANK[g.rank] : BOSS_NAME[g.rank];
            MutableComponent name = Component.literal("★ " + title + " ").withStyle(Ranks.COLOR[g.rank], ChatFormatting.BOLD);
            name.append(Component.literal("[" + Defs.RANK[g.rank] + "] Nv." + lvl).withStyle(ChatFormatting.WHITE));
            m.setCustomName(name);
            m.setCustomNameVisible(true);
        }
        ServerPlayer target = nearestPlayer(g, m);
        if (target != null) m.setTarget(target);
        Fx.send(g.level, ParticleTypes.REVERSE_PORTAL, m.getX(), m.getY() + 1.0, m.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
        return m;
    }

    private static List<ItemStack> loot(int rank) {
        return switch (rank) {
            case 0 -> List.of(new ItemStack(Items.IRON_INGOT, 4));
            case 1 -> List.of(new ItemStack(Items.IRON_INGOT, 8), new ItemStack(Items.GOLD_INGOT, 3));
            case 2 -> List.of(new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.GOLD_INGOT, 5));
            case 3 -> List.of(new ItemStack(Items.DIAMOND, 4), new ItemStack(Items.EMERALD, 4));
            case 4 -> List.of(new ItemStack(Items.DIAMOND, 6), new ItemStack(Items.NETHERITE_SCRAP, 1));
            default -> List.of(new ItemStack(Items.DIAMOND, 10), new ItemStack(Items.NETHERITE_SCRAP, 2),
                    new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
        };
    }

    private static void victory(Gate g) {
        Run r = g.run;
        r.won = true;
        boolean trial = g.special == 1;
        boolean exam = g.special == 2;
        r.endTimer = trial || exam ? 200 : 160;
        r.bar.setName(Component.literal(trial ? "¡Prueba superada! Volviendo en 10 s..."
                : exam ? "¡Examen superado! Ahora eres rango " + Defs.RANK[g.rank] + ". Volviendo en 10 s..."
                : "¡Mazmorra completada! Volviendo en 8 s..."));
        r.bar.setColor(BossEvent.BossBarColor.GREEN);
        r.bar.setProgress(1.0F);
        MinecraftServer server = g.level.getServer();
        RandomSource rnd = g.level.getRandom();
        int rank = g.rank;
        for (UUID id : r.players) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            SystemLogic.addXp(p, (int) (60 * (rank + 1) * (1 + g.lvl / 10.0)));
            if (trial) {
                Quests.trialWon(p);
                continue;
            }
            if (exam) {
                Ranking.examWon(p, g.rank);
                continue;
            }
            int gold = SystemLogic.addCoins(p, (int) (40 * (rank + 1) * (rank + 1) * (1 + g.lvl / 20.0)));
            Shop.give(p, new ItemStack(ModItems.CORES[rank], 2 + rnd.nextInt(3)));
            if (rnd.nextInt(100) < 25) Shop.give(p, new ItemStack(ModItems.RUNE));
            if (rnd.nextInt(100) < 25) Shop.give(p, new ItemStack(ModItems.KEYS[rank]));
            if (rank < 5 && rnd.nextInt(100) < 10) Shop.give(p, new ItemStack(ModItems.KEYS[rank + 1]));
            for (ItemStack s : loot(rank)) {
                Shop.give(p, s);
            }
            SystemLogic.notify(p, Net.N_DUNGEON_CLEAR, rank, gold);
            p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
            Quests.onDungeonClear(p, Store.get(p), rank);
        }
        Fx.send(g.level, ParticleTypes.TOTEM_OF_UNDYING, r.cx(), r.y0 + 2.0, r.cz(), 120, 3.0, 1.5, 3.0, 0.4);
    }

    // ================================================================ cerrar y limpiar

    private static void cleanup(Gate g) {
        Run r = g.run;
        if (r != null) {
            ServerLevel l = g.level;
            AABB box = new AABB(r.x0 - 2, r.y0 - 40, r.z0 - 2, r.x0 + SIZE + 2, r.y0 + H + 3, r.z0 + SIZE + 2);
            double gx = g.cx();
            double gy = g.pos.getY() + 0.2;
            double gz = g.cz();
            for (Entity e : l.getEntitiesOfClass(Entity.class, box, e -> e instanceof ItemEntity || e instanceof ExperienceOrb)) {
                e.teleportTo(gx, gy, gz);
            }
            for (UUID u : r.mobs) {
                Entity e = l.getEntity(u);
                if (e != null) e.discard();
            }
            for (Mob m : l.getEntitiesOfClass(Mob.class, box, m -> !Shadows.isShadow(m))) {
                m.discard();
            }
            MinecraftServer server = l.getServer();
            for (UUID id : new ArrayList<>(r.players)) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) leave(p, r);
            }
            r.players.clear();
            r.bar.removeAllPlayers();
            for (BlockPos pos : r.placed) {
                l.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        closeEffects(g);
    }

    private static void closeEffects(Gate g) {
        Fx.send(g.level, ParticleTypes.REVERSE_PORTAL, g.cx(), g.cy() + 0.5, g.cz(), 60, 0.6, 1.0, 0.6, 0.2);
        sound(g, SoundEvents.BEACON_DEACTIVATE, 1.0F, 0.8F);
    }

    /** Si un jugador se desconecta dentro, lo saca antes de guardar. */
    public static void onDisconnect(ServerPlayer p) {
        for (Gate g : GATES) {
            if (g.run != null && g.run.players.remove(p.getUUID())) {
                leave(p, g.run);
            }
        }
    }

    /** Al cerrar el servidor: saca a todos y borra las salas. */
    public static void shutdown() {
        for (Gate g : GATES) {
            try {
                cleanup(g);
            } catch (Exception e) {
                ElSistema.LOGGER.error("Error al cerrar una mazmorra", e);
            }
        }
        GATES.clear();
    }

    /** Si el juego se cerró de golpe dentro de una mazmorra, al volver te saca y borra los restos de la sala. */
    public static void recover(ServerPlayer p, Data d) {
        if (d.inDungeon != 1) return;
        for (Gate g : GATES) {
            if (g.run != null && g.run.players.contains(p.getUUID())) return;
        }
        ServerLevel l = (ServerLevel) p.level();
        if (d.arenaY > 100) {
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int x = 0; x < SIZE; x++) {
                for (int z = 0; z < SIZE; z++) {
                    for (int y = 0; y < H; y++) {
                        m.set(d.arenaX + x, d.arenaY + y, d.arenaZ + z);
                        if (isArenaBlock(l.getBlockState(m))) {
                            l.setBlock(m.immutable(), Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }
        p.teleportTo(d.retX, d.retY, d.retZ);
        d.inDungeon = 0;
    }

    /** Para la prueba automática: construye una mazmorra de cada rango, saca la oleada del jefe y la limpia. */
    public static void selfTest(ServerLevel lvl, BlockPos at) {
        BlockPos pos = new BlockPos(at.getX(), lvl.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ()), at.getZ());
        for (int rank = 0; rank < 6; rank++) {
            Gate g = new Gate(nextId++, lvl, pos, rank, Defs.RANK_MIN[rank] + 2);
            if (!build(g)) throw new IllegalStateException("no se pudo construir la mazmorra de rango " + Defs.RANK[rank]);
            g.run.total = 1;
            spawnWave(g);
            if (g.run.mobs.isEmpty()) throw new IllegalStateException("la mazmorra de rango " + Defs.RANK[rank] + " no tiene monstruos");
            cleanup(g);
        }
        // Prueba del cambio de clase (con el Guardián del Juicio)
        Gate t = new Gate(nextId++, lvl, pos, 3, 48);
        t.special = 1;
        if (!build(t)) throw new IllegalStateException("no se pudo construir la prueba del cambio de clase");
        t.run.total = 1;
        spawnWave(t);
        if (t.run.boss == null || !(lvl.getEntity(t.run.boss) instanceof SecretBoss)) {
            throw new IllegalStateException("la prueba no tiene al Guardián del Juicio");
        }
        cleanup(t);
        // Examen de ascenso (al rango A): 3 oleadas y el examinador
        Gate x = new Gate(nextId++, lvl, pos, 4, Defs.examLevel(4, 70));
        x.special = 2;
        if (!build(x)) throw new IllegalStateException("no se pudo construir el examen de ascenso");
        x.run.total = 1;
        spawnWave(x);
        if (x.run.boss == null || x.run.mobs.isEmpty()) throw new IllegalStateException("el examen no tiene examinador");
        cleanup(x);
        // Jefes secretos de las mazmorras B y S
        forceSecret = true;
        try {
            for (int rank : new int[]{3, 5}) {
                Gate s = new Gate(nextId++, lvl, pos, rank, Defs.RANK_MIN[rank] + 2);
                if (!build(s)) throw new IllegalStateException("no se pudo construir la mazmorra secreta " + Defs.RANK[rank]);
                s.run.total = 1;
                spawnWave(s);
                if (s.run.boss == null || !(lvl.getEntity(s.run.boss) instanceof SecretBoss)) {
                    throw new IllegalStateException("la mazmorra de rango " + Defs.RANK[rank] + " no tiene jefe secreto");
                }
                cleanup(s);
            }
        } finally {
            forceSecret = false;
        }
    }

    // ================================================================ efectos

    private static void sound(Gate g, SoundEvent s, float volume, float pitch) {
        g.level.playSound((Player) null, g.cx(), g.cy(), g.cz(), s, SoundSource.HOSTILE, volume, pitch);
    }

    private static void particles(Gate g) {
        ServerLevel l = g.level;
        float[] c = RGB[g.rank];
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(c[0], c[1], c[2]), 1.6F);
        double cx = g.cx();
        double cy = g.cy() + 0.5;
        double cz = g.cz();
        double t = g.age * 0.15;
        for (int i = 0; i < 12; i++) {
            double a = t + i * Math.PI * 2.0 / 12.0;
            Fx.essential(l, dust, cx + Math.cos(a) * 1.2, cy + Math.sin(a) * 1.6, cz, 1, 0.0, 0.0, 0.0, 0.0);
        }
        Fx.ambient(l, ParticleTypes.PORTAL, cx, cy, cz, 6, 0.3, 0.8, 0.1, 0.4);
        if (g.age % 20 == 0) {
            for (int y = 0; y < 24; y += 2) {
                Fx.essential(l, ParticleTypes.END_ROD, cx, cy + 2 + y, cz, 1, 0.05, 0.3, 0.05, 0.0);
            }
        }
    }
}
